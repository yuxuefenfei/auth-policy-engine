<script setup lang="ts">
import { ref } from 'vue';
import { verifyFactor } from '../api';
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
const showPassword = ref(false);
const loading = ref(false);
const errorMessage = ref('');

async function handleVerify() {
  if (!password.value) return;
  loading.value = true;
  errorMessage.value = '';
  try {
    const res = await verifyFactor(props.attempt.attemptId, props.nodeId, password.value);
    emit('updated', res.attempt);
    if (!res.verified) {
      errorMessage.value = '密码错误，请核对登录密码。';
    }
  } catch (err: any) {
    errorMessage.value = err.data?.error || err.message || '网络或服务端异常';
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div
    class="rounded-2xl p-6 border transition-all duration-300 relative overflow-hidden backdrop-blur-md"
    :class="[
      progress === 'VERIFIED'
        ? 'bg-emerald-950/20 border-emerald-500/40 shadow-lg shadow-emerald-500/5'
        : isAvailable
        ? 'bg-slate-900/80 border-indigo-500/50 shadow-xl shadow-indigo-500/10 ring-1 ring-indigo-500/20'
        : 'bg-slate-900/40 border-slate-800/80 opacity-60'
    ]"
  >
    <!-- 卡片头部 -->
    <div class="flex items-center justify-between mb-4">
      <div class="flex items-center space-x-3">
        <div
          class="w-10 h-10 rounded-xl flex items-center justify-center text-lg shadow-inner"
          :class="progress === 'VERIFIED' ? 'bg-emerald-500/20 text-emerald-400' : 'bg-indigo-500/20 text-indigo-400'"
        >
          <span>🔑</span>
        </div>
        <div>
          <h4 class="text-sm font-bold text-slate-100 flex items-center space-x-2">
            <span>密码身份认证 (PASSWORD)</span>
            <span class="text-[10px] px-1.5 py-0.5 rounded bg-slate-800 text-slate-400 font-mono">BCrypt</span>
          </h4>
          <span class="text-xs text-slate-400 font-mono">节点 ID: {{ nodeId }}</span>
        </div>
      </div>

      <span
        class="px-2.5 py-1 text-xs rounded-full font-mono font-semibold border"
        :class="[
          progress === 'VERIFIED'
            ? 'bg-emerald-500/20 text-emerald-300 border-emerald-500/30'
            : isAvailable
            ? 'bg-indigo-500/20 text-indigo-300 border-indigo-500/30 ring-1 ring-indigo-500/20'
            : 'bg-slate-800 text-slate-400 border-slate-700'
        ]"
      >
        {{ progress === 'VERIFIED' ? '✓ 校验通过' : progress }}
      </span>
    </div>

    <!-- 状态：已通过 -->
    <div v-if="progress === 'VERIFIED'" class="p-3.5 bg-emerald-950/30 rounded-xl border border-emerald-500/30 flex items-center space-x-3 text-xs text-emerald-300">
      <span class="w-6 h-6 rounded-full bg-emerald-500/20 flex items-center justify-center font-bold text-emerald-400">✓</span>
      <div>
        <p class="font-semibold">密码凭证校验通过</p>
        <p class="text-[11px] text-emerald-400/80">已通过 BCrypt 安全校验，当前认证状态已同步至状态机。</p>
      </div>
    </div>

    <!-- 状态：不可用 -->
    <div v-else-if="!isAvailable" class="p-3.5 bg-slate-900/60 rounded-xl border border-slate-800 text-xs text-slate-400 flex items-center space-x-2">
      <span>🔒</span>
      <span>当前认证暂不可用（需满足前置条件或已被短路分支忽略）。</span>
    </div>

    <!-- 状态：可输入验证 -->
    <form v-else @submit.prevent="handleVerify" class="space-y-4">
      <div>
        <label class="block text-xs font-medium text-slate-300 mb-1.5">输入登录密码</label>
        <div class="relative">
          <input
            v-model="password"
            :type="showPassword ? 'text' : 'password'"
            required
            placeholder="输入演示密码，例如 Demo@123456"
            class="w-full px-4 py-2.5 bg-slate-950/80 border border-slate-700/80 rounded-xl text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent transition font-mono pr-10"
          />
          <button
            type="button"
            @click="showPassword = !showPassword"
            class="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-200 text-xs transition"
          >
            {{ showPassword ? '🙈' : '👁️' }}
          </button>
        </div>
      </div>

      <!-- 快捷填入 -->
      <div class="flex items-center justify-between text-xs text-slate-400 pt-1">
        <span class="flex items-center space-x-1">
          <span>⚡ 快捷填入:</span>
        </span>
        <div class="flex space-x-2 font-mono">
          <button
            type="button"
            @click="password = 'Demo@123456'"
            class="px-2 py-0.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 border border-slate-700 transition hover:border-indigo-500/50"
          >
            Demo@123456
          </button>
          <button
            type="button"
            @click="password = 'Admin@123456'"
            class="px-2 py-0.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 border border-slate-700 transition hover:border-indigo-500/50"
          >
            Admin@123456
          </button>
        </div>
      </div>

      <!-- 错误提示 -->
      <div v-if="errorMessage" class="p-3 bg-rose-950/30 border border-rose-500/40 rounded-xl text-xs text-rose-300 flex items-center space-x-2">
        <span>⚠️</span>
        <span>{{ errorMessage }}</span>
      </div>

      <button
        type="submit"
        :disabled="loading"
        class="w-full py-2.5 bg-gradient-to-r from-indigo-600 to-indigo-500 hover:from-indigo-500 hover:to-indigo-600 text-white font-semibold rounded-xl text-sm transition shadow-lg shadow-indigo-600/20 disabled:opacity-50 flex items-center justify-center space-x-2"
      >
        <span v-if="loading" class="animate-spin">🔄</span>
        <span>提交密码验证</span>
      </button>
    </form>
  </div>
</template>
