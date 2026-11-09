package com.example.authengine.factor;

import com.example.authengine.attempt.FactorStep;
import com.example.authengine.attempt.LoginAttempt;
import com.example.authengine.domain.FactorType;
import com.example.authengine.user.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PasswordFactorHandler implements FactorHandler {
    private final AppUserRepository users;
    private final PasswordEncoder encoder;

    @Override
    public FactorType type() {
        return FactorType.PASSWORD;
    }

    @Override
    public boolean verify(LoginAttempt attempt, FactorStep step, String credential) {
        if (credential == null || credential.length() > 1024) return false;
        return users.findByUsername(attempt.getUsername())
                .filter(u -> u.isEnabled() && u.getId().equals(attempt.getSubjectUserId()))
                .map(u -> encoder.matches(credential, u.getPasswordHash())).orElse(false);
    }
}
