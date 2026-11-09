package com.example.authengine.attempt;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 单机演示实现；拷贝隔离和 compute 原子替换避免修改已保存对象。
 */
@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.auth.storage", havingValue = "memory", matchIfMissing = true)
public class InMemoryAttemptRepository implements AttemptRepository {
    private final ObjectMapper mapper;
    private final ConcurrentMap<String, LoginAttempt> map = new ConcurrentHashMap<>();

    @Override
    public boolean insert(LoginAttempt attempt) {
        return map.putIfAbsent(attempt.getId(), copy(attempt)) == null;
    }

    @Override
    public Optional<LoginAttempt> find(String id) {
        LoginAttempt item = map.get(id);
        if (item == null) return Optional.empty();
        if (!item.getExpiresAt().isAfter(Instant.now())) {
            map.remove(id, item);
            return Optional.empty();
        }
        return Optional.of(copy(item));
    }

    @Override
    public boolean compareAndSet(String id, long expectedRevision, LoginAttempt next) {
        final boolean[] changed = {false};
        map.computeIfPresent(id, (key, old) -> {
            if (old.getRevision() == expectedRevision && old.getExpiresAt().isAfter(Instant.now())) {
                changed[0] = true;
                return copy(next);
            }
            return old;
        });
        return changed[0];
    }

    private LoginAttempt copy(LoginAttempt item) {
        return mapper.convertValue(item, LoginAttempt.class);
    }
}
