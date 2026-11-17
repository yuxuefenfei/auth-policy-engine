package com.example.authengine.dto.response;

/**
 * 当前登录用户信息响应 DTO
 *
 * @param username 当前授权通过的用户名
 */
public record UserInfoResponse(
        String username
) {
}
