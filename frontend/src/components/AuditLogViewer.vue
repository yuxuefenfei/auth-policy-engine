<script setup lang="ts">
import { ref, computed } from 'vue';
import { apiLogs } from '../api/auth';

const filterMethod = ref('ALL');

const filteredLogs = computed(() => {
  if (filterMethod.value === 'ALL') return apiLogs.value;
  return apiLogs.value.filter((l) => l.method === filterMethod.value);
});

function clearLogs() {
  apiLogs.value = [];
}
</script>

<template>
  <div class="glass-panel rounded-2xl p-6 border border-slate-800 space-y-4 shadow-xl">
    <div class="flex flex-wrap items-center justify-between gap-3 pb-3 border-b border-slate-800">
      <div>
        <h3 class="text-sm font-bold text-slate-100 flex items-center space-x-2">
          <span>📡 API 交互与 CAS 状态转移审计追踪器</span>
          <span class="text-[10px] px-2 py-0.5 rounded-full bg-indigo-500/20 text-indigo-300 font-mono">
            {{ filteredLogs.length }} 笔记录
          </span>
        </h3>
        <p class="text-xs text-slate-400">实时监听捕获前端向后端发出的每一笔网络请求、Payload、状态码与响应体</p>
      </div>

      <div class="flex items-center space-x-2 text-xs">
        <select
          v-model="filterMethod"
          class="bg-slate-900 border border-slate-800 text-slate-300 px-2.5 py-1.5 rounded-xl font-mono focus:outline-none focus:ring-1 focus:ring-indigo-500"
        >
          <option value="ALL">全部请求 (ALL)</option>
          <option value="POST">POST 状态变更</option>
          <option value="GET">GET 状态轮询</option>
        </select>
        <button
          @click="clearLogs"
          class="px-3 py-1.5 bg-slate-800 hover:bg-slate-700 text-slate-300 border border-slate-700 rounded-xl transition font-medium"
        >
          清空追踪日志
        </button>
      </div>
    </div>

    <div v-if="!filteredLogs.length" class="text-center py-16 text-slate-500 text-xs font-mono">
      暂无 API 调用记录。在验证台执行登录或策略热更时将自动实时捕获。
    </div>

    <div v-else class="space-y-3 font-mono text-xs max-h-[600px] overflow-y-auto pr-1">
      <div
        v-for="(log, idx) in filteredLogs"
        :key="idx"
        class="p-4 rounded-xl border border-slate-800/80 bg-slate-900/60 hover:border-slate-700 transition space-y-2.5 backdrop-blur-sm"
      >
        <div class="flex items-center justify-between">
          <div class="flex items-center space-x-2.5">
            <span
              class="px-2 py-0.5 rounded-md font-bold text-[10px] border"
              :class="[
                typeof log.status === 'number' && log.status >= 200 && log.status < 300
                  ? 'bg-emerald-500/20 text-emerald-300 border-emerald-500/30'
                  : 'bg-rose-500/20 text-rose-300 border-rose-500/30'
              ]"
            >
              {{ log.method }} {{ log.status }}
            </span>
            <span class="text-slate-200 font-semibold break-all">{{ log.url }}</span>
          </div>
          <span class="text-slate-500 text-[11px]">{{ log.time }}</span>
        </div>

        <div v-if="log.body" class="text-[11px] bg-slate-950 p-2.5 rounded-lg border border-slate-800/80 text-slate-300">
          <span class="text-slate-500 block text-[10px] mb-1 font-semibold">请求报文 Payload:</span>
          <pre class="overflow-x-auto text-indigo-300 leading-tight">{{ log.body }}</pre>
        </div>

        <div class="text-[11px] bg-slate-950 p-2.5 rounded-lg border border-slate-800/80 text-slate-300">
          <span class="text-slate-500 block text-[10px] mb-1 font-semibold">响应 JSON Response:</span>
          <pre class="overflow-x-auto text-emerald-400 max-h-36 overflow-y-auto leading-tight">{{
            JSON.stringify(log.response, null, 2)
          }}</pre>
        </div>
      </div>
    </div>
  </div>
</template>
