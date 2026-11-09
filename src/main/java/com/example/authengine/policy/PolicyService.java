package com.example.authengine.policy;

import com.example.authengine.common.ApiException;
import com.example.authengine.common.SecureValues;
import com.example.authengine.domain.PolicyNode;
import com.example.authengine.domain.PolicyValidator;
import com.example.authengine.factor.FactorRegistry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 策略主表的 activeVersion 是唯一生效指针。新 Attempt 必须每次读取数据库，
 * 不缓存指针，因此提交事务后即可跨实例观察新版本。
 */
@Service
@RequiredArgsConstructor
public class PolicyService {
    private final PolicyRepository policies;
    private final PolicyVersionRepository versions;
    private final FactorRegistry factors;
    private final ObjectMapper mapper;

    @Transactional(readOnly = true)
    public PolicySnapshot active(String code) {
        PolicyEntity policy = policies.findByPolicyCode(code)
                .orElseThrow(() -> ApiException.notFound("登录策略不存在"));
        if (policy.getActiveVersion() <= 0) throw ApiException.conflict("策略尚未发布");
        PolicyVersionEntity revision = versions.findByPolicyCodeAndVersionNumber(code, policy.getActiveVersion())
                .orElseThrow(() -> ApiException.conflict("策略版本损坏"));
        return new PolicySnapshot(code, revision.getVersionNumber(), parse(revision.getDefinition()));
    }

    @Transactional
    public VersionInfo saveDraft(String code, PolicyNode root) {
        validate(root);
        PolicyEntity policy = policies.lockByCode(code).orElseGet(() -> policies.saveAndFlush(new PolicyEntity(code)));
        int version = policy.getLatestVersion() + 1;
        policy.setLatestVersion(version);
        PolicyVersionEntity item = new PolicyVersionEntity();
        item.setPolicyCode(code);
        item.setVersionNumber(version);
        item.setDefinition(serialize(root));
        item.setChecksum(SecureValues.sha256(item.getDefinition()));
        item.setStatus("DRAFT");
        item.setCreatedAt(Instant.now());
        versions.save(item);
        return info(item);
    }

    @Transactional
    public VersionInfo publish(String code, int version) {
        PolicyEntity policy = policies.lockByCode(code)
                .orElseThrow(() -> ApiException.notFound("登录策略不存在"));
        PolicyVersionEntity item = versions.findByPolicyCodeAndVersionNumber(code, version)
                .orElseThrow(() -> ApiException.notFound("策略版本不存在"));
        // 发布时再次校验：防止运行中的认证器升级或下线导致策略不可执行。
        validate(parse(item.getDefinition()));
        item.setStatus("PUBLISHED");
        item.setPublishedAt(Instant.now());
        policy.setActiveVersion(version);
        // 数据库事务提交后，新流程立即读取此版本；已有流程继续使用快照。
        return info(item);
    }

    @Transactional(readOnly = true)
    public List<VersionInfo> listVersions(String code) {
        return versions.findAllByPolicyCodeOrderByVersionNumberDesc(code).stream().map(this::info).toList();
    }

    @Transactional
    public void ensureBootstrap(String code, PolicyNode root) {
        if (policies.findByPolicyCode(code).isPresent()) return;
        validate(root);
        PolicyEntity p = new PolicyEntity(code);
        p.setActiveVersion(1);
        p.setLatestVersion(1);
        policies.saveAndFlush(p);
        PolicyVersionEntity v = new PolicyVersionEntity();
        v.setPolicyCode(code);
        v.setVersionNumber(1);
        v.setDefinition(serialize(root));
        v.setChecksum(SecureValues.sha256(v.getDefinition()));
        v.setStatus("PUBLISHED");
        v.setCreatedAt(Instant.now());
        v.setPublishedAt(Instant.now());
        versions.save(v);
    }

    private void validate(PolicyNode root) {
        try {
            PolicyValidator.validate(root, factors.installedTypes());
        } catch (IllegalArgumentException e) {
            throw ApiException.bad(e.getMessage());
        }
    }

    private String serialize(PolicyNode node) {
        try {
            return mapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw ApiException.bad("策略序列化失败");
        }
    }

    private PolicyNode parse(String text) {
        try {
            return mapper.readValue(text, PolicyNode.class);
        } catch (JsonProcessingException e) {
            throw ApiException.conflict("策略定义格式错误");
        }
    }

    private VersionInfo info(PolicyVersionEntity v) {
        return new VersionInfo(v.getVersionNumber(), v.getStatus(), v.getChecksum(), v.getPublishedAt());
    }

    public record PolicySnapshot(String code, int version, PolicyNode root) {
    }

    public record VersionInfo(int version, String status, String checksum, Instant publishedAt) {
    }
}
