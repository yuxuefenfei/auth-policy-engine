package com.example.authengine.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * /api/app /api/admin 只接受其独立 Basic Auth，不用网页登录令牌模拟移动端身份。
 */
@RequiredArgsConstructor
public class BearerSessionFilter extends OncePerRequestFilter {
    private final SessionTokenService sessions;
    private final UserDetailsService users;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp,
                                    FilterChain chain) throws ServletException, IOException {
        String path = req.getServletPath();
        if (!path.startsWith("/api/admin/") && !path.startsWith("/api/app/")) {
            String authorization = req.getHeader("Authorization");
            if (authorization != null && authorization.startsWith("Bearer ")) {
                sessions.authenticate(authorization.substring(7)).ifPresent(username -> {
                    try {
                        var user = users.loadUserByUsername(username);
                        if (user.isEnabled() && user.isAccountNonLocked()) {
                            var auth = UsernamePasswordAuthenticationToken.authenticated(
                                    user, null, user.getAuthorities());
                            var context = SecurityContextHolder.createEmptyContext();
                            context.setAuthentication(auth);
                            SecurityContextHolder.setContext(context);
                        }
                    } catch (Exception ignored) {
                        // 会话所属用户已删除/禁用时拒绝认证，不信任历史角色快照。
                    }
                });
            }
        }
        chain.doFilter(req, resp);
    }
}
