package com.example.authengine.factor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 仅用于本地演示，生产禁止启用此 Bean。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.auth.demo-sms-log-enabled", havingValue = "true")
public class ConsoleDemoSmsGateway implements SmsGateway {
    private static final Map<String, String> LATEST_CODES = new ConcurrentHashMap<>();
    private static volatile String lastAnyCode = null;

    @Override
    public void send(String mobile, String code) {
        lastAnyCode = code;
        if (mobile != null) {
            LATEST_CODES.put(mobile, code);
        }
        log.warn("[仅本地演示 / NEVER IN PRODUCTION] SMS mobile={} code={}", mobile, code);
    }

    public static String getLatestCode(String mobile) {
        if (mobile != null && LATEST_CODES.containsKey(mobile)) {
            return LATEST_CODES.get(mobile);
        }
        return lastAnyCode;
    }
}
