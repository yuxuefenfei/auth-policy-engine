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
        throw new UnsupportedOperationException("该认证器无需发起挑战");
    }

    default void deliver(LoginAttempt attempt, PreparedChallenge challenge) {
    }

    boolean verify(LoginAttempt attempt, FactorStep step, String credential);

    /**
     * secretHash 存入 Redis；deliverySecret 仅供发送通道使用，不能持久化或返回给浏览器。
     */
    record PreparedChallenge(String secretHash, Instant expiresAt,
                             String deliverySecret, String displayTicket) {
    }
}
