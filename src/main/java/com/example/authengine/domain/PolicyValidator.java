package com.example.authengine.domain;

import java.util.HashSet;
import java.util.Set;

/**
 * 先校验后发布：规避畸形 AST、无限嵌套、重复认证计数和未安装的认证器。
 */
public final class PolicyValidator {
    private PolicyValidator() {
    }

    public static void validate(PolicyNode root, Set<FactorType> installed) {
        Counter c = new Counter();
        visit(root, 1, c, installed);
        if (c.leaves == 0) throw new IllegalArgumentException("策略必须包含认证因子");
    }

    private static void visit(PolicyNode node, int depth, Counter c, Set<FactorType> installed) {
        if (node == null || node.op() == null) throw new IllegalArgumentException("策略节点类型不能为空");
        if (depth > 8 || ++c.nodes > 32) throw new IllegalArgumentException("策略树超出复杂度限制");
        var children = node.children();
        if (node.op() == PolicyNode.Op.FACTOR) {
            if (node.id() == null || !node.id().matches("[A-Za-z][A-Za-z0-9_-]{1,63}"))
                throw new IllegalArgumentException("FACTOR 必须具备合法的唯一 id");
            if (!c.ids.add(node.id())) throw new IllegalArgumentException("重复节点 id: " + node.id());
            if (node.factor() == null || !installed.contains(node.factor()))
                throw new IllegalArgumentException("认证器未安装或不可用: " + node.factor());
            // 同一个认证器不能在 AT_LEAST 的不同路径上被重复计算为两个独立因素。
            if (!c.factors.add(node.factor())) throw new IllegalArgumentException("重复使用认证方式: " + node.factor());
            if (children != null && !children.isEmpty()) throw new IllegalArgumentException("FACTOR 不能有 children");
            if (node.required() != null) throw new IllegalArgumentException("FACTOR 不需要 required");
            c.leaves++;
            if (c.leaves > 16) throw new IllegalArgumentException("认证因子超过 16 个");
            return;
        }
        if (node.id() != null || node.factor() != null)
            throw new IllegalArgumentException("组合节点不能有 id / factor");
        if (children == null || children.size() < 2) throw new IllegalArgumentException("组合节点至少有两个子节点");
        if (node.op() == PolicyNode.Op.AT_LEAST) {
            if (node.required() == null || node.required() < 1 || node.required() > children.size())
                throw new IllegalArgumentException("AT_LEAST 的 required 超出范围");
        } else if (node.required() != null) {
            throw new IllegalArgumentException("非 AT_LEAST 节点不能有 required");
        }
        for (PolicyNode child : children) visit(child, depth + 1, c, installed);
    }

    private static final class Counter {
        final Set<String> ids = new HashSet<>();
        final Set<FactorType> factors = new HashSet<>();
        int nodes;
        int leaves;
    }
}
