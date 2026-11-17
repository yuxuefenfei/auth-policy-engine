/**
 * 登录认证流程状态机有限值枚举 (AttemptStatus)
 * <p>
 * 对应后端 com.example.authengine.attempt.AttemptStatus
 */
export enum AttemptStatus {
  /** 进行中：等待用户执行待核验项 */
  IN_PROGRESS = 'IN_PROGRESS',
  /** 策略达成：所有必要条件已满足，可完成核销 */
  SUCCEEDED = 'SUCCEEDED',
  /** 失败：达到安全上限或必要分支均已失效 */
  FAILED = 'FAILED',
  /** 已核销：会话访问令牌已签发，一次性 Attempt 失效 */
  CONSUMED = 'CONSUMED',
}

export interface AttemptStatusMeta {
  code: AttemptStatus;
  label: string;
  badgeClass: string;
  description: string;
}

export const ATTEMPT_STATUS_META: Record<AttemptStatus, AttemptStatusMeta> = {
  [AttemptStatus.IN_PROGRESS]: {
    code: AttemptStatus.IN_PROGRESS,
    label: '进行中',
    badgeClass: 'bg-indigo-500/20 text-indigo-300 border-indigo-500/30',
    description: '认证流程执行中，等待用户完成待核验项目',
  },
  [AttemptStatus.SUCCEEDED]: {
    code: AttemptStatus.SUCCEEDED,
    label: '条件已达成',
    badgeClass: 'bg-emerald-500/20 text-emerald-300 border-emerald-500/30',
    description: '策略条件均已满足，可完成核销换取访问令牌',
  },
  [AttemptStatus.FAILED]: {
    code: AttemptStatus.FAILED,
    label: '流程失败',
    badgeClass: 'bg-rose-500/20 text-rose-300 border-rose-500/30',
    description: '因失败次数超限或必要分支失败，流程已终止',
  },
  [AttemptStatus.CONSUMED]: {
    code: AttemptStatus.CONSUMED,
    label: '已安全核销',
    badgeClass: 'bg-purple-500/20 text-purple-300 border-purple-500/30',
    description: '一次性会话已成功核销并签发令牌，单次凭据已失效',
  },
};
