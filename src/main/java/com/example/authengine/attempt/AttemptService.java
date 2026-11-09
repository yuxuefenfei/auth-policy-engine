package com.example.authengine.attempt;

import com.example.authengine.common.ApiException;
import com.example.authengine.common.SecureValues;
import com.example.authengine.config.AuthProperties;
import com.example.authengine.domain.FactorType;
import com.example.authengine.domain.PolicyEvaluator;
import com.example.authengine.domain.PolicyNode;
import com.example.authengine.factor.FactorHandler;
import com.example.authengine.factor.FactorRegistry;
import com.example.authengine.policy.PolicyService;
import com.example.authengine.user.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 登录状态机。每次更新用 CAS，避免短信/扫码/密码的并发覆盖及重复消费。
 * 策略快照保存在 Attempt 内，运行中不再读取当前策略指针。
 */
@Service
@RequiredArgsConstructor
public class AttemptService {
    private final AttemptRepository store;
    private final PolicyService policies;
    private final AppUserRepository users;
    private final FactorRegistry factors;
    private final AuthProperties config;

    public AttemptView create(String username, String browserBinding) {
        PolicyService.PolicySnapshot policy = policies.active(config.getPolicyCode());
        LoginAttempt attempt = new LoginAttempt();
        attempt.setId(SecureValues.randomToken(24));
        attempt.setUsername(username.trim());
        attempt.setSubjectUserId(users.findByUsername(username.trim())
                .filter(u -> u.isEnabled()).map(u -> u.getId()).orElse(null));
        attempt.setBindingHash(SecureValues.sha256(browserBinding));
        attempt.setPolicyCode(policy.code());
        attempt.setPolicyVersion(policy.version());
        attempt.setRoot(policy.root());
        attempt.setCreatedAt(Instant.now());
        attempt.setExpiresAt(attempt.getCreatedAt().plusSeconds(config.getAttemptTtlSeconds()));
        Map<String, FactorStep> steps = new LinkedHashMap<>();
        collectSteps(policy.root(), steps);
        attempt.setSteps(steps);
        if (!store.insert(attempt)) throw ApiException.conflict("无法创建登录流程，请重试");
        return viewOf(attempt);
    }

    private void collectSteps(PolicyNode node, Map<String, FactorStep> steps) {
        if (node.op() == PolicyNode.Op.FACTOR) {
            steps.put(node.id(), new FactorStep(node.id(), node.factor()));
        } else {
            node.children().forEach(c -> collectSteps(c, steps));
        }
    }

    public AttemptView status(String id, String browserBinding) {
        LoginAttempt attempt = load(id);
        requireBrowser(attempt, browserBinding);
        return viewOf(attempt);
    }

    public StartResult start(String id, String nodeId, String binding) {
        for (int tries = 0; tries < 12; tries++) {
            LoginAttempt attempt = load(id);
            requireBrowser(attempt, binding);
            FactorStep step = requireEligible(attempt, nodeId);
            if (step.getType() == FactorType.PASSWORD) throw ApiException.bad("密码认证无需开始挑战");
            FactorHandler handler = factors.require(step.getType());
            Instant now = Instant.now();
            if (step.getChallengeSends() >= config.getMaxChallengeSends())
                throw ApiException.forbidden("挑战发送次数已耗尽");
            if (step.getLastChallengeAt() != null &&
                    step.getLastChallengeAt().plusSeconds(config.getChallengeCooldownSeconds()).isAfter(now))
                throw ApiException.conflict("挑战发送过于频繁");
            FactorHandler.PreparedChallenge challenge = handler.prepare(attempt);
            step.setChallengeHash(challenge.secretHash());
            step.setChallengeExpiresAt(challenge.expiresAt());
            step.setChallengeSends(step.getChallengeSends() + 1);
            step.setLastChallengeAt(now);
            attempt.setRevision(attempt.getRevision() + 1);
            if (store.compareAndSet(id, attempt.getRevision() - 1, attempt)) {
                // 只有 CAS 获胜的请求才能发短信（避免冲突重试发出无效验证码）。
                handler.deliver(attempt, challenge);
                return new StartResult(viewOf(attempt), challenge.expiresAt(), challenge.displayTicket());
            }
        }
        throw ApiException.conflict("登录状态冲突，请稍后重试");
    }

    public VerifyResult verify(String id, String nodeId, String binding, String credential) {
        for (int tries = 0; tries < 12; tries++) {
            LoginAttempt attempt = load(id);
            requireBrowser(attempt, binding);
            FactorStep step = requireEligible(attempt, nodeId);
            if (step.getType() == FactorType.APP_QR_APPROVAL)
                throw ApiException.bad("扫码必须由已认证 APP 确认");
            boolean success = factors.require(step.getType()).verify(attempt, step, credential);
            if (success) {
                step.setVerified(true);
                step.setVerifiedAt(Instant.now());
                step.setChallengeHash(null);
                step.setChallengeExpiresAt(null);
            } else {
                step.setFailures(step.getFailures() + 1);
                if (step.getFailures() >= config.getMaxFactorFailures()) step.setExhausted(true);
            }
            refresh(attempt);
            attempt.setRevision(attempt.getRevision() + 1);
            if (store.compareAndSet(id, attempt.getRevision() - 1, attempt)) {
                return new VerifyResult(viewOf(attempt), success);
            }
        }
        throw ApiException.conflict("登录状态冲突，请稍后重试");
    }

