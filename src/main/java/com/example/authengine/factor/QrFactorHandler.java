package com.example.authengine.factor;

import com.example.authengine.attempt.FactorStep;
import com.example.authengine.attempt.LoginAttempt;
import com.example.authengine.common.ApiException;
import com.example.authengine.common.SecureValues;
import com.example.authengine.domain.FactorType;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 二维码通过已认证的 APP /api/app/qr/approve 接口完成，不允许网页自称扫码成功。
 */
@Component
public class QrFactorHandler implements FactorHandler {
    @Override
    public FactorType type() {
        return FactorType.APP_QR_APPROVAL;
    }

    @Override
    public PreparedChallenge prepare(LoginAttempt attempt) {
        String ticket = attempt.getId() + "." + SecureValues.randomToken(32);
        return new PreparedChallenge(SecureValues.sha256(ticket), Instant.now().plusSeconds(90), null, ticket);
    }

    @Override
    public boolean verify(LoginAttempt attempt, FactorStep step, String credential) {
        throw ApiException.bad("扫码确认必须由已登录 APP 发起");
    }
}
