package com.example.authengine.factor;

import com.example.authengine.attempt.FactorStep;
import com.example.authengine.attempt.LoginAttempt;
import com.example.authengine.common.ApiException;
import com.example.authengine.common.SecureValues;
import com.example.authengine.domain.FactorType;
import com.example.authengine.user.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class SmsFactorHandler implements FactorHandler {
    private final ObjectProvider<SmsGateway> gateway;
    private final AppUserRepository users;
    private final PasswordEncoder encoder;

    @Override
    public FactorType type() {
        return FactorType.SMS_OTP;
    }

    @Override
    public boolean available() {
        return gateway.getIfAvailable() != null;
    }

    @Override
    public PreparedChallenge prepare(LoginAttempt attempt) {
        if (!available()) throw ApiException.conflict("短信服务未配置");
        String otp = SecureValues.randomDigits(6);
        return new PreparedChallenge(encoder.encode(otp), Instant.now().plusSeconds(120), otp, null);
    }

    @Override
    public void deliver(LoginAttempt attempt, PreparedChallenge challenge) {
        // 不存在的账号也返回相同结构，避免通过发送接口直接探测账号存在性。
        users.findByUsername(attempt.getUsername())
                .filter(u -> u.isEnabled() && u.getId().equals(attempt.getSubjectUserId()))
                .filter(u -> u.getMobile() != null && !u.getMobile().isBlank())
                .ifPresent(u -> gateway.getObject().send(u.getMobile(), challenge.deliverySecret()));
    }

    @Override
    public boolean verify(LoginAttempt attempt, FactorStep step, String credential) {
        if (credential == null || !credential.matches("[0-9]{6}")) return false;
        if (step.getChallengeHash() == null || step.getChallengeExpiresAt() == null ||
                !step.getChallengeExpiresAt().isAfter(Instant.now())) return false;
        return attempt.getSubjectUserId() != null && encoder.matches(credential, step.getChallengeHash());
    }
}
