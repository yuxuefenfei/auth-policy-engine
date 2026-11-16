<script setup lang="ts">
import { apiLogs } from '../api/auth';

function clearLogs() {
  apiLogs.value = [];
}
</script>

<template>
  <div class="bg-white rounded-2xl p-6 shadow-sm border border-gray-200 space-y-4">
    <div class="flex items-center justify-between pb-3 border-b border-gray-100">
      <div>
        <h3 class="text-sm font-bold text-gray-900">API 请求与状态机转移审计记录</h3>
        <p class="text-xs text-gray-500">跟踪前端发出的每一条请求及响应体、延迟和状态码</p>
      </div>
      <button
        @click="clearLogs"
        class="px-3 py-1 bg-gray-100 hover:bg-gray-200 text-gray-700 rounded-lg text-xs font-semibold transition"
      >
        清空日志
      </button>
    </div>

    <div v-if="!apiLogs.length" class="text-center py-12 text-gray-400 text-sm">
      暂无调用记录。进行登录操作或切换策略时将自动记录。
    </div>

    <div v-else class="space-y-3 font-mono text-xs">
      <div v-for="(log, idx) in apiLogs" :key="idx" class="p-3 bg-gray-50 rounded-xl border border-gray-200 space-y-2">
        <div class="flex items-center justify-between">
          <div class="flex items-center space-x-2">
            <span
              :class="typeof log.status === 'number' && log.status >= 200 && log.status < 300 ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'"
              class="px-2 py-0.5 rounded font-bold"
            >
              {{ log.method }} {{ log.status }}
            </span>
            <span class="text-gray-800 font-semibold">{{ log.url }}</span>
          </div>
          <span class="text-gray-400 text-[11px]">{{ log.time }}</span>
        </div>

        <div v-if="log.body" class="text-[11px] text-gray-600 bg-white p-2 rounded border border-gray-100">
          <span class="text-gray-400 block mb-0.5">Payload:</span>
          <pre class="overflow-x-auto">{{ log.body }}</pre>
        </div>

        <div class="text-[11px] bg-gray-900 text-green-400 p-2 rounded max-h-36 overflow-y-auto">
          <span class="text-gray-400 block mb-0.5">Response:</span>
          <pre class="overflow-x-auto">{{ JSON.stringify(log.response, null, 2) }}</pre>
        </div>
      </div>
    </div>
  </div>
</template>
