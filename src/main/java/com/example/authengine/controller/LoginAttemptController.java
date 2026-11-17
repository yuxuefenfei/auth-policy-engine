package com.example.authengine.controller;

import com.example.authengine.attempt.AttemptService;
import com.example.authengine.common.SecureValues;
import com.example.authengine.config.AuthProperties;
import com.example.authengine.dto.request.CreateAttemptRequest;
import com.example.authengine.dto.request.VerifyFactorRequest;
import com.example.authengine.dto.response.AttemptResponse;
import com.example.authengine.dto.response.StartFactorResponse;
import com.example.authengine.dto.response.VerifyFactorResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

/**
 * 登录认证流程控制器
 * <p>
 * 负责动态认证 Attempt 的创建、状态轮询、动态核验凭据下发（短信验证码/扫码票据）及凭据核验。
 * 遵循 HTTP 边界适配原则，保证无状态请求与浏览器绑定的原子性。
 */
@RestController
@RequestMapping("/api/login/attempts")
@RequiredArgsConstructor
public class LoginAttemptController {

    private static final String BIND_COOKIE = "AUTH_BIND";
    private final AttemptService attemptService;
    private final AuthProperties authProperties;

    /**
     * 发起并创建新的登录认证 Attempt
     * <p>
     * 若客户端未携带合法的 {@code AUTH_BIND} Cookie，则由服务端生成高熵安全随机串并通过 HttpOnly Cookie 绑定下发。
     *
     * @param request  包含待登录用户名的请求体
     * @param bind     从请求头 Cookie 中提取的 AUTH_BIND 凭据
     * @param response HTTP 响应对象，用于注入 Set-Cookie
     * @return 初始化的 Attempt 响应视图
     */
    @PostMapping
    public AttemptResponse create(@Valid @RequestBody CreateAttemptRequest request,
                                  @CookieValue(name = BIND_COOKIE, required = false) String bind,
                                  HttpServletResponse response) {
        if (bind == null || !bind.matches("[A-Za-z0-9_-]{43}")) {
            bind = SecureValues.randomToken(32);
            ResponseCookie cookie = ResponseCookie.from(BIND_COOKIE, bind)
                    .httpOnly(true)
                    .secure(authProperties.isCookieSecure())
                    .sameSite("Strict")
                    .path("/api/login")
                    .maxAge(Duration.ofHours(2))
                    .build();
            response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        }
        return attemptService.create(request.username(), bind);
    }

    /**
     * 获取指定认证 Attempt 的当前状态与可执行因子列表
     *
     * @param id   Attempt 全局唯一标识
     * @param bind 浏览器绑定 Cookie
     * @return 当前 Attempt 响应视图
     */
    @GetMapping("/{id}")
    public AttemptResponse status(@PathVariable String id,
                                  @CookieValue(name = BIND_COOKIE, required = false) String bind) {
        return attemptService.status(id, bind);
    }

    /**
     * 针对指定因子节点发起动态核验 (如发送短信验证码或生成动态二维码)
     *
     * @param id     Attempt 标识
     * @param nodeId 待执行的因子节点 ID
     * @param bind   浏览器绑定 Cookie
     * @return 动态核验凭据结果与更新后的 Attempt 视图
     */
    @PostMapping("/{id}/factors/{nodeId}/start")
    public StartFactorResponse start(@PathVariable String id,
                                     @PathVariable String nodeId,
                                     @CookieValue(name = BIND_COOKIE, required = false) String bind) {
        return attemptService.start(id, nodeId, bind);
    }

    /**
     * 提交指定因子的核验凭据进行比对
     *
     * @param id      Attempt 标识
     * @param nodeId  核验的因子节点 ID
     * @param bind    浏览器绑定 Cookie
     * @param request 包含凭据内容的请求体
     * @return 因子核验结果与最新 Attempt 状态
     */
    @PostMapping("/{id}/factors/{nodeId}/verify")
    public VerifyFactorResponse verify(@PathVariable String id,
                                       @PathVariable String nodeId,
                                       @CookieValue(name = BIND_COOKIE, required = false) String bind,
                                       @Valid @RequestBody VerifyFactorRequest request) {
        return attemptService.verify(id, nodeId, bind, request.credential());
    }
}
