export type FactorType = 'PASSWORD' | 'SMS_OTP' | 'APP_QR_APPROVAL' | 'PASSKEY';

export type PolicyOp = 'FACTOR' | 'ALL' | 'ANY' | 'AT_LEAST' | 'SEQUENCE';

export interface PolicyNode {
  op: PolicyOp;
  id?: string;
  factor?: FactorType;
  required?: number;
  children?: PolicyNode[];
}

export type AttemptStatus = 'IN_PROGRESS' | 'SUCCEEDED' | 'FAILED' | 'CONSUMED';

export type StepProgress = 'PENDING' | 'CHALLENGE_ISSUED' | 'VERIFIED' | 'EXHAUSTED';

export interface FactorOption {
  nodeId: string;
  type: FactorType;
}

export interface AttemptView {
  attemptId: string;
  policyCode: string;
  policyVersion: number;
  status: AttemptStatus;
  expiresAt: string;
  available: FactorOption[];
  progress: Record<string, StepProgress>;
  canComplete: boolean;
}

export interface StartResult {
  attempt: AttemptView;
  challengeExpiresAt: string;
  qrTicket?: string;
}

export interface VerifyResult {
  attempt: AttemptView;
  verified: boolean;
}

export interface SessionToken {
  accessToken: string;
  expiresAt: string;
  tokenType: string;
}

export interface PolicySnapshot {
  code: string;
  version: number;
  root: PolicyNode;
}

export interface VersionInfo {
  version: number;
  active: boolean;
  root: PolicyNode;
  createdAt: string;
}

export interface ApiLogEntry {
  time: string;
  method: string;
  url: string;
  status: number | string;
  body?: string | null;
  response: any;
}
