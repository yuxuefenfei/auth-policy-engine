package com.example.authengine.attempt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 多实例共享状态。Lua CAS 同时完成 revision 比对、内容更新和 TTL 保留。
 * 所有节点必须使用同一 Redis 主集群；Redis 不可用时失败关闭。
 */
@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.auth.storage", havingValue = "redis")
public class RedisAttemptRepository implements AttemptRepository {
    private static final String PREFIX = "auth:attempt:";
    private static final DefaultRedisScript<Long> CAS = new DefaultRedisScript<>("""
            local v = redis.call('GET', KEYS[1])
            if not v then return 0 end
            local obj = cjson.decode(v)
            if tonumber(obj.revision) ~= tonumber(ARGV[1]) then return -1 end
            redis.call('SET', KEYS[1], ARGV[2], 'EX', tonumber(ARGV[3]))
            return 1
            """, Long.class);
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    @Override
    public boolean insert(LoginAttempt attempt) {
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(PREFIX + attempt.getId(),
                encode(attempt), ttl(attempt)));
    }

    @Override
    public Optional<LoginAttempt> find(String id) {
        String json = redis.opsForValue().get(PREFIX + id);
        if (json == null) return Optional.empty();
        try {
            LoginAttempt attempt = mapper.readValue(json, LoginAttempt.class);
            return attempt.getExpiresAt().isAfter(Instant.now()) ? Optional.of(attempt) : Optional.empty();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Redis 登录状态无法反序列化", e);
        }
    }

    @Override
    public boolean compareAndSet(String id, long expectedRevision, LoginAttempt next) {
        Long updated = redis.execute(CAS, List.of(PREFIX + id),
                String.valueOf(expectedRevision), encode(next), String.valueOf(ttl(next).toSeconds()));
        return Long.valueOf(1L).equals(updated);
    }

    private Duration ttl(LoginAttempt attempt) {
        return Duration.ofSeconds(Math.max(1, Duration.between(Instant.now(), attempt.getExpiresAt()).toSeconds()));
    }

    private String encode(LoginAttempt attempt) {
        try {
            return mapper.writeValueAsString(attempt);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("登录状态序列化失败", e);
        }
    }
}
