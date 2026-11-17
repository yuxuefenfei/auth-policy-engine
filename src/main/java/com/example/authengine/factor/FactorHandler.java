package com.example.authengine.factor;

import com.example.authengine.attempt.FactorStep;
import com.example.authengine.attempt.LoginAttempt;
import com.example.authengine.domain.FactorType;

import java.time.Instant;

/**
 * 认证器 SPI：不负责策略判断或签发登录令牌。
 */
public interface FactorHandler {
    FactorType type();

    default boolean available() {
        return true;
    }

    default PreparedChallenge prepare(LoginAttempt attempt) {
        throw new UnsupportedOperationException("该认证因子无需预先下发动态验证凭据（直接校验凭据即可）");
    }

    default void deliver(LoginAttempt attempt, PreparedChallenge challenge) {
    }

    boolean verify(LoginAttempt attempt, FactorStep step, String credential);

    /**
     * 预先生成的动态核验凭据包（如短信动态验证码哈希、扫码授权票据等）
     * <p>
     * 安全设计：secretHash 存入 Redis/持久层；deliverySecret 仅供外部发送通道使用，绝不返回前端。
     */
    record PreparedChallenge(String secretHash, Instant expiresAt,
                             String deliverySecret, String displayTicket) {
    }
}
