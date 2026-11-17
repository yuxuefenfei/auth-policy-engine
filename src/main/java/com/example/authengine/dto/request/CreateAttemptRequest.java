package com.example.authengine.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 发起动态认证流程请求 DTO
 *
 * @param username 待登录用户标识 (如 alice, admin)
 */
public record CreateAttemptRequest(
        @NotBlank(message = "登录用户名不能为空")
        @Size(max = 100, message = "用户名长度不能超过 100 个字符")
        String username
) {
}
