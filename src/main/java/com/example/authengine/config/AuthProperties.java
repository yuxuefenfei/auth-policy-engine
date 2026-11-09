package com.example.authengine.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {
    private String policyCode = "WEB_LOGIN";
    private long attemptTtlSeconds = 300;
    private int maxFactorFailures = 5;
    private int maxChallengeSends = 3;
    private int challengeCooldownSeconds = 30;
    private boolean cookieSecure = false;
    private String storage = "memory";
    private boolean demoSmsLogEnabled = true;
    private boolean seedDemoUsers = true;
}
