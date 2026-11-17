<script setup lang="ts">
import {
  type PolicyNode,
  type FactorOption,
  StepProgress,
  FactorType,
  PolicyOp,
  FACTOR_TYPE_META,
  POLICY_OP_META,
  STEP_PROGRESS_META,
} from '../types/auth';

const props = defineProps<{
  node: PolicyNode | null;
  progress: Record<string, StepProgress>;
  available: FactorOption[];
  isRoot?: boolean;
}>();

function factorDetails(factor?: string) {
  if (factor && factor in FACTOR_TYPE_META) {
    const meta = FACTOR_TYPE_META[factor as FactorType];
    return { name: `${meta.name} (${meta.badge})`, icon: meta.icon, color: meta.color, desc: meta.description };
  }
  return { name: '扩展认证方式', icon: '⚙️', color: 'slate', desc: '自定义扩展认证项' };
}

function opDetails(op: string) {
  if (op && op in POLICY_OP_META) {
    const meta = POLICY_OP_META[op as PolicyOp];
    const desc = op === PolicyOp.AT_LEAST ? `至少满足其中 ${props.node?.required || 2} 项认证` : meta.description;
    return { label: meta.label, color: meta.badgeClass, desc };
  }
  return { label: op, color: 'bg-slate-500/10 text-slate-400 border-slate-500/30', desc: '' };
}

function isAvailable(nodeId: string) {
  return props.available && props.available.some((a) => a.nodeId === nodeId);
}

function statusBadge(status?: StepProgress, available?: boolean) {
  if (status && status in STEP_PROGRESS_META) {
    const meta = STEP_PROGRESS_META[status];
    return { text: meta.label, class: meta.badgeClass };
  }
  if (available) {
    const meta = STEP_PROGRESS_META[StepProgress.READY];
    return { text: meta.label, class: meta.badgeClass };
  }
  const defaultMeta = STEP_PROGRESS_META[StepProgress.PENDING];
  return { text: defaultMeta.label, class: defaultMeta.badgeClass };
}
</script>

<template>
  <div v-if="node" class="text-xs select-none">
    <!-- 组合节点: ALL, ANY, AT_LEAST, SEQUENCE -->
    <div v-if="node.op !== 'FACTOR'" class="space-y-3">
      <div class="inline-flex items-center space-x-2 px-2.5 py-1 rounded-lg border text-[11px] font-medium" :class="opDetails(node.op).color">
        <span class="w-1.5 h-1.5 rounded-full bg-current"></span>
        <span class="font-bold font-mono">{{ opDetails(node.op).label }}</span>
        <span class="text-slate-400 text-[10px] hidden sm:inline">· {{ opDetails(node.op).desc }}</span>
      </div>

      <!-- 子节点连接树线 -->
      <div class="pl-4 ml-3 border-l-2 border-slate-700/60 hover:border-indigo-500/40 transition-colors space-y-3 pt-1">
        <PolicyNodeViewer
          v-for="(child, idx) in node.children"
          :key="idx"
          :node="child"
          :progress="progress"
          :available="available"
        />
      </div>
    </div>

    <!-- 叶子因子节点: FACTOR -->
    <div
      v-else-if="node.id"
      class="group relative flex items-center justify-between p-3 rounded-xl border transition-all duration-300 backdrop-blur-sm"
      :class="[
        progress[node.id] === 'VERIFIED'
          ? 'bg-emerald-950/20 border-emerald-500/40 shadow-sm shadow-emerald-500/10'
          : progress[node.id] === 'CHALLENGE_ISSUED'
          ? 'bg-amber-950/20 border-amber-500/40 shadow-sm shadow-amber-500/10'
          : isAvailable(node.id)
          ? 'bg-indigo-950/25 border-indigo-500/50 shadow-md shadow-indigo-500/10 ring-1 ring-indigo-500/30'
          : 'bg-slate-900/40 border-slate-800 opacity-65'
      ]"
    >
      <div class="flex items-center space-x-3">
        <div
          class="w-8 h-8 rounded-lg flex items-center justify-center text-sm shadow-inner transition-transform group-hover:scale-105"
          :class="[
            progress[node.id] === 'VERIFIED'
              ? 'bg-emerald-500/20 text-emerald-300'
              : isAvailable(node.id)
              ? 'bg-indigo-500/20 text-indigo-300'
              : 'bg-slate-800 text-slate-400'
          ]"
        >
          <span>{{ factorDetails(node.factor).icon }}</span>
        </div>
        <div>
          <div class="flex items-center space-x-2">
            <span class="font-bold text-slate-200 font-mono tracking-tight">{{ node.id }}</span>
            <span class="text-[10px] text-slate-400 font-sans">({{ factorDetails(node.factor).name }})</span>
          </div>
          <div class="text-[10px] text-slate-500 mt-0.5">
            {{ factorDetails(node.factor).desc }}
          </div>
        </div>
      </div>

      <div class="flex items-center space-x-2">
        <span
          class="px-2 py-0.5 rounded-md border text-[10px] font-mono font-semibold tracking-wider transition-all"
          :class="statusBadge(progress[node.id], isAvailable(node.id)).class"
        >
          {{ statusBadge(progress[node.id], isAvailable(node.id)).text }}
        </span>
      </div>
    </div>
  </div>
</template>
