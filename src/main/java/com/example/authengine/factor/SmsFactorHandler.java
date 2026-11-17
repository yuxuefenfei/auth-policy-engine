package com.example.authengine.factor;

import com.example.authengine.attempt.FactorStep;
import com.example.authengine.attempt.LoginAttempt;
import com.example.authengine.common.ApiException;
import com.example.authengine.common.SecureValues;
import com.example.authengine.domain.FactorType;
import com.example.authengine.mapper.AppUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 手机短信动态验证码因子处理器 (SMS_OTP)
 * <p>
 * 负责生成 6 位高随机度动态口令、计算单向校验哈希、驱动短信网关下发以及验证码的时效比对。
 */
@Component
@RequiredArgsConstructor
public class SmsFactorHandler implements FactorHandler {

    private final ObjectProvider<SmsGateway> smsGatewayProvider;
    private final AppUserMapper appUserMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public FactorType type() {
        return FactorType.SMS_OTP;
    }

    @Override
    public boolean available() {
        return smsGatewayProvider.getIfAvailable() != null;
    }

    /**
     * 生成一次性 6 位短信动态验证码凭据
     *
     * @param attempt 当前登录会话
     * @return 包含密文哈希及 120s TTL 的动态验证凭据包
     */
    @Override
    public PreparedChallenge prepare(LoginAttempt attempt) {
        if (!available()) {
            throw ApiException.conflict("短信网关服务未就绪");
        }
        String otp = SecureValues.randomDigits(6);
        return new PreparedChallenge(passwordEncoder.encode(otp), Instant.now().plusSeconds(120), otp, null);
    }

    /**
     * 调用网关投递短信验证码
     * <p>
     * 安全设计：即使用户不存在也返回相同动作结构，彻底防御基于发送接口的账号枚举嗅探。
     *
     * @param attempt   当前登录流程
     * @param challenge 已就绪的动态验证凭据
     */
    @Override
    public void deliver(LoginAttempt attempt, PreparedChallenge challenge) {
        appUserMapper.findByUsername(attempt.getUsername())
                .filter(u -> u.isEnabled() && u.getId().equals(attempt.getSubjectUserId()))
                .filter(u -> u.getMobile() != null && !u.getMobile().isBlank())
                .ifPresent(u -> smsGatewayProvider.getObject().send(u.getMobile(), challenge.deliverySecret()));
    }

    /**
     * 核验用户提交的 6 位数字验证码
     *
     * @param attempt    当前登录会话
     * @param step       当前因子节点
     * @param credential 用户提交的 6 位验证码
     * @return 验证通过且在时效内返回 true
     */
    @Override
    public boolean verify(LoginAttempt attempt, FactorStep step, String credential) {
        if (credential == null || !credential.matches("[0-9]{6}")) {
            return false;
        }
        if (step.getChallengeHash() == null || step.getChallengeExpiresAt() == null ||
                !step.getChallengeExpiresAt().isAfter(Instant.now())) {
            return false;
        }
        return attempt.getSubjectUserId() != null && passwordEncoder.matches(credential, step.getChallengeHash());
    }
}
