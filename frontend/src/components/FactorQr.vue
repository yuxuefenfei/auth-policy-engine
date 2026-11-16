<script setup lang="ts">
import { ref, onUnmounted, nextTick } from 'vue';
import { startFactor, getAttempt, approveQr } from '../api/auth';
import type { AttemptView, StepProgress } from '../types/auth';

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
let pollTimer: any = null;

async function handleStart() {
  loadingStart.value = true;
  try {
    const res = await startFactor(props.attempt.attemptId, props.nodeId);
    emit('updated', res.attempt);
    qrTicket.value = res.qrTicket || '';
    expiresAtText.value = new Date(res.challengeExpiresAt).toLocaleTimeString();

    await nextTick();
    renderQrCode(res.qrTicket || '');
    startPolling();
  } catch (err: any) {
    alert('生成二维码挑战失败: ' + (err.data?.error || err.message));
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
      width: 120,
      height: 120,
      colorDark: '#4f46e5',
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
      const updated = await getAttempt(props.attempt.attemptId);
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
  try {
    const pwd = props.username === 'admin' ? 'Admin@123456' : 'Demo@123456';
    const res = await approveQr(qrTicket.value, props.username, pwd);
    emit('updated', res);
    if (pollTimer) clearInterval(pollTimer);
  } catch (err: any) {
    alert('模拟扫码授权失败: ' + (err.data?.error || err.message));
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
    class="rounded-xl p-5 border transition"
    :class="[
      progress === 'VERIFIED'
        ? 'bg-green-50/60 border-green-200'
        : isAvailable
        ? 'bg-white border-purple-300 ring-2 ring-purple-100 shadow-sm'
        : 'bg-gray-50/70 border-gray-200 opacity-60'
    ]"
  >
    <div class="flex items-center justify-between mb-3">
      <div class="flex items-center space-x-2.5">
        <span class="w-8 h-8 rounded-lg bg-purple-100 text-purple-700 flex items-center justify-center font-bold text-sm">📲</span>
        <div>
          <h4 class="text-sm font-bold text-gray-900">APP 扫码确认 (APP_QR_APPROVAL)</h4>
          <span class="text-xs text-gray-500 font-mono">nodeId: {{ nodeId }} · 需由已认证 APP 调用</span>
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
      <span>APP 端已成功扫码并授权本次登录！</span>
    </div>
    <div v-else-if="!isAvailable" class="text-xs text-gray-400 bg-gray-50 p-2.5 rounded-lg border border-gray-200">
      ⚠️ 当前步骤不可执行。
    </div>
    <div v-else class="space-y-4">
      <!-- 未生成二维码 -->
      <div v-if="!qrTicket" class="text-center py-4">
        <button
          @click="handleStart"
          :disabled="loadingStart"
          class="px-4 py-2.5 bg-purple-600 hover:bg-purple-700 text-white rounded-xl text-sm font-semibold transition shadow disabled:opacity-50 flex items-center justify-center mx-auto space-x-1.5"
        >
          <span v-if="loadingStart" class="animate-spin">🔄</span>
          <span>📷 生成登录二维码 (Start Challenge)</span>
        </button>
        <p class="text-xs text-gray-500 mt-2">服务端将签发一次性随机 Ticket (90秒有效期)</p>
      </div>

      <!-- 二维码展示 -->
      <div v-else class="flex flex-col sm:flex-row items-center gap-6 p-4 bg-purple-50/50 rounded-xl border border-purple-100">
        <div class="flex flex-col items-center">
          <div id="qr-canvas-container" class="bg-white p-2.5 rounded-xl border shadow-sm w-36 h-36 flex items-center justify-center"></div>
          <span class="text-xs text-purple-700 font-semibold mt-2 animate-pulse">⏳ 正在等待 APP 授权...</span>
        </div>
        <div class="flex-1 space-y-2 text-xs">
          <div>
            <span class="text-gray-500 block">Ticket 原文:</span>
            <code class="bg-white px-2 py-1 rounded border font-mono text-purple-900 break-all select-all block mt-0.5">{{ qrTicket }}</code>
          </div>
          <div class="text-gray-600">
            <span>有效期至: </span>
            <span class="font-mono font-semibold">{{ expiresAtText }}</span>
          </div>

          <!-- 模拟确认 -->
          <div class="pt-2 border-t border-purple-100 flex flex-wrap gap-2 items-center">
            <button
              @click="simulateAppApprove"
              :disabled="loadingApprove"
              class="px-3 py-1.5 bg-gradient-to-r from-purple-600 to-indigo-600 text-white rounded-lg font-semibold hover:from-purple-700 hover:to-indigo-700 transition flex items-center space-x-1"
            >
              <span>📱</span>
              <span>作为 {{ username }} 一键扫码确认</span>
            </button>
            <button @click="handleStart" class="px-2.5 py-1.5 bg-white border border-purple-200 text-purple-700 rounded-lg hover:bg-purple-50 transition">
              刷新二维码
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
