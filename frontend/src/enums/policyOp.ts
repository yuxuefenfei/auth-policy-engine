/**
 * 策略 AST 逻辑操作符枚举 (PolicyOp)
 * <p>
 * 对应后端 com.example.authengine.domain.PolicyNode.Op
 */
export enum PolicyOp {
  /** 单叶子因子认证节点 */
  FACTOR = 'FACTOR',
  /** 逻辑全与：所有子分支必须全部满足 */
  ALL = 'ALL',
  /** 逻辑短路或：任意子分支满足即可短路通过 */
  ANY = 'ANY',
  /** 阈值多数派：至少满足 required 项独立因子 */
  AT_LEAST = 'AT_LEAST',
  /** 严格顺序流转：必须按子节点序列先后依次完成 */
  SEQUENCE = 'SEQUENCE',
}

export interface PolicyOpMeta {
  code: PolicyOp;
  label: string;
  badgeClass: string;
  description: string;
}

export const POLICY_OP_META: Record<PolicyOp, PolicyOpMeta> = {
  [PolicyOp.FACTOR]: {
    code: PolicyOp.FACTOR,
    label: 'FACTOR 认证项',
    badgeClass: 'bg-slate-500/10 text-slate-300 border-slate-500/30',
    description: '独立认证因子叶子节点',
  },
  [PolicyOp.ALL]: {
    code: PolicyOp.ALL,
    label: 'ALL 全部满足 (全与)',
    badgeClass: 'bg-indigo-500/10 text-indigo-400 border-indigo-500/30',
    description: '必须满足全部子条件',
  },
  [PolicyOp.ANY]: {
    code: PolicyOp.ANY,
    label: 'ANY 任选其一 (短路)',
    badgeClass: 'bg-purple-500/10 text-purple-400 border-purple-500/30',
    description: '任意一项达成即可快速通过',
  },
  [PolicyOp.AT_LEAST]: {
    code: PolicyOp.AT_LEAST,
    label: 'AT_LEAST 阈值要求',
    badgeClass: 'bg-blue-500/10 text-blue-400 border-blue-500/30',
    description: '至少满足指定项数的独立因子',
  },
  [PolicyOp.SEQUENCE]: {
    code: PolicyOp.SEQUENCE,
    label: 'SEQUENCE 顺序认证',
    badgeClass: 'bg-amber-500/10 text-amber-400 border-amber-500/30',
    description: '必须按固定前后顺序依次完成',
  },
};
