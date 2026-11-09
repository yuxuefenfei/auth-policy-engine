package com.example.authengine.domain;

import java.util.List;

/**
 * 受限的认证策略 AST。不能执行用户提供的 SpEL / Groovy 代码。
 * FACTOR 必须有 id + factor；其他节点必须包含 children。
 */
public record PolicyNode(
        Op op,
        String id,
        FactorType factor,
        Integer required,
        List<PolicyNode> children
) {
    public enum Op {FACTOR, ALL, ANY, AT_LEAST, SEQUENCE}
}
