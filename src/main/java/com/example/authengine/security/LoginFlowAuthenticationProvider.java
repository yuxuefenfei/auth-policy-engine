package com.example.authengine.security;

import com.example.authengine.attempt.AttemptService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

/**
 * 所有因子通过后，原子消费一次性 Attempt，再生成 Spring Security Authentication。
 */
@Component
@RequiredArgsConstructor
public class LoginFlowAuthenticationProvider implements AuthenticationProvider {
    private final AttemptService attempts;
    private final UserDetailsService users;

    @Override
    public Authentication authenticate(Authentication authentication) {
        String username = attempts.consumeCompleted((String) authentication.getPrincipal(),
                (String) authentication.getCredentials());
        UserDetails principal = users.loadUserByUsername(username);
        if (!principal.isEnabled() || !principal.isAccountNonLocked() || !principal.isAccountNonExpired())
            throw new DisabledException("用户不可用");
        return UsernamePasswordAuthenticationToken.authenticated(principal, null,
                principal.getAuthorities());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return LoginFlowAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
