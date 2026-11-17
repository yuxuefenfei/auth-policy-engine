package com.example.authengine.controller;

import com.example.authengine.dto.response.DemoSmsResponse;
import com.example.authengine.factor.ConsoleDemoSmsGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 本地演示环境短信日志辅助控制器
 * <p>
 * 仅在 {@code app.auth.demo-sms-log-enabled: true} 时激活，
 * 供前端验证工作台自动捕获模拟短信网关打印至控制台的验证码。
 */
@RestController
@RequestMapping("/api/login/demo")
@ConditionalOnProperty(name = "app.auth.demo-sms-log-enabled", havingValue = "true")
public class DemoController {

    /**
     * 获取指定手机号最新拦截到的短信验证码
     *
     * @param mobile 接收手机号 (可选)
     * @return 包含最新验证码的演示响应 DTO
     */
    @GetMapping("/latest-sms")
    public DemoSmsResponse latestSms(@RequestParam(required = false) String mobile) {
        String code = ConsoleDemoSmsGateway.getLatestCode(mobile);
        return new DemoSmsResponse(true, code != null ? code : "", mobile != null ? mobile : "");
    }
}
