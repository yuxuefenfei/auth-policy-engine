package com.example.authengine.api;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class MeController {
    @GetMapping("/api/me")
    public Map<String, String> me(Authentication auth) {
        return Map.of("username", auth.getName());
    }
}
