<script setup lang="ts">
import { ref, onUnmounted, nextTick } from 'vue';
import { startFactor, getAttemptStatus, approveQrTicket } from '../api';
import type { AttemptView } from '../types/auth';
import type { StepProgress } from '../enums';

const props = defineProps<{
  attempt: AttemptView;
  nodeId: string;
  isAvailable: boolean;
  progress: StepProgress;
  username: string;
}>();

const emit = defineEmits<{
  (e: 'updated', attempt: AttemptView): void;
}>();

const qrTicket = ref('');
const expiresAtText = ref('');
const loadingStart = ref(false);
const loadingApprove = ref(false);
const errorMessage = ref('');
let pollTimer: any = null;

async function handleStart() {
  loadingStart.value = true;
  errorMessage.value = '';
  try {
    const res = await startFactor(props.attempt.attemptId, props.nodeId);
    emit('updated', res.attempt);
    qrTicket.value = res.qrTicket || '';
    expiresAtText.value = new Date(res.challengeExpiresAt).toLocaleTimeString();

    await nextTick();
    renderQrCode(res.qrTicket || '');
    startPolling();
  } catch (err: any) {
    errorMessage.value = err.data?.error || err.message || '生成二维码失败';
  } finally {
    loadingStart.value = false;
  }
}

function renderQrCode(text: string) {
  const container = document.getElementById('qr-canvas-container');
  if (!container) return;
  container.innerHTML = '';
  // @ts-ignore
  if (window.QRCode) {
    // @ts-ignore
    new window.QRCode(container, {
      text,
      width: 140,
      height: 140,
      colorDark: '#0f172a',
      colorLight: '#ffffff',
    });
  } else {
    container.innerText = text;
  }
}

function startPolling() {
  if (pollTimer) clearInterval(pollTimer);
  pollTimer = setInterval(async () => {
    try {
      const updated = await getAttemptStatus(props.attempt.attemptId);
      emit('updated', updated);
      if (updated.progress[props.nodeId] === 'VERIFIED') {
        clearInterval(pollTimer);
      }
    } catch {
      clearInterval(pollTimer);
    }
  }, 1500);
}

async function simulateAppApprove() {
  if (!qrTicket.value) return;
  loadingApprove.value = true;
  errorMessage.value = '';
  try {
    const pwd = props.username === 'admin' ? 'Admin@123456' : 'Demo@123456';
    const res = await approveQrTicket(qrTicket.value, props.username, pwd);
    emit('updated', res);
    if (pollTimer) clearInterval(pollTimer);
  } catch (err: any) {
    errorMessage.value = err.data?.error || err.message || '扫码确认失败';
  } finally {
    loadingApprove.value = false;
  }
}

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer);
});
</script>

