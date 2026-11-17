package com.example.authengine.factor;

import com.example.authengine.attempt.FactorStep;
import com.example.authengine.attempt.LoginAttempt;
import com.example.authengine.common.ApiException;
import com.example.authengine.common.SecureValues;
import com.example.authengine.domain.FactorType;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 移动 APP 扫码跨端授权因子处理器 (APP_QR_APPROVAL)
 * <p>
 * 遵循安全设计原则：
 * 网页端仅可获取带有 attemptId 与高熵随机 token 签名的单次有效二维码 Ticket；
 * 核验动作必须由已认证合法的独立 APP 通过专用通道提交，严禁网页端自称已完成扫码。
 */
@Component
public class QrFactorHandler implements FactorHandler {

    @Override
    public FactorType type() {
        return FactorType.APP_QR_APPROVAL;
    }

    /**
     * 生成带时效的动态扫码票据
     *
     * @param attempt 当前登录会话
     * @return 包含 Ticket 明文 (用于渲染二维码) 及密文 SHA-256 摘要的挑战凭据包 (90s TTL)
     */
    @Override
    public PreparedChallenge prepare(LoginAttempt attempt) {
        String ticket = attempt.getId() + "." + SecureValues.randomToken(32);
        return new PreparedChallenge(SecureValues.sha256(ticket), Instant.now().plusSeconds(90), null, ticket);
    }

    /**
     * 扫码认证不支持由网页端直接提交 verify
     */
    @Override
    public boolean verify(LoginAttempt attempt, FactorStep step, String credential) {
        throw ApiException.bad("移动端扫码认证必须由已登录的独立客户端扫码确认");
    }
}
