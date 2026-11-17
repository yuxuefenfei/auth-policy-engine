package com.example.authengine.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 提交因子核验凭据请求 DTO
 *
 * @param credential 认证凭据 (如密码明文/密文、6 位数字短信动态码等)
 */
public record VerifyFactorRequest(
        @NotBlank(message = "核验凭据不能为空")
        String credential
) {
}
