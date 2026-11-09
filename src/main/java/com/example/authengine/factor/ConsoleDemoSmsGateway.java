package com.example.authengine.factor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 仅用于本地演示，生产禁止启用此 Bean。
 */
@Component
@ConditionalOnProperty(name = "app.auth.demo-sms-log-enabled", havingValue = "true")
public class ConsoleDemoSmsGateway implements SmsGateway {
    private static final Logger LOG = LoggerFactory.getLogger(ConsoleDemoSmsGateway.class);

    @Override
    public void send(String mobile, String code) {
        LOG.warn("[仅本地演示 / NEVER IN PRODUCTION] SMS mobile={} code={}", mobile, code);
    }
}
