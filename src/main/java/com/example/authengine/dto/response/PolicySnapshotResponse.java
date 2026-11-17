package com.example.authengine.dto.response;

import com.example.authengine.domain.PolicyNode;

/**
 * 策略定义快照响应 DTO
 *
 * @param code    策略唯一代码
 * @param version 策略快照版本号
 * @param root    策略 AST 根节点定义
 */
public record PolicySnapshotResponse(
        String code,
        int version,
        PolicyNode root
) {
}
