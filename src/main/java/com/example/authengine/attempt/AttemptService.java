package com.example.authengine.attempt;

import com.example.authengine.common.ApiException;
import com.example.authengine.common.SecureValues;
import com.example.authengine.config.AuthProperties;
import com.example.authengine.domain.FactorType;
import com.example.authengine.domain.PolicyEvaluator;
import com.example.authengine.domain.PolicyNode;
import com.example.authengine.dto.response.AttemptResponse;
import com.example.authengine.dto.response.FactorOptionResponse;
import com.example.authengine.dto.response.PolicySnapshotResponse;
import com.example.authengine.dto.response.StartFactorResponse;
import com.example.authengine.dto.response.VerifyFactorResponse;
import com.example.authengine.factor.FactorHandler;
import com.example.authengine.factor.FactorRegistry;
import com.example.authengine.mapper.AppUserMapper;
import com.example.authengine.policy.PolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 动态登录认证状态机会话服务
 * <p>
 * 职责：
 * 1. 管理登录 Attempt 全生命周期流转 (创建、因子开放决策、动态核验凭据下发、凭据核验、原子核销)；
 * 2. 状态机每次变更严格采用 CAS 乐观版本锁 (revision)，杜绝多端并发竞争与重放攻击；
 * 3. 会话创建时一次性捕获策略 AST 不可变快照，整个生命周期内不受后续策略版本发布影响；
 * 4. 强制校验浏览器绑定的 {@code AUTH_BIND} 指纹 Cookie，防御跨站/跨设备盗用。
 */
@Service
@RequiredArgsConstructor
public class AttemptService {

    private final AttemptRepository attemptRepository;
    private final PolicyService policyService;
    private final AppUserMapper appUserMapper;
    private final FactorRegistry factorRegistry;
    private final AuthProperties authProperties;

    /**
     * 发起并初始化一笔新的动态认证会话 (Attempt)
     * <p>
     * 1. 获取当前生效策略的不可变快照并注入 Attempt；
     * 2. 校验用户名对应的用户主体是否存在且可用；
     * 3. 计算浏览器绑定 Cookie 的 SHA-256 指纹；
     * 4. 初始化状态机为 {@link AttemptStatus#IN_PROGRESS} 并落库。
     *
     * @param username       登录用户名
     * @param browserBinding 浏览器下发的 AUTH_BIND 随机绑定凭据
     * @return 初始化的 Attempt 响应视图
     */
    public AttemptResponse create(String username, String browserBinding) {
        PolicySnapshotResponse policy = policyService.active(authProperties.getPolicyCode());

        LoginAttempt attempt = new LoginAttempt();
        attempt.setId(SecureValues.randomToken(24));
        attempt.setUsername(username.trim());
        attempt.setSubjectUserId(appUserMapper.findByUsername(username.trim())
                .filter(u -> u.isEnabled())
                .map(u -> u.getId())
                .orElse(null));
        attempt.setBindingHash(SecureValues.sha256(browserBinding));
        attempt.setPolicyCode(policy.code());
        attempt.setPolicyVersion(policy.version());
        attempt.setRoot(policy.root());
        attempt.setCreatedAt(Instant.now());
        attempt.setExpiresAt(attempt.getCreatedAt().plusSeconds(authProperties.getAttemptTtlSeconds()));

        Map<String, FactorStep> steps = new LinkedHashMap<>();
        collectSteps(policy.root(), steps);
        attempt.setSteps(steps);

        if (!attemptRepository.insert(attempt)) {
            throw ApiException.conflict("无法初始化登录认证流程，请重试");
        }
        return toAttemptResponse(attempt);
    }

    /**
     * 查询指定 Attempt 的当前执行拓扑与状态机视图
     *
     * @param id             Attempt 会话全局 ID
     * @param browserBinding 浏览器绑定 Cookie
     * @return 当前 Attempt 响应视图
     */
    public AttemptResponse status(String id, String browserBinding) {
        LoginAttempt attempt = load(id);
        requireBrowser(attempt, browserBinding);
        return toAttemptResponse(attempt);
    }

