package com.example.authengine.dto.response;

import com.example.authengine.domain.FactorType;

/**
 * 当前开放可执行的因子选项响应 DTO
 *
 * @param nodeId 策略语法树中的因子节点唯一 ID
 * @param type   认证因子枚举类型 (PASSWORD, SMS_OTP, APP_QR_APPROVAL, PASSKEY)
 */
public record FactorOptionResponse(
        String nodeId,
        FactorType type
) {
}
