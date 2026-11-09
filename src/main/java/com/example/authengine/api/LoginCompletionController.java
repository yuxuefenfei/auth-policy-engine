package com.example.authengine.api;

import com.example.authengine.security.LoginFlowAuthenticationToken;
import com.example.authengine.security.SessionTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.web.bind.annotation.*;

/**
 * 完成整个策略后才允许由 Provider 生成 Authentication / 会话令牌。
 */
@RestController
@RequestMapping("/api/login/attempts")
@RequiredArgsConstructor
public class LoginCompletionController {
    private final AuthenticationManager authenticationManager;
    private final SessionTokenService sessions;

    @PostMapping("/{id}/complete")
    public SessionTokenService.SessionToken complete(@PathVariable String id,
                                                     @CookieValue(name = "AUTH_BIND", required = false) String binding) {
        var auth = authenticationManager.authenticate(new LoginFlowAuthenticationToken(id, binding));
        return sessions.issue(auth.getName());
    }
}