    /**
     * 发起指定因子的动态核验流程 (如发送短信验证码或生成动态二维码)
     * <p>
     * 只有成功执行 CAS 递增版本的请求才能执行短信发送或二维码落地，避免高并发冲突时下发无效凭据。
     *
     * @param id             Attempt 会话 ID
     * @param nodeId         当前待执行的因子节点 ID
     * @param browserBinding 浏览器绑定 Cookie
     * @return 包含最新 Attempt 视图及二维码票据/时效的响应
     */
    public StartFactorResponse start(String id, String nodeId, String browserBinding) {
        for (int tries = 0; tries < 12; tries++) {
            LoginAttempt attempt = load(id);
            requireBrowser(attempt, browserBinding);
            FactorStep step = requireEligible(attempt, nodeId);

            if (step.getType() == FactorType.PASSWORD) {
                throw ApiException.bad("密码认证无需预先发起动态验证，请直接提交密码核验");
            }

            FactorHandler handler = factorRegistry.require(step.getType());
            Instant now = Instant.now();

            if (step.getChallengeSends() >= authProperties.getMaxChallengeSends()) {
                throw ApiException.forbidden("动态验证凭据发送次数已达上限，请稍后重试");
            }
            if (step.getLastChallengeAt() != null &&
                    step.getLastChallengeAt().plusSeconds(authProperties.getChallengeCooldownSeconds()).isAfter(now)) {
                throw ApiException.conflict("请求过于频繁，请等待倒计时结束后重试");
            }

            FactorHandler.PreparedChallenge challenge = handler.prepare(attempt);
            step.setChallengeHash(challenge.secretHash());
            step.setChallengeExpiresAt(challenge.expiresAt());
            step.setChallengeSends(step.getChallengeSends() + 1);
            step.setLastChallengeAt(now);

            attempt.setRevision(attempt.getRevision() + 1);
            if (attemptRepository.compareAndSet(id, attempt.getRevision() - 1, attempt)) {
                handler.deliver(attempt, challenge);
                return new StartFactorResponse(toAttemptResponse(attempt), challenge.expiresAt(), challenge.displayTicket());
            }
        }
        throw ApiException.conflict("会话状态更新并发冲突，请重试");
    }

    /**
     * 提交指定因子的核验凭据 (如密码或短信验证码)
     * <p>
     * 核验成功后标记节点为 VERIFIED 并销毁对应动态凭据，失败则增加错误计数并在达到阈值后锁定节点。
     * 每次核验后立即驱动 AST 求值引擎重新计算整树达成状态。
     *
     * @param id             Attempt 会话 ID
     * @param nodeId         当前核验的因子节点 ID
     * @param browserBinding 浏览器绑定 Cookie
     * @param credential     待核验的凭据内容
     * @return 包含更新后状态视图与核验是否通过的响应
     */
    public VerifyFactorResponse verify(String id, String nodeId, String browserBinding, String credential) {
        for (int tries = 0; tries < 12; tries++) {
            LoginAttempt attempt = load(id);
            requireBrowser(attempt, browserBinding);
            FactorStep step = requireEligible(attempt, nodeId);

            if (step.getType() == FactorType.APP_QR_APPROVAL) {
                throw ApiException.bad("移动端扫码认证必须由已登录的独立客户端确认");
            }

            boolean success = factorRegistry.require(step.getType()).verify(attempt, step, credential);
            if (success) {
                step.setVerified(true);
                step.setVerifiedAt(Instant.now());
                step.setChallengeHash(null);
                step.setChallengeExpiresAt(null);
            } else {
                step.setFailures(step.getFailures() + 1);
                if (step.getFailures() >= authProperties.getMaxFactorFailures()) {
                    step.setExhausted(true);
                }
            }

            refresh(attempt);
            attempt.setRevision(attempt.getRevision() + 1);
            if (attemptRepository.compareAndSet(id, attempt.getRevision() - 1, attempt)) {
                return new VerifyFactorResponse(toAttemptResponse(attempt), success);
            }
        }
        throw ApiException.conflict("会话状态更新并发冲突，请重试");
    }

    /**
     * 移动客户端 APP 专用扫码反向授权确认入口
     * <p>
     * 强制校验：
     * 1. 扫码票据防篡改 SHA-256 哈希及有效期；
     * 2. 移动端登录主体与发起网页端登录的用户名必须严格一致（跨账号隔离）；
     * 3. 授权通过后单次安全核销票据。
     *
     * @param ticket              二维码展示的 attemptId.token 复合票据密文
     * @param approvedByUsername  APP 移动端当前登录身份的用户名
     * @return 更新后的 Attempt 响应视图
     */
    public AttemptResponse approveQr(String ticket, String approvedByUsername) {
        if (ticket == null || ticket.length() > 256) {
            throw ApiException.bad("二维码票据格式无效");
        }
        int pos = ticket.indexOf('.');
        if (pos <= 0) {
            throw ApiException.bad("二维码票据格式无效");
        }
        String id = ticket.substring(0, pos);

        for (int tries = 0; tries < 12; tries++) {
            LoginAttempt attempt = load(id);

            if (attempt.getSubjectUserId() == null || !attempt.getUsername().equals(approvedByUsername)) {
                throw ApiException.forbidden("扫码客户端账号与当前发起登录账号不一致");
            }

            FactorStep step = attempt.getSteps().values().stream()
                    .filter(s -> s.getType() == FactorType.APP_QR_APPROVAL)
                    .findFirst()
                    .orElseThrow(() -> ApiException.bad("当前策略不包含扫码认证项"));

            requireEligible(attempt, step.getNodeId());

            if (step.getChallengeHash() == null || step.getChallengeExpiresAt() == null ||
                    !step.getChallengeExpiresAt().isAfter(Instant.now()) ||
                    !SecureValues.constantEquals(step.getChallengeHash(), SecureValues.sha256(ticket))) {
                throw ApiException.forbidden("二维码已失效或已过期");
            }

            step.setVerified(true);
            step.setVerifiedAt(Instant.now());
            step.setChallengeHash(null);
            step.setChallengeExpiresAt(null);

            refresh(attempt);
            attempt.setRevision(attempt.getRevision() + 1);
            if (attemptRepository.compareAndSet(id, attempt.getRevision() - 1, attempt)) {
                return toAttemptResponse(attempt);
            }
        }
        throw ApiException.conflict("扫码确认状态冲突，请重试");
    }

