package com.example.authengine.factor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 仅用于本地演示，生产禁止启用此 Bean。
 */
@Component
@ConditionalOnProperty(name = "app.auth.demo-sms-log-enabled", havingValue = "true")
public class ConsoleDemoSmsGateway implements SmsGateway {
    private static final Logger LOG = LoggerFactory.getLogger(ConsoleDemoSmsGateway.class);
    private static final Map<String, String> LATEST_CODES = new ConcurrentHashMap<>();
    private static volatile String lastAnyCode = null;

    @Override
    public void send(String mobile, String code) {
        lastAnyCode = code;
        if (mobile != null) {
            LATEST_CODES.put(mobile, code);
        }
        LOG.warn("[仅本地演示 / NEVER IN PRODUCTION] SMS mobile={} code={}", mobile, code);
    }

    public static String getLatestCode(String mobile) {
        if (mobile != null && LATEST_CODES.containsKey(mobile)) {
            return LATEST_CODES.get(mobile);
        }
        return lastAnyCode;
    }
}
