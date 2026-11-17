import { request } from './httpClient';
import type { AttemptView, StartResult, VerifyResult, SessionToken } from '../types/auth';

/**
 * 登录认证流程 API 模块
 */

/**
 * 发起登录认证流程，创建 Attempt 会话
 *
 * @param username 待登录用户名
 */
export function createAttempt(username: string): Promise<AttemptView> {
  return request<AttemptView>('POST', '/api/login/attempts', { username });
}

/**
 * 轮询/查询指定 Attempt 的最新执行拓扑与状态
 *
 * @param id Attempt 会话 ID
 */
export function getAttemptStatus(id: string): Promise<AttemptView> {
  return request<AttemptView>('GET', `/api/login/attempts/${id}`);
}

/**
 * 向指定因子节点发起动态验证流程 (如发送短信验证码或生成动态二维码)
 *
 * @param id Attempt 标识
 * @param nodeId 待发起的节点 ID
 */
export function startFactor(id: string, nodeId: string): Promise<StartResult> {
  return request<StartResult>('POST', `/api/login/attempts/${id}/factors/${nodeId}/start`);
}

/**
 * 提交因子核验凭据
 *
 * @param id Attempt 标识
 * @param nodeId 核验节点 ID
 * @param credential 凭据字符串
 */
export function verifyFactor(id: string, nodeId: string, credential: string): Promise<VerifyResult> {
  return request<VerifyResult>('POST', `/api/login/attempts/${id}/factors/${nodeId}/verify`, { credential });
}

/**
 * 原子核销已通过所有条件的 Attempt 并换取 Bearer 访问令牌
 *
 * @param id Attempt 会话 ID
 */
export function completeLogin(id: string): Promise<SessionToken> {
  return request<SessionToken>('POST', `/api/login/attempts/${id}/complete`);
}

// 保持别名兼容
export const getAttempt = getAttemptStatus;
export const completeAttempt = completeLogin;