    /**
     * 原子核销已通过全部策略的 Attempt 会话并返回用户名
     * <p>
     * 仅供 {@code LoginFlowAuthenticationProvider} 调用：
     * 采用 CAS 将状态从 {@link AttemptStatus#SUCCEEDED} 原子置为 {@link AttemptStatus#CONSUMED}，
     * 彻底杜绝重放攻击与令牌重复签发。
     *
     * @param id             Attempt 会话 ID
     * @param browserBinding 浏览器绑定 Cookie
     * @return 最终认证合法的用户名
     */
    public String consumeCompleted(String id, String browserBinding) {
        for (int tries = 0; tries < 12; tries++) {
            LoginAttempt attempt = load(id);
            requireBrowser(attempt, browserBinding);

            if (attempt.getStatus() != AttemptStatus.SUCCEEDED) {
                throw ApiException.forbidden("认证流程尚未达成或已完成核销");
            }
            if (attempt.getSubjectUserId() == null) {
                throw ApiException.forbidden("认证用户不存在或已被禁用");
            }

            attempt.setStatus(AttemptStatus.CONSUMED);
            attempt.setRevision(attempt.getRevision() + 1);
            if (attemptRepository.compareAndSet(id, attempt.getRevision() - 1, attempt)) {
                return attempt.getUsername();
            }
        }
        throw ApiException.conflict("会话核销状态冲突");
    }

    // ================================= 内部辅助与状态计算 =================================

    private void collectSteps(PolicyNode node, Map<String, FactorStep> steps) {
        if (node.op() == PolicyNode.Op.FACTOR) {
            steps.put(node.id(), new FactorStep(node.id(), node.factor()));
        } else if (node.children() != null) {
            node.children().forEach(c -> collectSteps(c, steps));
        }
    }

    private LoginAttempt load(String id) {
        return attemptRepository.find(id)
                .orElseThrow(() -> ApiException.notFound("登录认证流程不存在或已过期"));
    }

    private void requireBrowser(LoginAttempt attempt, String binding) {
        if (binding == null || !SecureValues.constantEquals(attempt.getBindingHash(), SecureValues.sha256(binding))) {
            throw ApiException.forbidden("非法的设备绑定凭证 (AUTH_BIND mismatch)");
        }
    }

    private FactorStep requireEligible(LoginAttempt attempt, String nodeId) {
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw ApiException.forbidden("登录认证流程已结束");
        }
        FactorStep step = attempt.getSteps().get(nodeId);
        if (step == null || !attempt.decision().availableNodes().contains(nodeId)) {
            throw ApiException.forbidden("当前认证步骤暂不可用");
        }
        return step;
    }

    private void refresh(LoginAttempt attempt) {
        var result = attempt.decision().status();
        if (result == PolicyEvaluator.Status.SATISFIED) {
            attempt.setStatus(AttemptStatus.SUCCEEDED);
        } else if (result == PolicyEvaluator.Status.IMPOSSIBLE) {
            attempt.setStatus(AttemptStatus.FAILED);
        }
    }

    private AttemptResponse toAttemptResponse(LoginAttempt attempt) {
        List<FactorOptionResponse> available = attempt.decision().availableNodes().stream()
                .map(id -> new FactorOptionResponse(id, attempt.getSteps().get(id).getType()))
                .toList();

        Map<String, String> progress = new LinkedHashMap<>();
        for (FactorStep step : attempt.getSteps().values()) {
            progress.put(step.getNodeId(), step.isVerified() ? "VERIFIED" :
                    step.isExhausted() ? "EXHAUSTED" :
                            step.getChallengeHash() != null ? "CHALLENGE_ISSUED" : "PENDING");
        }

        return new AttemptResponse(
                attempt.getId(),
                attempt.getPolicyCode(),
                attempt.getPolicyVersion(),
                attempt.getStatus(),
                attempt.getExpiresAt(),
                available,
                progress,
                attempt.getStatus() == AttemptStatus.SUCCEEDED
        );
    }

    // 保留向后兼容类型别名 (方便现有测试调用)
    public record FactorOption(String nodeId, FactorType type) {}
    public record AttemptView(String attemptId, String policyCode, int policyVersion,
                              AttemptStatus status, Instant expiresAt,
                              List<FactorOption> available, Map<String, String> progress,
                              boolean canComplete) {}
    public record StartResult(AttemptResponse attempt, Instant challengeExpiresAt, String qrTicket) {}
    public record VerifyResult(AttemptResponse attempt, boolean verified) {}
}
