package com.example.authengine.api;

import com.example.authengine.attempt.AttemptService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前 Basic Auth 模拟“已登录 APP”，生产必须替换成 APP 独立的安全认证。
 */
@RestController
@RequestMapping("/api/app/qr")
@RequiredArgsConstructor
public class QrApprovalController {
    private final AttemptService attempts;

    @PostMapping("/approve")
    public AttemptService.AttemptView approve(@Valid @RequestBody ApproveRequest req, Authentication auth) {
        return attempts.approveQr(req.ticket(), auth.getName());
    }

    public record ApproveRequest(@NotBlank String ticket) {
    }
}
