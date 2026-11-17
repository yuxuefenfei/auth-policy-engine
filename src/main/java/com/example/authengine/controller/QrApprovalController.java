package com.example.authengine.controller;

import com.example.authengine.attempt.AttemptService;
import com.example.authengine.common.ApiException;
import com.example.authengine.dto.request.ApproveQrRequest;
import com.example.authengine.dto.response.AttemptResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 移动端扫码反向授权控制器
 * <p>
 * 模拟移动 APP 扫码确认场景：
 * 接口要求持有合法登录态的主体 (通过 Basic Auth / Bearer Token 模拟 APP 安全令身份，或在请求体提供 approvedBy 用户名)
 * 向服务端提交扫描获取的 Ticket 密文进行反向授权。
 */
@RestController
@RequestMapping("/api/app/qr")
@RequiredArgsConstructor
public class QrApprovalController {

    private final AttemptService attemptService;

    /**
     * 移动客户端确认扫码并授权网页端登录
     *
     * @param request 包含扫码票据 Ticket 及可选操作用户的请求体
     * @param auth    当前已通过认证的 APP 登录主体 (由 Spring Security 注入，无认证时为 null 或 anonymous)
     * @return 授权完成后的 Attempt 会话状态
     */
    @PostMapping("/approve")
    public AttemptResponse approve(@Valid @RequestBody ApproveQrRequest request, @Nullable Authentication auth) {
        String username = null;
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            username = auth.getName();
        } else if (request.approvedBy() != null && !request.approvedBy().isBlank()) {
            username = request.approvedBy().trim();
        }

        if (username == null || username.isBlank()) {
            throw ApiException.unauthorized("移动端未完成身份认证 (请在请求头提供 Basic Auth，或在请求体中传递 approvedBy 登录用户名)");
        }

        return attemptService.approveQr(request.ticket(), username);
    }
}