    /**
     * APP 专属确认入口：使用独立的 APP 认证主体，不能接受浏览器传来的用户名。
     */
    public AttemptView approveQr(String ticket, String approvedByUsername) {
        if (ticket == null || ticket.length() > 256) throw ApiException.bad("二维码无效");
        int pos = ticket.indexOf('.');
        if (pos <= 0) throw ApiException.bad("二维码无效");
        String id = ticket.substring(0, pos);
        for (int tries = 0; tries < 12; tries++) {
            LoginAttempt attempt = load(id);
            if (attempt.getSubjectUserId() == null || !attempt.getUsername().equals(approvedByUsername))
                throw ApiException.forbidden("扫码账号与登录账号不一致");
            FactorStep step = attempt.getSteps().values().stream()
                    .filter(s -> s.getType() == FactorType.APP_QR_APPROVAL).findFirst()
                    .orElseThrow(() -> ApiException.bad("当前策略不支持扫码"));
            requireEligible(attempt, step.getNodeId());
            if (step.getChallengeHash() == null || step.getChallengeExpiresAt() == null ||
                    !step.getChallengeExpiresAt().isAfter(Instant.now()) ||
                    !SecureValues.constantEquals(step.getChallengeHash(), SecureValues.sha256(ticket)))
                throw ApiException.forbidden("二维码不存在或已过期");
            step.setVerified(true);
            step.setVerifiedAt(Instant.now());
            step.setChallengeHash(null);
            step.setChallengeExpiresAt(null);
            refresh(attempt);
            attempt.setRevision(attempt.getRevision() + 1);
            if (store.compareAndSet(id, attempt.getRevision() - 1, attempt)) return viewOf(attempt);
        }
        throw ApiException.conflict("扫码确认状态冲突，请重试");
    }

    /**
     * 仅供自定义 AuthenticationProvider 调用：原子消费防止重复签发会话。
     */
    public String consumeCompleted(String id, String binding) {
        for (int tries = 0; tries < 12; tries++) {
            LoginAttempt attempt = load(id);
            requireBrowser(attempt, binding);
            if (attempt.getStatus() != AttemptStatus.SUCCEEDED)
                throw ApiException.forbidden("认证流程尚未全部完成或已经消费");
            if (attempt.getSubjectUserId() == null)
                throw ApiException.forbidden("登录用户无效");
            attempt.setStatus(AttemptStatus.CONSUMED);
            attempt.setRevision(attempt.getRevision() + 1);
            if (store.compareAndSet(id, attempt.getRevision() - 1, attempt)) return attempt.getUsername();
        }
        throw ApiException.conflict("登录完成状态冲突");
    }

    private LoginAttempt load(String id) {
        return store.find(id).orElseThrow(() -> ApiException.notFound("登录流程不存在或已过期"));
    }

    private void requireBrowser(LoginAttempt attempt, String binding) {
        if (binding == null || !SecureValues.constantEquals(
                attempt.getBindingHash(), SecureValues.sha256(binding)))
            throw ApiException.forbidden("登录流程不属于当前浏览器");
    }

    private FactorStep requireEligible(LoginAttempt attempt, String nodeId) {
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS)
            throw ApiException.forbidden("登录流程已结束");
        FactorStep step = attempt.getSteps().get(nodeId);
        if (step == null || !attempt.decision().availableNodes().contains(nodeId))
            throw ApiException.forbidden("当前步骤不可执行");
        return step;
    }

    private void refresh(LoginAttempt attempt) {
        var result = attempt.decision().status();
        if (result == PolicyEvaluator.Status.SATISFIED) attempt.setStatus(AttemptStatus.SUCCEEDED);
        if (result == PolicyEvaluator.Status.IMPOSSIBLE) attempt.setStatus(AttemptStatus.FAILED);
    }

    private AttemptView viewOf(LoginAttempt attempt) {
        List<FactorOption> available = attempt.decision().availableNodes().stream()
                .map(id -> new FactorOption(id, attempt.getSteps().get(id).getType())).toList();
        Map<String, String> progress = new LinkedHashMap<>();
        for (FactorStep step : attempt.getSteps().values()) {
            progress.put(step.getNodeId(), step.isVerified() ? "VERIFIED" :
                    step.isExhausted() ? "EXHAUSTED" :
                            step.getChallengeHash() != null ? "CHALLENGE_ISSUED" : "PENDING");
        }
        return new AttemptView(attempt.getId(), attempt.getPolicyCode(), attempt.getPolicyVersion(),
                attempt.getStatus(), attempt.getExpiresAt(), available, progress,
                attempt.getStatus() == AttemptStatus.SUCCEEDED);
    }

    public record FactorOption(String nodeId, FactorType type) {
    }

    public record AttemptView(String attemptId, String policyCode, int policyVersion,
                              AttemptStatus status, Instant expiresAt,
                              List<FactorOption> available, Map<String, String> progress,
                              boolean canComplete) {
    }

    public record StartResult(AttemptView attempt, Instant challengeExpiresAt, String qrTicket) {
    }

    public record VerifyResult(AttemptView attempt, boolean verified) {
    }
}
