package com.example.authengine.api;

import com.example.authengine.attempt.AttemptService;
import com.example.authengine.common.SecureValues;
import com.example.authengine.config.AuthProperties;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/login/attempts")
@RequiredArgsConstructor
public class LoginAttemptController {
    private static final String BIND_COOKIE = "AUTH_BIND";
    private final AttemptService service;
    private final AuthProperties config;

    @PostMapping
    public AttemptService.AttemptView create(@Valid @RequestBody CreateRequest request,
                                             @CookieValue(name = BIND_COOKIE, required = false) String bind,
                                             HttpServletResponse response) {
        if (bind == null || !bind.matches("[A-Za-z0-9_-]{43}")) {
            bind = SecureValues.randomToken(32);
            ResponseCookie cookie = ResponseCookie.from(BIND_COOKIE, bind)
                    .httpOnly(true).secure(config.isCookieSecure()).sameSite("Strict")
                    .path("/api/login").maxAge(Duration.ofHours(2)).build();
            response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        }
        return service.create(request.username(), bind);
    }

    @GetMapping("/{id}")
    public AttemptService.AttemptView status(@PathVariable String id,
                                             @CookieValue(name = BIND_COOKIE, required = false) String bind) {
        return service.status(id, bind);
    }

    @PostMapping("/{id}/factors/{nodeId}/start")
    public AttemptService.StartResult start(@PathVariable String id, @PathVariable String nodeId,
                                            @CookieValue(name = BIND_COOKIE, required = false) String bind) {
        return service.start(id, nodeId, bind);
    }

    @PostMapping("/{id}/factors/{nodeId}/verify")
    public AttemptService.VerifyResult verify(@PathVariable String id, @PathVariable String nodeId,
                                              @CookieValue(name = BIND_COOKIE, required = false) String bind,
                                              @Valid @RequestBody VerifyRequest request) {
        return service.verify(id, nodeId, bind, request.credential());
    }

    public record CreateRequest(@NotBlank @Size(max = 100) String username) {
    }

    public record VerifyRequest(@NotBlank String credential) {
    }
}
