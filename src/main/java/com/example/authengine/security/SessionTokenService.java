package com.example.authengine.security;

import com.example.authengine.common.SecureValues;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SessionTokenService {
    private static final Duration TTL = Duration.ofMinutes(30);
    private final LoginSessionStore store;

    public SessionToken issue(String username) {
        String rawToken = SecureValues.randomToken(32);
        store.save(SecureValues.sha256(rawToken), username, TTL);
        return new SessionToken(rawToken, "Bearer", TTL.toSeconds());
    }

    public Optional<String> authenticate(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) return Optional.empty();
        return store.findUsername(SecureValues.sha256(token));
    }

    public record SessionToken(String accessToken, String tokenType, long expiresIn) {
    }
}
