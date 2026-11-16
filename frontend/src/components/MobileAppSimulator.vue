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
      title: '✅ 扫码授权确认成功',
      message: `移动端用户 (${appUser.value}) 已确认授权，服务端状态已更新为 VERIFIED。`,
    };
    emit('approved', res);
  } catch (err: any) {
    result.value = {
      success: false,
      title: `❌ 授权未通过 (HTTP ${err.status})`,
      message: err.data?.error || err.message || '扫码账号核验失败',
    };
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="max-w-md mx-auto py-4">
    <!-- 钛金属拟物手机框架 -->
    <div class="relative bg-slate-900 rounded-[48px] p-4 shadow-2xl border-[6px] border-slate-700/80 ring-1 ring-white/10 shadow-indigo-500/10">
      
      <!-- 手机屏幕 -->
      <div class="bg-slate-950 rounded-[38px] p-5 space-y-5 border border-slate-800/80 overflow-hidden relative">
        
        <!-- 灵动岛与顶部状态栏 -->
        <div class="flex justify-between items-center text-xs text-slate-400 pt-1 px-1">
          <span class="font-semibold text-slate-200">09:41</span>
          <!-- 灵动岛 (Dynamic Island) -->
          <div class="w-24 h-5 bg-black rounded-full border border-white/5 flex items-center justify-center space-x-1.5 shadow-inner">
            <span class="w-2 h-2 rounded-full bg-indigo-500 animate-pulse"></span>
            <span class="text-[9px] text-indigo-300 font-mono">AuthGuard</span>
          </div>
          <div class="flex items-center space-x-1.5 text-[11px] text-slate-300 font-mono">
            <span>5G</span>
            <span>📶</span>
            <span>100%</span>
          </div>
        </div>

        <!-- 模拟 APP 头部标题与 Logo -->
        <div class="text-center pt-2 space-y-1">
          <div class="w-12 h-12 rounded-2xl bg-gradient-to-tr from-purple-600 to-indigo-600 text-white flex items-center justify-center text-xl mx-auto shadow-lg shadow-purple-500/20">
            📲
          </div>
          <h3 class="text-base font-bold text-slate-100">AuthGuard 手机安全令</h3>
          <p class="text-[11px] text-slate-400">
            模拟持有合法登录态的移动客户端，反向调用 <code>/api/app/qr/approve</code>
          </p>
        </div>

        <!-- 登录态身份切换 -->
        <div class="p-3 bg-slate-900/80 rounded-2xl border border-slate-800 space-y-2">
          <div class="flex justify-between items-center text-[11px]">
            <span class="text-slate-400">APP 当前登录身份 (Basic Auth):</span>
            <span class="font-mono text-purple-400 font-semibold">{{ appUser }}</span>
          </div>
          <div class="grid grid-cols-2 gap-2">
            <button
              @click="appUser = 'alice'"
              :class="[
                appUser === 'alice'
                  ? 'bg-purple-600 text-white font-bold shadow-md shadow-purple-600/30'
                  : 'bg-slate-800/80 text-slate-300 border border-slate-700/80 hover:bg-slate-800'
              ]"
              class="py-2 rounded-xl transition text-xs flex items-center justify-center space-x-1.5"
            >
              <span>👤</span>
              <span>alice (用户)</span>
            </button>
            <button
              @click="appUser = 'admin'"
              :class="[
                appUser === 'admin'
                  ? 'bg-purple-600 text-white font-bold shadow-md shadow-purple-600/30'
                  : 'bg-slate-800/80 text-slate-300 border border-slate-700/80 hover:bg-slate-800'
              ]"
              class="py-2 rounded-xl transition text-xs flex items-center justify-center space-x-1.5"
            >
              <span>🛡️</span>
              <span>admin (管理员)</span>
            </button>
          </div>
        </div>

        <!-- 扫描 Ticket 输入区 -->
        <div class="space-y-2">
          <label class="block text-xs font-medium text-slate-300">已捕获的扫码票据凭据 (Ticket)</label>
          <textarea
            v-model="ticket"
            rows="3"
            placeholder="粘贴或读取 attemptId.token 格式密文"
            class="w-full p-3 bg-slate-900 border border-slate-800 rounded-xl text-xs font-mono text-purple-300 placeholder-slate-600 focus:outline-none focus:ring-1 focus:ring-purple-500 resize-none"
          ></textarea>
          <div class="flex justify-end">
            <button @click="ticket = ''" class="text-[11px] text-slate-500 hover:text-slate-300">
              清空
            </button>
          </div>
        </div>

        <!-- 确认授权动作按钮 -->
        <button
          @click="handleApprove"
          :disabled="!ticket || loading"
          class="w-full py-3 bg-gradient-to-r from-purple-600 to-indigo-600 hover:from-purple-500 hover:to-indigo-500 text-white font-bold rounded-2xl shadow-lg shadow-purple-600/25 transition disabled:opacity-40 flex items-center justify-center space-x-2 text-sm"
        >
          <span v-if="loading" class="animate-spin">🔄</span>
          <span>👆 确认扫码并授权登录 (Approve)</span>
        </button>

        <!-- 授权结果卡片 (iOS 通知风格) -->
        <div
          v-if="result"
          class="p-3.5 rounded-2xl border text-xs space-y-1 backdrop-blur-md animate-subtle-pulse"
          :class="[
            result.success
              ? 'bg-emerald-950/40 border-emerald-500/40 text-emerald-300'
              : 'bg-rose-950/40 border-rose-500/40 text-rose-300'
          ]"
        >
          <div class="font-bold text-sm">{{ result.title }}</div>
          <p class="leading-relaxed text-[11px]">{{ result.message }}</p>
        </div>

        <div class="p-3 rounded-xl bg-slate-900/60 border border-slate-800/80 text-[11px] text-slate-400 space-y-1">
          <p class="font-semibold text-slate-300">🧪 跨账号隔离防护验证：</p>
          <p>
            若当前登录账号为 alice，但移动端使用 admin 身份进行扫码授权，后端将触发身份校验拦截并返回 403 Forbidden（扫码人与登录人身份不符）。
          </p>
        </div>

        <!-- 手机底部 Home Indicator 触控条 -->
        <div class="pt-3 pb-1 flex justify-center">
          <div class="w-32 h-1 bg-slate-600/80 rounded-full"></div>
        </div>

      </div>
    </div>
  </div>
</template>
