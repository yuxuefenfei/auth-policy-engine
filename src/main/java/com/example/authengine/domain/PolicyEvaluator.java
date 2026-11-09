package com.example.authengine.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 纯函数策略求值器：不依赖 Spring、Redis、数据库或 HTTP。
 * 任何认证器只能提交证据，不能自行决定整次登录是否完成。
 */
public final class PolicyEvaluator {
    private PolicyEvaluator() {
    }

    public static Decision evaluate(PolicyNode root, Function<String, LeafState> states) {
        var status = status(root, states);
        if (status != Status.PENDING) return new Decision(status, List.of());
        List<String> available = new ArrayList<>();
        eligible(root, states, available);
        return new Decision(status, List.copyOf(available));
    }

    private static Status status(PolicyNode node, Function<String, LeafState> states) {
        if (node.op() == PolicyNode.Op.FACTOR) {
            var state = states.apply(node.id());
            if (state == LeafState.VERIFIED) return Status.SATISFIED;
            return state == LeafState.EXHAUSTED ? Status.IMPOSSIBLE : Status.PENDING;
        }
        int ok = 0, impossible = 0;
        for (PolicyNode child : node.children()) {
            Status s = status(child, states);
            if (s == Status.SATISFIED) ok++;
            if (s == Status.IMPOSSIBLE) impossible++;
        }
        int n = node.children().size();
        return switch (node.op()) {
            case ALL, SEQUENCE -> impossible > 0 ? Status.IMPOSSIBLE
                    : ok == n ? Status.SATISFIED : Status.PENDING;
            case ANY -> ok > 0 ? Status.SATISFIED
                    : impossible == n ? Status.IMPOSSIBLE : Status.PENDING;
            case AT_LEAST -> ok >= node.required() ? Status.SATISFIED
                    : ok + (n - ok - impossible) < node.required() ? Status.IMPOSSIBLE : Status.PENDING;
            default -> throw new IllegalStateException("未知组合节点");
        };
    }

    private static void eligible(PolicyNode node, Function<String, LeafState> states, List<String> out) {
        if (status(node, states) != Status.PENDING) return;
        if (node.op() == PolicyNode.Op.FACTOR) {
            out.add(node.id());
        } else if (node.op() == PolicyNode.Op.SEQUENCE) {
            // 顺序节点：只开放第一个尚未满足的子树。
            for (var child : node.children()) {
                if (status(child, states) != Status.SATISFIED) {
                    eligible(child, states, out);
                    break;
                }
            }
        } else {
            for (var child : node.children()) eligible(child, states, out);
        }
    }

    public enum Status {PENDING, SATISFIED, IMPOSSIBLE}

    public enum LeafState {PENDING, VERIFIED, EXHAUSTED}

    public record Decision(Status status, List<String> availableNodes) {
    }
}
