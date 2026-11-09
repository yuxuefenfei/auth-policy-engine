package com.example.authengine.security;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.auth.storage", havingValue = "redis")
public class RedisLoginSessionStore implements LoginSessionStore {
    private final StringRedisTemplate redis;

    @Override
    public void save(String hash, String username, Duration ttl) {
        redis.opsForValue().set("auth:session:" + hash, username, ttl);
    }

    @Override
    public Optional<String> findUsername(String hash) {
        return Optional.ofNullable(redis.opsForValue().get("auth:session:" + hash));
    }
}
