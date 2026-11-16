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
const errorMessage = ref('');
let timer: any = null;

async function handleStart() {
  loadingStart.value = true;
  errorMessage.value = '';
  try {
    const res = await startFactor(props.attempt.attemptId, props.nodeId);
    emit('updated', res.attempt);
    startCountdown();
    setTimeout(fetchConsoleCode, 500);
  } catch (err: any) {
    errorMessage.value = err.data?.error || err.message || '发送失败';
  } finally {
    loadingStart.value = false;
  }
}

async function handleVerify() {
  if (!smsCode.value) return;
  loadingVerify.value = true;
  errorMessage.value = '';
  try {
    const res = await verifyFactor(props.attempt.attemptId, props.nodeId, smsCode.value.trim());
    emit('updated', res.attempt);
    if (!res.verified) {
      errorMessage.value = '验证码错误、已过期或不匹配，请重试。';
    }
  } catch (err: any) {
    errorMessage.value = err.data?.error || err.message || '校验失败';
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
    }
  } catch (e) {}
}

function fillDetectedCode() {
  if (detectedConsoleSms.value) {
    smsCode.value = detectedConsoleSms.value;
  }
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
    class="rounded-2xl p-6 border transition-all duration-300 relative overflow-hidden backdrop-blur-md"
    :class="[
      progress === 'VERIFIED'
        ? 'bg-emerald-950/20 border-emerald-500/40 shadow-lg shadow-emerald-500/5'
        : isAvailable
        ? 'bg-slate-900/80 border-amber-500/50 shadow-xl shadow-amber-500/10 ring-1 ring-amber-500/20'
        : 'bg-slate-900/40 border-slate-800/80 opacity-60'
    ]"
  >
    <!-- 头部 -->
    <div class="flex items-center justify-between mb-4">
      <div class="flex items-center space-x-3">
        <div
          class="w-10 h-10 rounded-xl flex items-center justify-center text-lg shadow-inner"
          :class="progress === 'VERIFIED' ? 'bg-emerald-500/20 text-emerald-400' : 'bg-amber-500/20 text-amber-400'"
        >
          <span>💬</span>
        </div>
        <div>
          <h4 class="text-sm font-bold text-slate-100 flex items-center space-x-2">
            <span>短信动态验证码 (SMS_OTP)</span>
            <span class="text-[10px] px-1.5 py-0.5 rounded bg-slate-800 text-slate-400 font-mono">120s TTL</span>
          </h4>
          <span class="text-xs text-slate-400 font-mono">
            手机: <span class="text-amber-400 font-semibold">{{ username === 'admin' ? '13900000000' : '13800000000' }}</span>
          </span>
        </div>
      </div>

      <span
        class="px-2.5 py-1 text-xs rounded-full font-mono font-semibold border"
        :class="[
          progress === 'VERIFIED'
            ? 'bg-emerald-500/20 text-emerald-300 border-emerald-500/30'
            : isAvailable
            ? 'bg-amber-500/20 text-amber-300 border-amber-500/30 ring-1 ring-amber-500/20'
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
        <p class="font-semibold">短信动态验证码已成功核验</p>
        <p class="text-[11px] text-emerald-400/80">一次性动态口令匹配成功，挑战哈希已在服务端原子销毁。</p>
      </div>
    </div>

    <!-- 状态：不可用 -->
    <div v-else-if="!isAvailable" class="p-3.5 bg-slate-900/60 rounded-xl border border-slate-800 text-xs text-slate-400 flex items-center space-x-2">
      <span>🔒</span>
      <span>当前因子不可执行（需满足前序条件或已被跳过）。</span>
    </div>

    <!-- 状态：可发送挑战与校验 -->
    <div v-else class="space-y-4">
      <!-- 触发挑战发送 -->
      <div class="flex items-center space-x-3">
        <button
          type="button"
          @click="handleStart"
          :disabled="cooldown > 0 || loadingStart"
          class="flex-1 py-2.5 px-4 bg-gradient-to-r from-amber-600 to-amber-500 hover:from-amber-500 hover:to-amber-600 text-white font-semibold rounded-xl text-xs transition shadow-lg shadow-amber-600/20 disabled:opacity-50 flex items-center justify-center space-x-2"
        >
          <span v-if="loadingStart" class="animate-spin">🔄</span>
          <span>{{ cooldown > 0 ? `⏳ ${cooldown}s 后可重新获取` : '📡 发起短信挑战 (Start Challenge)' }}</span>
        </button>
        <button
          type="button"
          @click="fetchConsoleCode"
          class="py-2.5 px-3 bg-slate-800 hover:bg-slate-700 text-slate-300 border border-slate-700 rounded-xl text-xs font-mono transition flex items-center space-x-1"
        >
          <span>⚡ 探测控制台</span>
        </button>
      </div>

      <!-- 控制台自动捕获悬浮提示 -->
      <div
        v-if="detectedConsoleSms"
        class="p-3 rounded-xl bg-amber-950/40 border border-amber-500/40 flex items-center justify-between text-xs text-amber-300 animate-subtle-pulse"
      >
        <div class="flex items-center space-x-2">
          <span>📡</span>
          <span>检测到控制台验证码: <strong class="font-mono text-white text-sm tracking-widest">{{ detectedConsoleSms }}</strong></span>
        </div>
        <button
          type="button"
          @click="fillDetectedCode"
          class="px-2.5 py-1 bg-amber-500 hover:bg-amber-400 text-slate-950 rounded-lg font-bold text-[11px] transition"
        >
          一键填入 ↵
        </button>
      </div>

      <!-- 6 位验证码输入与校验 -->
      <form @submit.prevent="handleVerify" class="space-y-3">
        <div>
          <label class="block text-xs font-medium text-slate-300 mb-1.5">输入 6 位数字验证码</label>
          <div class="flex space-x-2">
            <input
              v-model="smsCode"
              type="text"
              maxlength="6"
              pattern="[0-9]{6}"
              required
              placeholder="000000"
              class="flex-1 px-4 py-2.5 bg-slate-950/80 border border-slate-700/80 rounded-xl text-base text-slate-100 placeholder-slate-600 focus:outline-none focus:ring-2 focus:ring-amber-500 focus:border-transparent transition font-mono tracking-widest text-center"
            />
            <button
              type="submit"
              :disabled="loadingVerify || smsCode.length < 6"
              class="px-5 py-2.5 bg-amber-500 hover:bg-amber-400 text-slate-950 font-bold rounded-xl text-xs transition shadow-md shadow-amber-500/20 disabled:opacity-50 flex items-center space-x-1"
            >
              <span v-if="loadingVerify" class="animate-spin">🔄</span>
              <span>提交校验</span>
            </button>
          </div>
        </div>

        <div v-if="errorMessage" class="p-3 bg-rose-950/30 border border-rose-500/40 rounded-xl text-xs text-rose-300 flex items-center space-x-2">
          <span>⚠️</span>
          <span>{{ errorMessage }}</span>
        </div>

        <p class="text-[11px] text-slate-500">
          💡 本地演示模式：验证码直接打印在 Spring Boot 控制台日志中（如 <code>SMS mobile=... code=123456</code>）。
        </p>
      </form>
    </div>
  </div>
</template>
