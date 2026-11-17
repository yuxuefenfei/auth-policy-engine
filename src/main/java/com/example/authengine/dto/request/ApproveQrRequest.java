package com.example.authengine.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 移动端扫码确认授权请求 DTO
 *
 * @param ticket 网页端二维码展示的一次性防篡改票据凭据
 */
public record ApproveQrRequest(
        @NotBlank(message = "授权票据 Ticket 不能为空")
        String ticket
) {
}
