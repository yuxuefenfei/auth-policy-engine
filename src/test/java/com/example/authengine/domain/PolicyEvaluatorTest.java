package com.example.authengine.domain;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PolicyEvaluatorTest {
    private static PolicyNode leaf(String id, FactorType factor) {
        return new PolicyNode(PolicyNode.Op.FACTOR, id, factor, null, null);
    }

    private static PolicyNode combine(PolicyNode.Op op, Integer required, PolicyNode... parts) {
        return new PolicyNode(op, null, null, required, List.of(parts));
    }

    @Test
    void andOrShowsOnlyUnfinishedGroup() {
        var root = combine(PolicyNode.Op.ALL, null,
                combine(PolicyNode.Op.ANY, null, leaf("pwd", FactorType.PASSWORD), leaf("qr", FactorType.APP_QR_APPROVAL)),
                leaf("sms", FactorType.SMS_OTP));
        PolicyValidator.validate(root, EnumSet.allOf(FactorType.class));
        var passed = new HashSet<String>();
        var result = PolicyEvaluator.evaluate(root, id -> passed.contains(id)
                ? PolicyEvaluator.LeafState.VERIFIED : PolicyEvaluator.LeafState.PENDING);
        assertEquals(PolicyEvaluator.Status.PENDING, result.status());
        assertEquals(List.of("pwd", "qr", "sms"), result.availableNodes());
        passed.add("pwd");
        assertEquals(List.of("sms"), PolicyEvaluator.evaluate(root,
                        id -> passed.contains(id) ? PolicyEvaluator.LeafState.VERIFIED : PolicyEvaluator.LeafState.PENDING)
                .availableNodes());
        passed.add("sms");
        assertEquals(PolicyEvaluator.Status.SATISFIED, PolicyEvaluator.evaluate(root,
                        id -> passed.contains(id) ? PolicyEvaluator.LeafState.VERIFIED : PolicyEvaluator.LeafState.PENDING)
                .status());
    }

    @Test
    void atLeastBecomesImpossibleWhenTooManyExhausted() {
        var root = combine(PolicyNode.Op.AT_LEAST, 2,
                leaf("pwd", FactorType.PASSWORD), leaf("qr", FactorType.APP_QR_APPROVAL),
                leaf("sms", FactorType.SMS_OTP));
        assertEquals(PolicyEvaluator.Status.IMPOSSIBLE,
                PolicyEvaluator.evaluate(root, id -> id.equals("pwd") || id.equals("qr")
                        ? PolicyEvaluator.LeafState.EXHAUSTED : PolicyEvaluator.LeafState.PENDING).status());
    }

    @Test
    void sequenceRestrictsNextFactor() {
        var root = combine(PolicyNode.Op.SEQUENCE, null,
                leaf("pwd", FactorType.PASSWORD), leaf("sms", FactorType.SMS_OTP));
        assertEquals(List.of("pwd"), PolicyEvaluator.evaluate(root,
                id -> PolicyEvaluator.LeafState.PENDING).availableNodes());
        assertEquals(List.of("sms"), PolicyEvaluator.evaluate(root,
                        id -> id.equals("pwd") ? PolicyEvaluator.LeafState.VERIFIED : PolicyEvaluator.LeafState.PENDING)
                .availableNodes());
    }

    @Test
    void duplicateFactorIsRejected() {
        var root = combine(PolicyNode.Op.AT_LEAST, 2,
                leaf("a", FactorType.PASSWORD), leaf("b", FactorType.PASSWORD));
        assertThrows(IllegalArgumentException.class, () -> PolicyValidator.validate(root,
                EnumSet.allOf(FactorType.class)));
    }

    @Test
    void unavailablePasskeyIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> PolicyValidator.validate(
                leaf("key", FactorType.PASSKEY), EnumSet.of(FactorType.PASSWORD)));
    }
}
