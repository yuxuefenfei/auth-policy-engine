package com.example.authengine.security;

import java.time.Duration;
import java.util.Optional;

/**
 * 服务端 opaque session：存储随机令牌的 SHA-256，不持久化原始令牌。
 */
public interface LoginSessionStore {
    void save(String tokenHash, String username, Duration ttl);

    Optional<String> findUsername(String tokenHash);
}
