import { request } from './httpClient';

/**
 * 用户自省与受保护接口 API 模块
 */

/**
 * 校验已签发的 Bearer Token 并获取当前用户信息
 *
 * @param token 访问令牌 (Opaque Token)
 */
export function getMe(token?: string): Promise<{ username: string }> {
  const headers: Record<string, string> = {};
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  return request<{ username: string }>('GET', '/api/me', undefined, { headers });
}
