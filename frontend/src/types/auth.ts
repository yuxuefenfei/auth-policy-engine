import type { FactorType, PolicyOp, AttemptStatus, StepProgress } from '@/enums';

export * from '@/enums';

export interface PolicyNode {
  op: PolicyOp;
  id?: string;
  factor?: FactorType;
  required?: number;
  children?: PolicyNode[];
}

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
  tokenType: string;
  expiresIn: number;
}

export interface PolicySnapshot {
  code: string;
  version: number;
  root: PolicyNode;
}

export interface VersionInfo {
  version: number;
  status: string;
  checksum: string;
  publishedAt?: string | null;
}

export interface ApiLogEntry {
  time: string;
  method: string;
  url: string;
  status: number | string;
  body?: string | null;
  response: any;
}
