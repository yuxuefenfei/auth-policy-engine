package com.example.authengine.controller;

import com.example.authengine.attempt.AttemptService;
import com.example.authengine.dto.request.ApproveQrRequest;
import com.example.authengine.dto.response.AttemptResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 移动端扫码反向授权控制器
 * <p>
 * 模拟移动 APP 扫码确认场景：
 * 接口要求持有合法登录态的主体 (通过 Basic Auth 模拟 APP 安全令身份)，向服务端提交扫描获取的 Ticket 密文进行反向授权。
 */
@RestController
@RequestMapping("/api/app/qr")
@RequiredArgsConstructor
public class QrApprovalController {

    private final AttemptService attemptService;

    /**
     * 移动客户端确认扫码并授权网页端登录
     *
     * @param request 包含扫码票据 Ticket 的请求体
     * @param auth    当前已通过认证的 APP 登录主体
     * @return 授权完成后的 Attempt 会话状态
     */
    @PostMapping("/approve")
    public AttemptResponse approve(@Valid @RequestBody ApproveQrRequest request, Authentication auth) {
        return attemptService.approveQr(request.ticket(), auth.getName());
    }
}
