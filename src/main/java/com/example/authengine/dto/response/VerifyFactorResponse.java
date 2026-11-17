package com.example.authengine.dto.response;

/**
 * 提交因子核验结果响应 DTO
 *
 * @param attempt  更新后的 Attempt 会话状态视图
 * @param verified 本次因子核验是否成功通过
 */
public record VerifyFactorResponse(
        AttemptResponse attempt,
        boolean verified
) {
}
