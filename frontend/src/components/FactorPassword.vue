<script setup lang="ts">
import { ref } from 'vue';
import { verifyFactor } from '../api/auth';
import type { AttemptView, StepProgress } from '../types/auth';

const props = defineProps<{
  attempt: AttemptView;
  nodeId: string;
  isAvailable: boolean;
  progress: StepProgress;
}>();

const emit = defineEmits<{
  (e: 'updated', attempt: AttemptView): void;
}>();

const password = ref('Demo@123456');
const loading = ref(false);

async function handleVerify() {
  if (!password.value) return;
  loading.value = true;
  try {
    const res = await verifyFactor(props.attempt.attemptId, props.nodeId, password.value);
    emit('updated', res.attempt);
    if (!res.verified) {
      alert('密码校验失败，请检查密码');
    }
  } catch (err: any) {
    alert('密码验证错误: ' + (err.data?.error || err.message));
  } finally {
    loading.value.reset();
  }
}
</script>

<template>
  <div
    class="rounded-xl p-5 border transition"
    :class="[
      progress === 'VERIFIED'
        ? 'bg-green-50/60 border-green-200'
        : isAvailable
        ? 'bg-white border-blue-300 ring-2 ring-blue-100 shadow-sm'
        : 'bg-gray-50/70 border-gray-200 opacity-60'
    ]"
  >
    <div class="flex items-center justify-between mb-3">
      <div class="flex items-center space-x-2.5">
        <span class="w-8 h-8 rounded-lg bg-indigo-100 text-indigo-700 flex items-center justify-center font-bold text-sm">🔑</span>
        <div>
          <h4 class="text-sm font-bold text-gray-900">密码认证 (PASSWORD)</h4>
          <span class="text-xs text-gray-500 font-mono">nodeId: {{ nodeId }}</span>
        </div>
      </div>
      <span
        class="px-2.5 py-1 text-xs rounded-full font-semibold"
        :class="progress === 'VERIFIED' ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-600'"
      >
        {{ progress }}
      </span>
    </div>

    <div v-if="progress === 'VERIFIED'" class="text-xs text-green-700 bg-green-50 p-2.5 rounded-lg border border-green-200 flex items-center space-x-2">
      <span>✅</span>
      <span>密码已成功验证并记录到状态机。</span>
    </div>
    <div v-else-if="!isAvailable" class="text-xs text-gray-400 bg-gray-50 p-2.5 rounded-lg border border-gray-200">
      ⚠️ 当前步骤不可执行（可能前置未满足或已被分支短路）。
    </div>
    <form v-else @submit.prevent="handleVerify" class="space-y-3">
      <div class="flex space-x-2">
        <input
          v-model="password"
          type="password"
          required
          placeholder="输入密码，如: Demo@123456"
          class="flex-1 px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-blue-500"
        />
        <button
          type="submit"
          :disabled="loading"
          class="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg text-sm font-semibold transition disabled:opacity-50"
        >
          <span v-if="loading" class="animate-spin mr-1">🔄</span>
          <span>验证密码</span>
        </button>
      </div>
      <div class="flex items-center justify-between text-xs text-gray-500">
        <span>快捷填入密码:</span>
        <div class="space-x-1.5 font-mono">
          <button type="button" @click="password = 'Demo@123456'" class="px-1.5 py-0.5 bg-gray-100 hover:bg-gray-200 rounded border">Demo@123456</button>
          <button type="button" @click="password = 'Admin@123456'" class="px-1.5 py-0.5 bg-gray-100 hover:bg-gray-200 rounded border">Admin@123456</button>
        </div>
      </div>
    </form>
  </div>
</template>
