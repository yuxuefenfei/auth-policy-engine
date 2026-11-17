/**
 * 因子节点执行进度有限状态枚举 (StepProgress)
 */
export enum StepProgress {
  /** 等待前置：依赖前序条件或未到执行顺序 */
  PENDING = 'PENDING',
  /** 可验证：当前拓扑分支已开放，用户可执行 */
  READY = 'READY',
  /** 待核验：动态验证凭据已下发 (短信已发/二维码已展)，等待输入或扫码确认 */
  CHALLENGE_ISSUED = 'CHALLENGE_ISSUED',
  /** 已验证：本因子凭据比对通过 */
  VERIFIED = 'VERIFIED',
  /** 已锁定：失败重试次数超限 */
  EXHAUSTED = 'EXHAUSTED',
}

export interface StepProgressMeta {
  code: StepProgress;
  label: string;
  badgeClass: string;
  description: string;
}

export const STEP_PROGRESS_META: Record<StepProgress, StepProgressMeta> = {
  [StepProgress.PENDING]: {
    code: StepProgress.PENDING,
    label: '等待前置 PENDING',
    badgeClass: 'bg-slate-800 text-slate-400 border-slate-700',
    description: '未满足前置依赖，暂不可执行',
  },
  [StepProgress.READY]: {
    code: StepProgress.READY,
    label: '待验证 READY',
    badgeClass: 'bg-indigo-500/20 text-indigo-300 border-indigo-500/40 ring-1 ring-indigo-500/30',
    description: '前置条件满足，等待用户发起',
  },
  [StepProgress.CHALLENGE_ISSUED]: {
    code: StepProgress.CHALLENGE_ISSUED,
    label: '待核验 (凭据已发)',
    badgeClass: 'bg-amber-500/20 text-amber-400 border-amber-500/30 animate-pulse',
    description: '动态验证凭据已下发，等待提交或扫码确认',
  },
  [StepProgress.VERIFIED]: {
    code: StepProgress.VERIFIED,
    label: '已验证 VERIFIED',
    badgeClass: 'bg-emerald-500/20 text-emerald-400 border-emerald-500/30 ring-1 ring-emerald-500/20',
    description: '凭据核验通过，条件已达成',
  },
  [StepProgress.EXHAUSTED]: {
    code: StepProgress.EXHAUSTED,
    label: '次数超限 EXHAUSTED',
    badgeClass: 'bg-rose-500/20 text-rose-400 border-rose-500/30',
    description: '错误尝试达到上限，本节点锁定',
  },
};
