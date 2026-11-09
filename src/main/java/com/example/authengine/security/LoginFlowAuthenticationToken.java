package com.example.authengine.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;

/**
 * 只能由 LoginFlowAuthenticationProvider 消费，不接收客户端伪造的 userId/roles。
 */
public class LoginFlowAuthenticationToken extends AbstractAuthenticationToken {
    private final String attemptId;
    private final String browserBinding;

    public LoginFlowAuthenticationToken(String attemptId, String browserBinding) {
        super(null);
        this.attemptId = attemptId;
        this.browserBinding = browserBinding;
        setAuthenticated(false);
    }

    @Override
    public Object getPrincipal() {
        return attemptId;
    }

    @Override
    public Object getCredentials() {
        return browserBinding;
    }
}
