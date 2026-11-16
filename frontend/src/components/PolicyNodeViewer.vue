<script setup lang="ts">
import type { PolicyNode, StepProgress, FactorOption } from '../types/auth';

const props = defineProps<{
  node: PolicyNode | null;
  progress: Record<string, StepProgress>;
  available: FactorOption[];
  isRoot?: boolean;
}>();

function factorDetails(factor?: string) {
  switch (factor) {
    case 'PASSWORD':
      return { name: '密码凭证 (BCrypt)', icon: '🔑', color: 'indigo', desc: '口令凭证哈希单向校验' };
    case 'SMS_OTP':
      return { name: '短信验证码 (OTP)', icon: '💬', color: 'amber', desc: '带时效一次性动态校验码' };
    case 'APP_QR_APPROVAL':
      return { name: 'APP 扫码授权 (QR)', icon: '📲', color: 'purple', desc: '独立移动端凭证反向授权' };
    case 'PASSKEY':
      return { name: '生物密钥 (Passkey)', icon: '🛡️', color: 'rose', desc: 'WebAuthn 硬件公钥认证' };
    default:
      return { name: '未知因子', icon: '⚙️', color: 'slate', desc: '自定义扩展认证因子' };
  }
}

function opDetails(op: string) {
  switch (op) {
    case 'ALL':
      return { label: 'ALL 逻辑全与', color: 'bg-indigo-500/10 text-indigo-400 border-indigo-500/30', desc: '必须满足全部子条件' };
    case 'ANY':
      return { label: 'ANY 逻辑或 (短路)', color: 'bg-purple-500/10 text-purple-400 border-purple-500/30', desc: '任意子节点达成即可满足' };
    case 'AT_LEAST':
      return { label: 'AT_LEAST 多数派', color: 'bg-blue-500/10 text-blue-400 border-blue-500/30', desc: `必须至少满足 ${props.node?.required || 2} 项独立因子` };
    case 'SEQUENCE':
      return { label: 'SEQUENCE 顺序流转', color: 'bg-amber-500/10 text-amber-400 border-amber-500/30', desc: '按严格顺序依次推进解锁' };
    default:
      return { label: op, color: 'bg-slate-500/10 text-slate-400 border-slate-500/30', desc: '' };
  }
}

function isAvailable(nodeId: string) {
  return props.available && props.available.some((a) => a.nodeId === nodeId);
}

function statusBadge(status?: StepProgress, available?: boolean) {
  if (status === 'VERIFIED') {
    return { text: '已验证 VERIFIED', class: 'bg-emerald-500/20 text-emerald-400 border-emerald-500/30 ring-1 ring-emerald-500/20' };
  }
  if (status === 'CHALLENGE_ISSUED') {
    return { text: '挑战就绪 CHALLENGED', class: 'bg-amber-500/20 text-amber-400 border-amber-500/30 animate-pulse' };
  }
  if (status === 'EXHAUSTED') {
    return { text: '锁定 EXHAUSTED', class: 'bg-rose-500/20 text-rose-400 border-rose-500/30' };
  }
  if (available) {
    return { text: '可执行 READY', class: 'bg-indigo-500/20 text-indigo-300 border-indigo-500/40 ring-1 ring-indigo-500/30' };
  }
  return { text: '等待中 PENDING', class: 'bg-slate-800 text-slate-400 border-slate-700' };
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
