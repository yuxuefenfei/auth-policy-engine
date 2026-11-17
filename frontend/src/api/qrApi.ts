import { request } from './httpClient';
import type { AttemptView } from '../types/auth';

/**
 * 移动客户端 APP 扫码确认授权 API 模块
 */

/**
 * 模拟移动端 APP 确认扫码并授权
 *
 * @param ticket 网页端二维码展示的一次性票据密文
 * @param username 移动端当前登录用户名 (Basic Auth)
 * @param password 移动端当前登录用户密码
 */
export function approveQrTicket(
  ticket: string,
  username: string = 'alice',
  password: string = 'Demo@123456'
): Promise<AttemptView> {
  const basicAuth = 'Basic ' + btoa(`${username}:${password}`);
  return request<AttemptView>('POST', '/api/app/qr/approve', { ticket }, {
    headers: { Authorization: basicAuth },
  });
}

// 保持别名兼容
export const approveQr = approveQrTicket;
