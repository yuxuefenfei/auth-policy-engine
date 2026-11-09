package com.example.authengine.attempt;

import com.example.authengine.domain.FactorType;
import com.example.authengine.domain.PolicyEvaluator;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * 单个 AST 叶子节点的挑战和尝试记录，不存储任何明文凭据。
 */
@Getter
@Setter
@NoArgsConstructor
public class FactorStep {
    private String nodeId;
    private FactorType type;
    private int failures;
    private boolean verified;
    private boolean exhausted;
    private Instant verifiedAt;
    private String challengeHash;
    private Instant challengeExpiresAt;
    private Instant lastChallengeAt;
    private int challengeSends;

    public FactorStep(String nodeId, FactorType type) {
        this.nodeId = nodeId;
        this.type = type;
    }

    public PolicyEvaluator.LeafState leafState() {
        if (verified) return PolicyEvaluator.LeafState.VERIFIED;
        if (exhausted) return PolicyEvaluator.LeafState.EXHAUSTED;
        return PolicyEvaluator.LeafState.PENDING;
    }
}
