package com.example.authengine.api;

import com.example.authengine.factor.ConsoleDemoSmsGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 本地演示辅助接口：在开启 demo-sms-log-enabled 时，为前端验证台提供最近控制台验证码读取。
 */
@RestController
@RequestMapping("/api/login/demo")
@ConditionalOnProperty(name = "app.auth.demo-sms-log-enabled", havingValue = "true")
public class DemoController {

    @GetMapping("/latest-sms")
    public Map<String, Object> latestSms(@RequestParam(required = false) String mobile) {
        String code = ConsoleDemoSmsGateway.getLatestCode(mobile);
        Map<String, Object> res = new HashMap<>();
        res.put("enabled", true);
        res.put("code", code != null ? code : "");
        res.put("mobile", mobile != null ? mobile : "");
        return res;
    }
}
