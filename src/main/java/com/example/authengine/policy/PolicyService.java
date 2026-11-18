package com.example.authengine.policy;

import com.example.authengine.common.ApiException;
import com.example.authengine.common.SecureValues;
import com.example.authengine.domain.PolicyNode;
import com.example.authengine.domain.PolicyValidator;
import com.example.authengine.dto.response.PolicySnapshotResponse;
import com.example.authengine.dto.response.PolicyVersionResponse;
import com.example.authengine.entity.PolicyEntity;
import com.example.authengine.entity.PolicyVersionEntity;
import com.example.authengine.factor.FactorRegistry;
import com.example.authengine.mapper.PolicyMapper;
import com.example.authengine.mapper.PolicyVersionMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 动态认证策略核心编排服务
 * <p>
 * 职责：
 * 1. 策略版本全生命周期管理（草稿保存、语法校验、版本发布与生效指针切换）；
 * 2. 保证高并发策略发布时的行级锁互斥与快照隔离；
 * 3. 策略主表的 {@code activeVersion} 作为唯一生效指针，所有新登录流程均实时读取当前版本；
 * 4. 采用 MyBatis-Flex 进行实体持久化操作。
 */
@Service
@RequiredArgsConstructor
public class PolicyService {

    private final PolicyMapper policyMapper;
    private final PolicyVersionMapper policyVersionMapper;
    private final FactorRegistry factorRegistry;
    private final ObjectMapper objectMapper;

    /**
     * 获取指定策略当前已发布的生效版本快照
     * <p>
     * 每次创建登录 Attempt 时均实时读取数据库，确保策略热更后新会话立即生效。
     *
     * @param code 策略唯一代码 (如 WEB_LOGIN)
     * @return 包含版本号及完整 AST 语法树的策略快照
     * @throws ApiException 若策略不存在或尚未发布任何版本
     */
    @Transactional(readOnly = true)
    public PolicySnapshotResponse active(String code) {
        PolicyEntity policy = policyMapper.findByPolicyCode(code)
                .orElseThrow(() -> ApiException.notFound("登录策略不存在: " + code));

        if (policy.getActiveVersion() <= 0) {
            throw ApiException.conflict("策略尚未发布生效版本");
        }

        PolicyVersionEntity revision = policyVersionMapper.findByPolicyCodeAndVersionNumber(code, policy.getActiveVersion())
                .orElseThrow(() -> ApiException.conflict("策略版本定义已损坏或丢失"));

        return new PolicySnapshotResponse(code, revision.getVersionNumber(), parse(revision.getDefinition()));
    }

    /**
     * 保存新的策略草稿版本
     * <p>
     * 执行静态 AST 合法性校验，利用行级锁锁定主表并分配递增的最新版本号，生成不可篡改的 SHA-256 摘要。
     *
     * @param code 策略唯一代码
     * @param root 待保存的策略 AST 根节点
     * @return 新建的草稿版本摘要信息
     */
    @Transactional
    public PolicyVersionResponse saveDraft(String code, PolicyNode root) {
        validate(root);

        // 使用 FOR UPDATE 行级排他锁锁定策略记录，若不存在则初始化创建
        PolicyEntity policy = policyMapper.lockByCode(code).orElseGet(() -> {
            PolicyEntity newPolicy = new PolicyEntity(code);
            policyMapper.insertSelective(newPolicy);
            return newPolicy;
        });

        int nextVersion = policy.getLatestVersion() + 1;
        policy.setLatestVersion(nextVersion);
        policyMapper.update(policy);

        String jsonDefinition = serialize(root);
        PolicyVersionEntity item = PolicyVersionEntity.builder()
                .policyCode(code)
                .versionNumber(nextVersion)
                .definition(jsonDefinition)
                .checksum(SecureValues.sha256(jsonDefinition))
                .status("DRAFT")
                .createdAt(Instant.now())
                .build();

        policyVersionMapper.insertSelective(item);
        return toVersionResponse(item);
    }

