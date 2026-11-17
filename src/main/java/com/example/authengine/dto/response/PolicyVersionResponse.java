package com.example.authengine.dto.response;

import java.time.Instant;

/**
 * 策略历史版本摘要响应 DTO
 *
 * @param version     版本序列号
 * @param status      版本状态 (DRAFT / PUBLISHED)
 * @param checksum    SHA256 完整性摘要
 * @param publishedAt 发布生效时间戳 (未发布为 null)
 */
public record PolicyVersionResponse(
        int version,
        String status,
        String checksum,
        Instant publishedAt
) {
}
