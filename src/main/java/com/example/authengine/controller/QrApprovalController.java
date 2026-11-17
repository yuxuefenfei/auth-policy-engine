package com.example.authengine.controller;

import com.example.authengine.attempt.AttemptService;
import com.example.authengine.common.ApiException;
import com.example.authengine.dto.request.ApproveQrRequest;
import com.example.authengine.dto.response.AttemptResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * 移动端扫码反向授权控制器
 * <p>
 * 模拟移动 APP 扫码确认场景：
 * 接口支持多种调用规范与媒体格式 (application/json, text/plain, Query 参数)：
 * 1. 认证支持：Header Basic Auth / Bearer Token 优先，无认证时支持在请求体或参数中直接传入 approvedBy 用户名；
 * 2. 报文支持：支持标准 JSON 结构体 {"ticket": "...", "approvedBy": "... "}，也支持 Postman 纯文本直接粘贴 Ticket 密文。
 */
@Slf4j
@RestController
@RequestMapping("/api/app/qr")
@RequiredArgsConstructor
public class QrApprovalController {

    private final AttemptService attemptService;
    private final ObjectMapper objectMapper;

    /**
     * 移动客户端确认扫码并授权网页端登录
     *
     * @param rawBody    请求体原始字符串 (由 Spring 自动接收，支持 JSON 或纯文本)
     * @param ticket     Query/Form 传递的 ticket 备选参数
     * @param approvedBy Query/Form 传递的 approvedBy 备选参数
     * @param auth       当前已通过认证的 APP 登录主体 (由 Spring Security 注入)
     * @return 授权完成后的 Attempt 会话状态
     */
    @PostMapping(value = "/approve")
    public AttemptResponse approve(
            @RequestBody(required = false) String rawBody,
            @RequestParam(required = false) String ticket,
            @RequestParam(required = false) String approvedBy,
            @Nullable Authentication auth
    ) {
        String resolvedTicket = ticket;
        String resolvedUser = approvedBy;

        // 1. 若请求体存在内容，优先解析
        if (rawBody != null && !rawBody.isBlank()) {
            String trimmed = rawBody.trim();
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                try {
                    ApproveQrRequest req = objectMapper.readValue(trimmed, ApproveQrRequest.class);
                    if (req.ticket() != null && !req.ticket().isBlank()) {
                        resolvedTicket = req.ticket().trim();
                    }
                    if (req.approvedBy() != null && !req.approvedBy().isBlank()) {
                        resolvedUser = req.approvedBy().trim();
                    }
                } catch (Exception e) {
                    log.warn("无法解析请求体为 ApproveQrRequest: {}", e.getMessage());
                }
            } else if (resolvedTicket == null || resolvedTicket.isBlank()) {
                // 客户端直接作为纯文本 (Text) 发送了 Ticket 字符串
                if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() > 2) {
                    trimmed = trimmed.substring(1, trimmed.length() - 1);
                }
                resolvedTicket = trimmed;
            }
        }

        if (resolvedTicket == null || resolvedTicket.isBlank()) {
            throw ApiException.bad("请提供待授权的二维码票据 Ticket (支持 JSON 请求体、纯文本请求体或 Query 参数)");
        }

        // 2. 提取身份认证：Spring Security 认证上下文 (Basic / Bearer) 优先于请求体显式指定的 approvedBy
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            resolvedUser = auth.getName();
        }

        if (resolvedUser == null || resolvedUser.isBlank()) {
            throw ApiException.unauthorized("移动端未完成身份认证 (请在请求头提供 Basic Auth / Bearer Token，或在请求体中传递 approvedBy 登录用户名)");
        }

        return attemptService.approveQr(resolvedTicket, resolvedUser);
    }
}
