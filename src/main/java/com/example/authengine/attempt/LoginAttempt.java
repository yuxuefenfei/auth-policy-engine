package com.example.authengine.attempt;

import com.example.authengine.domain.PolicyEvaluator;
import com.example.authengine.domain.PolicyNode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一次登录流程快照；published policy 改变不影响运行中的 attempt。
 */
@Getter
@Setter
@NoArgsConstructor
public class LoginAttempt {
    private String id;
    private String username;
    private Long subjectUserId;
    private String bindingHash;
    private String policyCode;
    private int policyVersion;
    private PolicyNode root;
    private AttemptStatus status = AttemptStatus.IN_PROGRESS;
    private Instant createdAt;
    private Instant expiresAt;
    private long revision;
    private Map<String, FactorStep> steps = new LinkedHashMap<>();

    public PolicyEvaluator.Decision decision() {
        return PolicyEvaluator.evaluate(root, nodeId -> steps.get(nodeId).leafState());
    }
}
