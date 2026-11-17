package com.example.authengine.dto.response;

/**
 * 最终签发的安全会话访问令牌响应 DTO
 *
 * @param accessToken 签发的访问令牌字符串 (Opaque Token)
 * @param tokenType   令牌协议类型 (固定为 Bearer)
 * @param expiresIn   有效时长 (单位：秒)
 */
public record SessionTokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {
}