<template>
  <div
    class="rounded-2xl p-6 border transition-all duration-300 relative overflow-hidden backdrop-blur-md"
    :class="[
      progress === 'VERIFIED'
        ? 'bg-emerald-950/20 border-emerald-500/40 shadow-lg shadow-emerald-500/5'
        : isAvailable
        ? 'bg-slate-900/80 border-purple-500/50 shadow-xl shadow-purple-500/10 ring-1 ring-purple-500/20'
        : 'bg-slate-900/40 border-slate-800/80 opacity-60'
    ]"
  >
    <!-- 头部 -->
    <div class="flex items-center justify-between mb-4">
      <div class="flex items-center space-x-3">
        <div
          class="w-10 h-10 rounded-xl flex items-center justify-center text-lg shadow-inner"
          :class="progress === 'VERIFIED' ? 'bg-emerald-500/20 text-emerald-400' : 'bg-purple-500/20 text-purple-400'"
        >
          <span>📲</span>
        </div>
        <div>
          <h4 class="text-sm font-bold text-slate-100 flex items-center space-x-2">
            <span>APP 扫码跨端认证 (APP_QR_APPROVAL)</span>
            <span class="text-[10px] px-1.5 py-0.5 rounded bg-slate-800 text-slate-400 font-mono">90s TTL</span>
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
            ? 'bg-purple-500/20 text-purple-300 border-purple-500/30 ring-1 ring-purple-500/20'
            : 'bg-slate-800 text-slate-400 border-slate-700'
        ]"
      >
        {{ progress === 'VERIFIED' ? '✓ 授权通过' : progress }}
      </span>
    </div>

    <!-- 状态：已通过 -->
    <div v-if="progress === 'VERIFIED'" class="p-3.5 bg-emerald-950/30 rounded-xl border border-emerald-500/30 flex items-center space-x-3 text-xs text-emerald-300">
      <span class="w-6 h-6 rounded-full bg-emerald-500/20 flex items-center justify-center font-bold text-emerald-400">✓</span>
      <div>
        <p class="font-semibold">移动端 APP 扫码授权成功</p>
        <p class="text-[11px] text-emerald-400/80">已通过独立移动端身份完成反向签名授权，Ticket 已被单次原子核销。</p>
      </div>
    </div>

    <!-- 状态：不可用 -->
    <div v-else-if="!isAvailable" class="p-3.5 bg-slate-900/60 rounded-xl border border-slate-800 text-xs text-slate-400 flex items-center space-x-2">
      <span>🔒</span>
      <span>当前认证方式暂不可用（需满足前置条件或已被短路通过）。</span>
    </div>

    <!-- 状态：未生成二维码 -->
    <div v-else-if="!qrTicket" class="text-center py-6 border border-dashed border-slate-700/80 rounded-2xl bg-slate-950/40">
      <div class="w-12 h-12 rounded-2xl bg-purple-500/10 text-purple-400 flex items-center justify-center text-xl mx-auto mb-3 shadow-inner">
        📷
      </div>
      <h5 class="text-xs font-semibold text-slate-200">待获取登录二维码</h5>
      <p class="text-[11px] text-slate-500 mt-1 max-w-sm mx-auto">
        点击下方按钮生成具有时效防护的一次性扫码登录二维码。
      </p>
      <button
        type="button"
        @click="handleStart"
        :disabled="loadingStart"
        class="mt-4 px-5 py-2.5 bg-gradient-to-r from-purple-600 to-indigo-600 hover:from-purple-500 hover:to-indigo-500 text-white font-semibold rounded-xl text-xs transition shadow-lg shadow-purple-600/20 disabled:opacity-50 inline-flex items-center space-x-2"
      >
        <span v-if="loadingStart" class="animate-spin">🔄</span>
        <span>生成动态登录二维码 (获取凭据)</span>
      </button>
    </div>

    <!-- 状态：二维码展示与等待扫码 -->
    <div v-else class="space-y-4">
      <div class="p-5 bg-slate-950/80 rounded-2xl border border-slate-800 flex flex-col sm:flex-row items-center gap-6">
        <!-- 扫码取景框 -->
        <div class="relative p-2.5 bg-white rounded-xl shadow-2xl flex flex-col items-center justify-center overflow-hidden">
          <div id="qr-canvas-container" class="w-[140px] h-[140px] flex items-center justify-center"></div>
          <!-- 激光扫描线动画 -->
          <div class="absolute left-0 right-0 h-1 bg-gradient-to-r from-transparent via-cyan-400 to-transparent animate-scan pointer-events-none"></div>
          <!-- 四角激光准心 -->
          <div class="absolute top-1 left-1 w-2.5 h-2.5 border-t-2 border-l-2 border-indigo-600 pointer-events-none"></div>
          <div class="absolute top-1 right-1 w-2.5 h-2.5 border-t-2 border-r-2 border-indigo-600 pointer-events-none"></div>
          <div class="absolute bottom-1 left-1 w-2.5 h-2.5 border-b-2 border-l-2 border-indigo-600 pointer-events-none"></div>
          <div class="absolute bottom-1 right-1 w-2.5 h-2.5 border-b-2 border-r-2 border-indigo-600 pointer-events-none"></div>
        </div>

        <div class="flex-1 space-y-2.5 text-xs">
          <div class="inline-flex items-center space-x-1.5 px-2.5 py-0.5 rounded-full bg-purple-500/20 text-purple-300 font-mono text-[10px] animate-pulse">
            <span class="w-1.5 h-1.5 rounded-full bg-purple-400"></span>
            <span>正在实时监听 APP 授权 (每 1.5s 轮询)...</span>
          </div>

          <div>
            <span class="text-slate-400 block text-[11px]">一次性授权凭据 (Ticket):</span>
            <code class="block mt-0.5 p-2 bg-slate-900 border border-slate-800 rounded-lg font-mono text-[11px] text-purple-300 break-all select-all">
              {{ qrTicket }}
            </code>
          </div>

          <div class="flex items-center justify-between text-slate-400 text-[11px]">
            <span>有效期至: <strong class="text-slate-200 font-mono">{{ expiresAtText }}</strong></span>
            <button
              type="button"
              @click="handleStart"
              class="text-indigo-400 hover:text-indigo-300 underline font-mono"
            >
              刷新二维码 ↻
            </button>
          </div>

          <!-- 模拟确认快捷动作条 -->
          <div class="pt-2 border-t border-slate-800/80 flex items-center space-x-2">
            <button
              type="button"
              @click="simulateAppApprove"
              :disabled="loadingApprove"
              class="flex-1 py-2 px-3 bg-gradient-to-r from-purple-600 to-indigo-600 hover:from-purple-500 hover:to-indigo-500 text-white rounded-xl font-semibold transition text-xs shadow-md shadow-purple-600/20 disabled:opacity-50 flex items-center justify-center space-x-1.5"
            >
              <span v-if="loadingApprove" class="animate-spin">🔄</span>
              <span>📱 一键作为 {{ username }} 模拟扫码授权</span>
            </button>
          </div>
        </div>
      </div>

      <div v-if="errorMessage" class="p-3 bg-rose-950/30 border border-rose-500/40 rounded-xl text-xs text-rose-300 flex items-center space-x-2">
        <span>⚠️</span>
        <span>{{ errorMessage }}</span>
      </div>
    </div>
  </div>
</template>
