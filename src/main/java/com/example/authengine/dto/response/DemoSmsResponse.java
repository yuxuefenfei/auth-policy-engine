package com.example.authengine.dto.response;

/**
 * 本地演示环境短信日志响应 DTO
 *
 * @param enabled 是否开启了控制台短信日志监听
 * @param code    捕获到的最新 6 位短信动态口令
 * @param mobile  接收目标手机号
 */
public record DemoSmsResponse(
        boolean enabled,
        String code,
        String mobile
) {
}
