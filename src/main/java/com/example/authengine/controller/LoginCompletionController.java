package com.example.authengine.controller;

import com.example.authengine.dto.response.SessionTokenResponse;
import com.example.authengine.security.LoginFlowAuthenticationToken;
import com.example.authengine.security.SessionTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.web.bind.annotation.*;

/**
 * 登录认证流程终结与令牌签发控制器
 * <p>
 * 当策略 AST 树所有必要条件均已达成 (SATISFIED) 时，由客户端调用该接口。
 * 委托 Spring Security {@link AuthenticationManager} 执行原子核销并签发高权限访问令牌。
 */
@RestController
@RequestMapping("/api/login/attempts")
@RequiredArgsConstructor
public class LoginCompletionController {

    private final AuthenticationManager authenticationManager;
    private final SessionTokenService sessionTokenService;

    /**
     * 原子核销已完成的 Attempt 并签发访问会话令牌
     *
     * @param id      Attempt 会话 ID
     * @param binding 浏览器绑定 AUTH_BIND Cookie
     * @return 签发的 Bearer 访问令牌响应 DTO
     */
    @PostMapping("/{id}/complete")
    public SessionTokenResponse complete(@PathVariable String id,
                                         @CookieValue(name = "AUTH_BIND", required = false) String binding) {
        var auth = authenticationManager.authenticate(new LoginFlowAuthenticationToken(id, binding));
        return sessionTokenService.issue(auth.getName());
    }
}
