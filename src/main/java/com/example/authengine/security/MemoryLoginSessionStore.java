package com.example.authengine.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "app.auth.storage", havingValue = "memory", matchIfMissing = true)
public class MemoryLoginSessionStore implements LoginSessionStore {
    private final ConcurrentHashMap<String, Session> map = new ConcurrentHashMap<>();

    @Override
    public void save(String hash, String username, Duration ttl) {
        map.put(hash, new Session(username, Instant.now().plus(ttl)));
    }

    @Override
    public Optional<String> findUsername(String hash) {
        Session session = map.get(hash);
        if (session == null) return Optional.empty();
        if (!session.expiresAt().isAfter(Instant.now())) {
            map.remove(hash);
            return Optional.empty();
        }
        return Optional.of(session.username());
    }

    private record Session(String username, Instant expiresAt) {
    }
}
