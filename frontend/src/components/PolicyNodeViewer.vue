<script setup lang="ts">
import type { PolicyNode, StepProgress, FactorOption } from '../types/auth';

const props = defineProps<{
  node: PolicyNode | null;
  progress: Record<string, StepProgress>;
  available: FactorOption[];
}>();

function factorEmoji(factor?: string) {
  switch (factor) {
    case 'PASSWORD': return '🔑';
    case 'SMS_OTP': return '💬';
    case 'APP_QR_APPROVAL': return '📲';
    case 'PASSKEY': return '🛡️';
    default: return '⚙️';
  }
}

function isAvailable(nodeId: string) {
  return props.available && props.available.some((a) => a.nodeId === nodeId);
}

function progressClass(status?: StepProgress) {
  switch (status) {
    case 'VERIFIED': return 'bg-green-100 text-green-800';
    case 'CHALLENGE_ISSUED': return 'bg-yellow-100 text-yellow-800';
    case 'EXHAUSTED': return 'bg-red-100 text-red-800';
    default: return 'bg-gray-100 text-gray-600';
  }
}

function nodeStatusClass(nodeId: string) {
  const status = props.progress[nodeId];
  if (status === 'VERIFIED') return 'border-green-300 bg-green-50/40';
  if (status === 'CHALLENGE_ISSUED') return 'border-yellow-300 bg-yellow-50/40';
  if (isAvailable(nodeId)) return 'border-blue-300 ring-1 ring-blue-200';
  return 'border-gray-200';
}
</script>

<template>
  <div v-if="node" class="text-xs">
    <!-- 组合节点: ALL, ANY, AT_LEAST, SEQUENCE -->
    <div v-if="node.op !== 'FACTOR'" class="space-y-2">
      <div class="flex items-center space-x-2 font-bold text-gray-800">
        <span class="px-2 py-0.5 rounded bg-indigo-100 text-indigo-800 text-[11px]">
          {{ node.op }}
          <span v-if="node.op === 'AT_LEAST'">(至少 {{ node.required }} 项)</span>
        </span>
        <span class="text-gray-400 text-[11px]">组合逻辑</span>
      </div>
      <div class="pl-4 ml-2 border-l-2 border-gray-200 space-y-2.5">
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
      class="flex items-center justify-between p-2 rounded-lg border bg-white shadow-xs"
      :class="nodeStatusClass(node.id)"
    >
      <div class="flex items-center space-x-2">
        <span>{{ factorEmoji(node.factor) }}</span>
        <div>
          <span class="font-bold text-gray-800">{{ node.id }}</span>
          <span class="text-[10px] text-gray-400 ml-1">({{ node.factor }})</span>
        </div>
      </div>
      <div class="flex items-center space-x-1.5 font-mono text-[10px]">
        <span v-if="isAvailable(node.id)" class="px-1.5 py-0.5 rounded bg-blue-100 text-blue-700 font-bold">
          AVAILABLE
        </span>
        <span :class="progressClass(progress[node.id])" class="px-1.5 py-0.5 rounded font-bold">
          {{ progress[node.id] || 'PENDING' }}
        </span>
      </div>
    </div>
  </div>
</template>
