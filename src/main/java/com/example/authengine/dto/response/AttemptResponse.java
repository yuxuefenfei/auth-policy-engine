package com.example.authengine.dto.response;

import com.example.authengine.attempt.AttemptStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 登录认证流程状态与执行拓扑响应 DTO
 *
 * @param attemptId     认证流程全局唯一追踪 ID
 * @param policyCode    绑定的策略唯一代码
 * @param policyVersion 锁定的策略不可变快照版本号
 * @param status        状态机状态 (IN_PROGRESS, SUCCEEDED, FAILED, CONSUMED)
 * @param expiresAt     会话时效截至时间戳
 * @param available     当前开放可执行的因子选项列表
 * @param progress      各节点的执行进度映射 (nodeId -> VERIFIED/CHALLENGE_ISSUED/PENDING/EXHAUSTED)
 * @param canComplete   是否已达成所有必要策略条件，可调用 complete 换取令牌
 */
public record AttemptResponse(
        String attemptId,
        String policyCode,
        int policyVersion,
        AttemptStatus status,
        Instant expiresAt,
        List<FactorOptionResponse> available,
        Map<String, String> progress,
        boolean canComplete
) {
}
