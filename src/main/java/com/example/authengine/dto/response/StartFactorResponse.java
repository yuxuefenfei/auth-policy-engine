package com.example.authengine.dto.response;

import java.time.Instant;

/**
 * 发起因子动态核验 (如生成动态登录二维码或发送短信验证码) 响应 DTO
 *
 * @param attempt            更新后的 Attempt 会话状态视图
 * @param challengeExpiresAt 动态核验凭据有效期截止时间
 * @param qrTicket           展示用一次性防篡改票据 (二维码场景提供，短信场景为 null)
 */
public record StartFactorResponse(
        AttemptResponse attempt,
        Instant challengeExpiresAt,
        String qrTicket
) {
}
