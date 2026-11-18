import { ref } from 'vue';
import type { ApiLogEntry } from '../types/auth';

export class ApiError extends Error {
  status: number;
  data: any;

  constructor(message: string, status: number, data: any) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.data = data;
  }
}

/** 响应式 API 审计请求日志记录池 */
export const apiLogs = ref<ApiLogEntry[]>([]);

/**
 * 统一网络请求核心客户端
 * <p>
 * 1. 默认携带 credentials: 'include'，确保跨请求透传 AUTH_BIND Cookie；
 * 2. 自动格式化请求报文并同步至 apiLogs，同时广播 window 事件；
 * 3. 统一提取错误并包装为标准 ApiError。
 */
export async function request<T>(
  method: string,
  path: string,
  body?: any,
  options: RequestInit = {}
): Promise<T> {
  const headers = new Headers(options.headers || {});
  let bodyPayload: BodyInit | undefined = undefined;

  if (body !== undefined && body !== null) {
    if (typeof body === 'object' && !(body instanceof FormData)) {
      headers.set('Content-Type', 'application/json');
      bodyPayload = JSON.stringify(body);
    } else {
      bodyPayload = body;
    }
  }

  const logEntry: Partial<ApiLogEntry> = {
    time: new Date().toLocaleTimeString(),
    method,
    url: path,
    body: bodyPayload ? String(bodyPayload) : null,
  };

  let res: Response;
  let data: any = null;

  try {
    res = await fetch(path, {
      method,
      headers,
      body: bodyPayload,
      credentials: 'include',
      ...options,
    });

    logEntry.status = res.status;
    const contentType = res.headers.get('content-type') || '';

    if (contentType.includes('application/json')) {
      data = await res.json();
    } else {
      data = await res.text();
    }

    logEntry.response = data;
    apiLogs.value.unshift(logEntry as ApiLogEntry);
    window.dispatchEvent(new CustomEvent('api-log', { detail: logEntry }));
  } catch (err: any) {
    logEntry.status = 'ERROR';
    logEntry.response = err.message || '网络连接异常';
    apiLogs.value.unshift(logEntry as ApiLogEntry);
    window.dispatchEvent(new CustomEvent('api-log', { detail: logEntry }));
    throw new ApiError(err.message || '网络连接异常', 0, null);
  }

  if (!res.ok) {
    const errorMsg =
      typeof data === 'object' && data?.error
        ? data.error
        : `HTTP ${res.status} 请求失败`;
    throw new ApiError(errorMsg, res.status, data);
  }

  return data as T;
}