    /**
     * 发布指定版本的策略，使其成为当前全局生效的认证规则
     * <p>
     * 在事务内完成 AST 再次校验、状态变更与 activeVersion 指针切换。事务提交后，
     * 所有新发起的登录认证将立即绑定该版本，已有认证流程继续按各自快照执行。
     *
     * @param code    策略唯一代码
     * @param version 待发布的版本号
     * @return 发布成功的版本摘要信息
     */
    @Transactional
    public PolicyVersionResponse publish(String code, int version) {
        PolicyEntity policy = policyMapper.lockByCode(code)
                .orElseThrow(() -> ApiException.notFound("登录策略不存在: " + code));

        PolicyVersionEntity item = policyVersionMapper.findByPolicyCodeAndVersionNumber(code, version)
                .orElseThrow(() -> ApiException.notFound("指定的策略版本不存在: v" + version));

        // 发布前二次校验：防止系统运行期内因认证器下线或升级导致不可执行
        validate(parse(item.getDefinition()));

        item.setStatus("PUBLISHED");
        item.setPublishedAt(Instant.now());
        policyVersionMapper.update(item);

        policy.setActiveVersion(version);
        policyMapper.update(policy);

        return toVersionResponse(item);
    }

    /**
     * 查询指定策略的所有版本历史快照列表，按版本号从新到旧排序
     *
     * @param code 策略唯一代码
     * @return 策略历史版本列表
     */
    @Transactional(readOnly = true)
    public List<PolicyVersionResponse> listVersions(String code) {
        return policyVersionMapper.findAllByPolicyCodeOrderByVersionNumberDesc(code).stream()
                .map(this::toVersionResponse)
                .toList();
    }

    /**
     * 系统冷启动时的默认策略初始化引导
     * <p>
     * 若数据库中尚未存在该策略，则自动创建并发布 Version 1。
     *
     * @param code 策略代码
     * @param root 初始默认策略 AST
     */
    @Transactional
    public void ensureBootstrap(String code, PolicyNode root) {
        if (policyMapper.findByPolicyCode(code).isPresent()) {
            return;
        }

        validate(root);

        PolicyEntity p = new PolicyEntity(code);
        p.setActiveVersion(1);
        p.setLatestVersion(1);
        policyMapper.insertSelective(p);

        String json = serialize(root);
        PolicyVersionEntity v = PolicyVersionEntity.builder()
                .policyCode(code)
                .versionNumber(1)
                .definition(json)
                .checksum(SecureValues.sha256(json))
                .status("PUBLISHED")
                .createdAt(Instant.now())
                .publishedAt(Instant.now())
                .build();

        policyVersionMapper.insertSelective(v);
    }

    /**
     * 校验策略 AST 是否符合语法及当前已注册因子的要求
     */
    private void validate(PolicyNode root) {
        try {
            PolicyValidator.validate(root, factorRegistry.installedTypes());
        } catch (IllegalArgumentException e) {
            throw ApiException.bad(e.getMessage());
        }
    }

    /**
     * 将策略 AST 对象序列化为 JSON 字符串
     */
    private String serialize(PolicyNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw ApiException.bad("策略语法树序列化失败");
        }
    }

    /**
     * 将 JSON 字符串反序列化为策略 AST 根节点
     */
    private PolicyNode parse(String text) {
        try {
            return objectMapper.readValue(text, PolicyNode.class);
        } catch (JsonProcessingException e) {
            throw ApiException.conflict("策略语法树定义格式损坏");
        }
    }

    /**
     * 实体转响应 DTO
     */
    private PolicyVersionResponse toVersionResponse(PolicyVersionEntity v) {
        return new PolicyVersionResponse(v.getVersionNumber(), v.getStatus(), v.getChecksum(), v.getPublishedAt());
    }

    // 保留向后兼容内部类型定义 (便于老测试或内部类直接引用)
    @SuppressWarnings("unused")
    public record PolicySnapshot(String code, int version, PolicyNode root) {
    }

    @SuppressWarnings("unused")
    public record VersionInfo(int version, String status, String checksum, Instant publishedAt) {
    }
}
