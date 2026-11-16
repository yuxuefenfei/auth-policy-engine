import { ref } from 'vue';
import type {
  AttemptView,
  StartResult,
  VerifyResult,
  SessionToken,
  PolicySnapshot,
  VersionInfo,
  PolicyNode,
  ApiLogEntry,
} from '../types/auth';

export const apiLogs = ref<ApiLogEntry[]>([]);

async function request<T>(
  method: string,
  url: string,
  body?: any,
  headers: Record<string, string> = {}
): Promise<T> {
  const time = new Date().toLocaleTimeString();
  const init: RequestInit = {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...headers,
    },
    credentials: 'include', // 重要：携带 AUTH_BIND Cookie
  };

  if (body !== undefined && body !== null) {
    init.body = JSON.stringify(body);
  }

  let res: Response;
  let data: any;

  try {
    res = await fetch(url, init);
    const text = await res.text();
    try {
      data = JSON.parse(text);
    } catch {
      data = { raw: text };
    }
  } catch (err: any) {
    apiLogs.value.unshift({
      time,
      method,
      url,
      status: 'NET_ERR',
      body: body ? JSON.stringify(body) : null,
      response: { error: err.message },
    });
    throw err;
  }

  apiLogs.value.unshift({
    time,
    method,
    url,
    status: res.status,
    body: body ? JSON.stringify(body) : null,
    response: data,
  });

  if (!res.ok) {
    const error = new Error(data?.error || `HTTP ${res.status}`);
    (error as any).status = res.status;
    (error as any).data = data;
    throw error;
  }

  return data as T;
}

// 认证 Attempt 流程
export function createAttempt(username: string): Promise<AttemptView> {
  return request<AttemptView>('POST', '/api/login/attempts', { username });
}

export function getAttempt(id: string): Promise<AttemptView> {
  return request<AttemptView>('GET', `/api/login/attempts/${id}`);
}

export function startFactor(id: string, nodeId: string): Promise<StartResult> {
  return request<StartResult>('POST', `/api/login/attempts/${id}/factors/${nodeId}/start`);
}

export function verifyFactor(id: string, nodeId: string, credential: string): Promise<VerifyResult> {
  return request<VerifyResult>('POST', `/api/login/attempts/${id}/factors/${nodeId}/verify`, { credential });
}

export function completeAttempt(id: string): Promise<SessionToken> {
  return request<SessionToken>('POST', `/api/login/attempts/${id}/complete`);
}

export function getMe(accessToken: string): Promise<{ username: string }> {
  return request<{ username: string }>('GET', '/api/me', null, {
    Authorization: `Bearer ${accessToken}`,
  });
}

// 管理员策略 API (Basic Auth)
export function getActivePolicy(code: string = 'WEB_LOGIN', auth: string = 'admin:Admin@123456'): Promise<PolicySnapshot> {
  return request<PolicySnapshot>('GET', `/api/admin/policies/${code}/active`, null, {
    Authorization: 'Basic ' + btoa(auth),
  });
}

export function getPolicyVersions(code: string = 'WEB_LOGIN', auth: string = 'admin:Admin@123456'): Promise<VersionInfo[]> {
  return request<VersionInfo[]>('GET', `/api/admin/policies/${code}/versions`, null, {
    Authorization: 'Basic ' + btoa(auth),
  });
}

export function savePolicyDraft(code: string, root: PolicyNode, auth: string = 'admin:Admin@123456'): Promise<VersionInfo> {
  return request<VersionInfo>('POST', `/api/admin/policies/${code}/draft`, root, {
    Authorization: 'Basic ' + btoa(auth),
  });
}

export function publishPolicy(code: string, version: number, auth: string = 'admin:Admin@123456'): Promise<VersionInfo> {
  return request<VersionInfo>('POST', `/api/admin/policies/${code}/publish/${version}`, null, {
    Authorization: 'Basic ' + btoa(auth),
  });
}

// APP 扫码确认 API (Basic Auth)
export function approveQr(ticket: string, username: string = 'alice', password: string = 'Demo@123456'): Promise<AttemptView> {
  return request<AttemptView>('POST', '/api/app/qr/approve', { ticket }, {
    Authorization: 'Basic ' + btoa(`${username}:${password}`),
  });
}

// 本地演示短信验证码读取
export function getLatestDemoSms(mobile?: string): Promise<{ enabled: boolean; code: string; mobile: string }> {
  const query = mobile ? `?mobile=${mobile}` : '';
  return request<{ enabled: boolean; code: string; mobile: string }>('GET', `/api/login/demo/latest-sms${query}`);
}
