package com.example.authengine.security;

import com.example.authengine.common.SecureValues;
import com.example.authengine.dto.response.SessionTokenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * 认证会话令牌服务 (Session Token Service)
 * <p>
 * 采用不可逆哈希存储的 Opaque Token 机制：
 * 客户端持有高熵随机令牌原始值，服务端仅持久化其 SHA-256 摘要，防止数据库泄露引发令牌劫持。
 */
@Service
@RequiredArgsConstructor
public class SessionTokenService {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(30);
    private final LoginSessionStore sessionStore;

    /**
     * 为认证成功的用户签发访问令牌
     *
     * @param username 已通过全量多因子认证的用户名
     * @return 包含明文 Token 及有效期的响应 DTO
     */
    public SessionTokenResponse issue(String username) {
        String rawToken = SecureValues.randomToken(32);
        sessionStore.save(SecureValues.sha256(rawToken), username, DEFAULT_TTL);
        return new SessionTokenResponse(rawToken, "Bearer", DEFAULT_TTL.toSeconds());
    }

    /**
     * 核验客户端请求头携带的 Bearer Token
     *
     * @param token 客户端传入的明文令牌
     * @return 匹配的用户标识 Optional 包装
     */
    public Optional<String> authenticate(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
            return Optional.empty();
        }
        return sessionStore.findUsername(SecureValues.sha256(token));
    }

    // 保留向后兼容类型别名
    public record SessionToken(String accessToken, String tokenType, long expiresIn) {}
}
