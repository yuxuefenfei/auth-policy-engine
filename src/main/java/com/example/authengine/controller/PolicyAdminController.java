package com.example.authengine.controller;

import com.example.authengine.domain.PolicyNode;
import com.example.authengine.dto.response.PolicySnapshotResponse;
import com.example.authengine.dto.response.PolicyVersionResponse;
import com.example.authengine.policy.PolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 策略管理与热更编排控制器
 * <p>
 * 受 Spring Security 保护 (必须持有 ROLE_ADMIN 权限)。
 * 提供当前生效策略快照查看、版本历史回溯、新建草稿版本及在线热发布能力。
 */
@RestController
@RequestMapping("/api/admin/policies")
@RequiredArgsConstructor
public class PolicyAdminController {

    private final PolicyService policyService;

    /**
     * 获取指定策略当前已发布的生效版本快照
     *
     * @param code 策略唯一代码 (如 WEB_LOGIN)
     * @return 包含当前版本号与完整 AST 树的快照信息
     */
    @GetMapping("/{code}/active")
    public PolicySnapshotResponse active(@PathVariable String code) {
        return policyService.active(code);
    }

    /**
     * 查询指定策略的所有版本历史列表
     *
     * @param code 策略代码
     * @return 按版本号倒序排列的历史版本列表
     */
    @GetMapping("/{code}/versions")
    public List<PolicyVersionResponse> versions(@PathVariable String code) {
        return policyService.listVersions(code);
    }

    /**
     * 保存新的策略定义草稿版本
     *
     * @param code 策略代码
     * @param root 新策略的 AST 根节点
     * @return 新建的草稿版本信息
     */
    @PostMapping("/{code}/draft")
    public PolicyVersionResponse draft(@PathVariable String code, @RequestBody PolicyNode root) {
        return policyService.saveDraft(code, root);
    }

    /**
     * 将指定版本号的策略正式发布生效
     *
     * @param code    策略代码
     * @param version 待发布的版本号
     * @return 发布生效后的版本信息
     */
    @PostMapping("/{code}/publish/{version}")
    public PolicyVersionResponse publish(@PathVariable String code, @PathVariable int version) {
        return policyService.publish(code, version);
    }
}
