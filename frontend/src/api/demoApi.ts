import { request } from './httpClient';

/**
 * 本地演示环境短信监听 API 模块
 */

/**
 * 读取后端模拟网关打印至控制台的最新短信验证码
 *
 * @param mobile 接收手机号 (可选)
 */
export function getLatestConsoleSms(mobile?: string): Promise<{ enabled: boolean; code: string; mobile: string }> {
  const query = mobile ? `?mobile=${encodeURIComponent(mobile)}` : '';
  return request<{ enabled: boolean; code: string; mobile: string }>('GET', `/api/login/demo/latest-sms${query}`);
}

// 保持别名兼容
export const getLatestDemoSms = getLatestConsoleSms;

