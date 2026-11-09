package com.example.authengine.api;

import com.example.authengine.domain.PolicyNode;
import com.example.authengine.policy.PolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 所有 /api/admin/** 接口由 SecurityConfig 保护，必须 ROLE_ADMIN。
 */
@RestController
@RequestMapping("/api/admin/policies")
@RequiredArgsConstructor
public class PolicyAdminController {
    private final PolicyService service;

    @GetMapping("/{code}/active")
    public PolicyService.PolicySnapshot active(@PathVariable String code) {
        return service.active(code);
    }

    @GetMapping("/{code}/versions")
    public List<PolicyService.VersionInfo> versions(@PathVariable String code) {
        return service.listVersions(code);
    }

    @PostMapping("/{code}/draft")
    public PolicyService.VersionInfo draft(@PathVariable String code, @RequestBody PolicyNode root) {
        return service.saveDraft(code, root);
    }

    @PostMapping("/{code}/publish/{version}")
    public PolicyService.VersionInfo publish(@PathVariable String code, @PathVariable int version) {
        return service.publish(code, version);
    }
}
