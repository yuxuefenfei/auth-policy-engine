<script setup lang="ts">
import { ref, onUnmounted } from 'vue';
import { startFactor, verifyFactor, getLatestDemoSms } from '../api/auth';
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

const smsCode = ref('');
const cooldown = ref(0);
const loadingStart = ref(false);
const loadingVerify = ref(false);
const detectedConsoleSms = ref('');
let timer: any = null;

async function handleStart() {
  loadingStart.value = true;
  try {
    const res = await startFactor(props.attempt.attemptId, props.nodeId);
    emit('updated', res.attempt);
    startCountdown();
    setTimeout(fetchConsoleCode, 400);
  } catch (err: any) {
    alert('发起短信挑战失败: ' + (err.data?.error || err.message));
  } finally {
    loadingStart.value = false;
  }
}

async function handleVerify() {
  if (!smsCode.value) return;
  loadingVerify.value = true;
  try {
    const res = await verifyFactor(props.attempt.attemptId, props.nodeId, smsCode.value.trim());
    emit('updated', res.attempt);
    if (!res.verified) {
      alert('验证码错误或已过期');
    }
  } catch (err: any) {
    alert('验证码校验失败: ' + (err.data?.error || err.message));
  } finally {
    loadingVerify.value = false;
  }
}

async function fetchConsoleCode() {
  try {
    const mobile = props.username === 'admin' ? '13900000000' : '13800000000';
    const data = await getLatestDemoSms(mobile);
    if (data.code) {
      detectedConsoleSms.value = data.code;
      smsCode.value = data.code;
    }
  } catch (e) {}
}

function startCountdown() {
  cooldown.value = 30;
  if (timer) clearInterval(timer);
  timer = setInterval(() => {
    if (cooldown.value > 0) cooldown.value--;
    else clearInterval(timer);
  }, 1000);
}

onUnmounted(() => {
  if (timer) clearInterval(timer);
});
</script>

<template>
  <div
    class="rounded-xl p-5 border transition"
    :class="[
      progress === 'VERIFIED'
        ? 'bg-green-50/60 border-green-200'
        : isAvailable
        ? 'bg-white border-yellow-300 ring-2 ring-yellow-100 shadow-sm'
        : 'bg-gray-50/70 border-gray-200 opacity-60'
    ]"
  >
    <div class="flex items-center justify-between mb-3">
      <div class="flex items-center space-x-2.5">
        <span class="w-8 h-8 rounded-lg bg-yellow-100 text-yellow-800 flex items-center justify-center font-bold text-sm">💬</span>
        <div>
          <h4 class="text-sm font-bold text-gray-900">短信动态验证码 (SMS_OTP)</h4>
          <span class="text-xs text-gray-500 font-mono">nodeId: {{ nodeId }} · 演示手机: {{ username === 'admin' ? '13900000000' : '13800000000' }}</span>
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
      <span>短信验证码已通过验证。</span>
    </div>
    <div v-else-if="!isAvailable" class="text-xs text-gray-400 bg-gray-50 p-2.5 rounded-lg border border-gray-200">
      ⚠️ 当前步骤不可执行（需等待前序条件完成）。
    </div>
    <div v-else class="space-y-3">
      <!-- 发送挑战按钮 -->
      <div class="flex items-center space-x-3">
        <button
          @click="handleStart"
          :disabled="cooldown > 0 || loadingStart"
          class="px-3.5 py-2 bg-yellow-500 hover:bg-yellow-600 text-white rounded-lg text-sm font-semibold transition disabled:opacity-50 flex items-center space-x-1"
        >
          <span v-if="loadingStart" class="animate-spin mr-1">🔄</span>
          <span>{{ cooldown > 0 ? `${cooldown}s 后重新发送` : '发送短信验证码 (Start Challenge)' }}</span>
        </button>
        <button
          @click="fetchConsoleCode"
          class="px-2.5 py-2 bg-gray-100 hover:bg-gray-200 text-gray-700 rounded-lg text-xs font-semibold border flex items-center space-x-1"
        >
          <span>⚡</span>
          <span>检测控制台验证码</span>
        </button>
      </div>

      <!-- 验证码输入与提交 -->
      <form @submit.prevent="handleVerify" class="space-y-2">
        <div class="flex space-x-2">
          <input
            v-model="smsCode"
            type="text"
            maxlength="6"
            pattern="[0-9]{6}"
            required
            placeholder="输入 6 位数字验证码"
            class="flex-1 px-3 py-2 border border-gray-300 rounded-lg text-sm font-mono tracking-widest focus:ring-2 focus:ring-yellow-500"
          />
          <button
            type="submit"
            :disabled="loadingVerify"
            class="px-4 py-2 bg-yellow-600 hover:bg-yellow-700 text-white rounded-lg text-sm font-semibold transition disabled:opacity-50"
          >
            <span v-if="loadingVerify" class="animate-spin">🔄</span>
            <span>校验验证码</span>
          </button>
        </div>
        <div class="flex items-center justify-between text-xs text-gray-500">
          <span>💡 验证码不会下发至前端。本地服务直接输出在控制台日志中。</span>
          <span v-if="detectedConsoleSms" class="text-green-600 font-bold">检测到: {{ detectedConsoleSms }}</span>
        </div>
      </form>
    </div>
  </div>
</template>
