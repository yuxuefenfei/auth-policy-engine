package com.example.authengine.controller;

import com.example.authengine.dto.response.UserInfoResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户身份自省受保护控制器
 * <p>
 * 受 Bearer Token 过滤器保护，用于检验已签发访问令牌的有效性并获取当前用户信息。
 */
@RestController
public class MeController {

    /**
     * 获取当前通过 Bearer Token 认证的用户基本信息
     *
     * @param auth Spring Security 认证主体
     * @return 包含用户名的响应 DTO
     */
    @GetMapping("/api/me")
    public UserInfoResponse me(Authentication auth) {
        return new UserInfoResponse(auth.getName());
    }
}
