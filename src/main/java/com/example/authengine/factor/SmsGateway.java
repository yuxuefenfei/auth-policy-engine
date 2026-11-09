package com.example.authengine.factor;

public interface SmsGateway {
    /**
     * 集成短信供应商：生产实现不得输出明文验证码到日志。
     */
    void send(String mobile, String code);
}
