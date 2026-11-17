import { request } from './httpClient';
import type { PolicyNode, PolicySnapshot, VersionInfo } from '../types/auth';

/**
 * 策略管理与热更编排 API 模块 (Basic Auth 保护)
 */
const ADMIN_BASIC_AUTH = 'Basic ' + btoa('admin:Admin@123456');

/**
 * 查询指定策略当前生效的快照定义
 *
 * @param code 策略代码 (如 WEB_LOGIN)
 */
export function getActivePolicy(code: string = 'WEB_LOGIN'): Promise<PolicySnapshot> {
  return request<PolicySnapshot>('GET', `/api/admin/policies/${code}/active`, undefined, {
    headers: { Authorization: ADMIN_BASIC_AUTH },
  });
}

/**
 * 查询策略的所有历史版本快照列表
 *
 * @param code 策略代码
 */
export function listPolicyVersions(code: string = 'WEB_LOGIN'): Promise<VersionInfo[]> {
  return request<VersionInfo[]>('GET', `/api/admin/policies/${code}/versions`, undefined, {
    headers: { Authorization: ADMIN_BASIC_AUTH },
  });
}

/**
 * 保存新的策略 AST 定义为草稿版本
 *
 * @param code 策略代码
 * @param root 策略 AST 根节点
 */
export function saveDraftPolicy(code: string, root: PolicyNode): Promise<VersionInfo> {
  return request<VersionInfo>('POST', `/api/admin/policies/${code}/draft`, root, {
    headers: { Authorization: ADMIN_BASIC_AUTH },
  });
}

/**
 * 正式发布指定版本，使其成为当前全局生效策略
 *
 * @param code 策略代码
 * @param version 待发布版本号
 */
export function publishPolicy(code: string, version: number): Promise<VersionInfo> {
  return request<VersionInfo>('POST', `/api/admin/policies/${code}/publish/${version}`, undefined, {
    headers: { Authorization: ADMIN_BASIC_AUTH },
  });
}

// 保持别名兼容
export const getPolicyVersions = listPolicyVersions;
export const savePolicyDraft = saveDraftPolicy;

