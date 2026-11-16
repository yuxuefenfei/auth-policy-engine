<script setup lang="ts">
import { ref } from 'vue';
import { approveQr } from '../api/auth';
import type { AttemptView } from '../types/auth';

const emit = defineEmits<{
  (e: 'approved', attempt: AttemptView): void;
}>();

const appUser = ref('alice');
const ticket = ref('');
const loading = ref(false);
const result = ref<{ success: boolean; title: string; message: string } | null>(null);

async function handleApprove() {
  if (!ticket.value) return;
  loading.value = true;
  result.value = null;
  try {
    const pwd = appUser.value === 'admin' ? 'Admin@123456' : 'Demo@123456';
    const res = await approveQr(ticket.value.trim(), appUser.value, pwd);
    result.value = {
      success: true,
      title: '✅ 扫码授权成功！',
      message: `APP 端 (${appUser.value}) 已经完成授权。流程状态已推进。`,
    };
    emit('approved', res);
  } catch (err: any) {
    result.value = {
      success: false,
      title: `❌ 授权被拒绝 (HTTP ${err.status})`,
      message: err.data?.error || err.message,
    };
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="max-w-md mx-auto">
    <div class="bg-white rounded-3xl p-6 shadow-lg border-4 border-gray-800 space-y-5">
      <!-- 手机顶部状态条 -->
      <div class="flex justify-between items-center text-xs text-gray-400 pb-2 border-b">
        <span>9:41</span>
        <div class="w-16 h-4 bg-gray-900 rounded-full"></div>
        <span>5G 📶 100%</span>
      </div>

      <div class="text-center space-y-1">
        <div class="w-12 h-12 bg-purple-100 text-purple-600 rounded-2xl flex items-center justify-center mx-auto text-xl shadow">📲</div>
        <h3 class="text-base font-bold text-gray-900">移动端 APP 授权模拟器</h3>
        <p class="text-xs text-gray-500">模拟已登录合法身份的移动客户端，调用 /api/app/qr/approve 确认扫码</p>
      </div>

      <!-- APP 登录身份切换 -->
      <div class="bg-gray-50 p-3 rounded-xl border border-gray-200 text-xs space-y-2">
        <span class="text-gray-600 font-semibold block">已登录 APP 用户身份 (Basic Auth):</span>
        <div class="grid grid-cols-2 gap-2">
          <button
            @click="appUser = 'alice'"
            :class="appUser === 'alice' ? 'bg-purple-600 text-white font-bold' : 'bg-white text-gray-700 border'"
            class="py-2 rounded-lg transition text-center"
          >
            alice (用户)
          </button>
          <button
            @click="appUser = 'admin'"
            :class="appUser === 'admin' ? 'bg-purple-600 text-white font-bold' : 'bg-white text-gray-700 border'"
            class="py-2 rounded-lg transition text-center"
          >
            admin (管理员)
          </button>
        </div>
      </div>

      <!-- 扫码 Ticket 输入框 -->
      <div class="space-y-3">
        <div>
          <label class="block text-xs font-semibold text-gray-700 mb-1">扫描到的 Ticket 原文</label>
          <textarea
            v-model="ticket"
            rows="3"
            placeholder="格式为: attemptId.randomSecretToken"
            class="w-full p-2.5 border border-gray-300 rounded-xl text-xs font-mono"
          ></textarea>
        </div>
        <div class="flex justify-end">
          <button @click="ticket = ''" class="text-xs text-gray-400 hover:text-gray-600">清空</button>
        </div>
      </div>

      <!-- 确认授权按钮 -->
      <button
        @click="handleApprove"
        :disabled="!ticket || loading"
        class="w-full py-3 bg-gradient-to-r from-purple-600 to-indigo-600 text-white font-bold rounded-xl shadow hover:from-purple-700 hover:to-indigo-700 transition disabled:opacity-50"
      >
        <span v-if="loading" class="animate-spin mr-1">🔄</span>
        <span>确认授权本次登录 (Approve)</span>
      </button>

      <!-- 响应结果展示 -->
      <div
        v-if="result"
        :class="result.success ? 'bg-green-50 border-green-200 text-green-800' : 'bg-red-50 border-red-200 text-red-800'"
        class="p-3 rounded-xl border text-xs space-y-1"
      >
        <div class="font-bold">{{ result.title }}</div>
        <p>{{ result.message }}</p>
      </div>

      <div class="text-[11px] text-gray-400 bg-gray-50 p-2.5 rounded-lg border text-center">
        🧪 提示：若登录页 Attempt 用户是 alice，但 APP 模拟器以 admin 身份点击确认，后端会精准拦截并返回 403 Forbidden（扫码账号与登录账号不一致）。
      </div>
    </div>
  </div>
</template>
