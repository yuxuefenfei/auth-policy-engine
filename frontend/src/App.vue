<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import {
  createAttempt,
  getAttempt,
  completeAttempt,
  getMe,
  getActivePolicy,
  apiLogs,
} from './api/auth';
import type { AttemptView, PolicySnapshot, SessionToken, PolicyNode } from './types/auth';
import PolicyNodeViewer from './components/PolicyNodeViewer.vue';
import FactorPassword from './components/FactorPassword.vue';
import FactorSms from './components/FactorSms.vue';
import FactorQr from './components/FactorQr.vue';
import PolicyStudio from './components/PolicyStudio.vue';
import MobileAppSimulator from './components/MobileAppSimulator.vue';
import AuditLogViewer from './components/AuditLogViewer.vue';

const activeTab = ref<'login' | 'policy' | 'app' | 'logs'>('login');
const activePolicy = ref<PolicySnapshot | null>(null);
const currentAttempt = ref<AttemptView | null>(null);
const currentAttemptAst = ref<PolicyNode | null>(null);
const completedSession = ref<SessionToken | null>(null);
const sessionUser = ref<{ username: string } | null>(null);

const loginUsername = ref('alice');
const loadingCreate = ref(false);
const loadingRefresh = ref(false);
const loadingComplete = ref(false);
const loadingReplay = ref(false);
const loadingCookie = ref(false);
const testResults = ref({ replay: '', cookie: '' });

const attemptCountdownText = computed(() => {
  if (!currentAttempt.value || !currentAttempt.value.expiresAt) return '--:--';
  return new Date(currentAttempt.value.expiresAt).toLocaleTimeString();
});

const policyDecisionExplanation = computed(() => {
  if (!currentAttempt.value) return '';
  if (currentAttempt.value.status === 'SUCCEEDED') return '所有必要因子均已满足，策略决策状态为 SATISFIED。';
  if (currentAttempt.value.status === 'FAILED') return '由于失败次数超限或分支全灭，策略已判定为 IMPOSSIBLE。';
  const avail = currentAttempt.value.available.map((a) => a.nodeId).join(', ');
  return `当前待完成节点，根据 AST 操作符规则当前开放执行的操作为: [${avail || '无'}]。`;
});

async function loadActive() {
  try {
    activePolicy.value = await getActivePolicy();
  } catch (err) {
    console.error('加载生效策略失败', err);
  }
}

async function handleCreateAttempt() {
  loadingCreate.value = true;
  try {
    const attempt = await createAttempt(loginUsername.value);
    currentAttempt.value = attempt;
    if (activePolicy.value && activePolicy.value.version === attempt.policyVersion) {
      currentAttemptAst.value = activePolicy.value.root;
    } else {
      await loadActive();
      currentAttemptAst.value = activePolicy.value?.root || null;
    }
  } catch (err: any) {
    alert('创建登录流程失败: ' + (err.data?.error || err.message));
  } finally {
    loadingCreate.value = false;
  }
}

async function handleRefreshStatus() {
  if (!currentAttempt.value) return;
  loadingRefresh.value = true;
  try {
    currentAttempt.value = await getAttempt(currentAttempt.value.attemptId);
  } catch (err) {
    console.error('刷新状态失败', err);
  } finally {
    loadingRefresh.value = false;
  }
}

async function handleCompleteLogin() {
  if (!currentAttempt.value) return;
  loadingComplete.value = true;
  try {
    const token = await completeAttempt(currentAttempt.value.attemptId);
    completedSession.value = token;
    sessionUser.value = await getMe(token.accessToken);
  } catch (err: any) {
    alert('完成登录签发令牌失败: ' + (err.data?.error || err.message));
  } finally {
    loadingComplete.value = false;
  }
}

async function testReplayAttack() {
  if (!currentAttempt.value) return;
  loadingReplay.value = true;
  testResults.value.replay = '正在请求...';
  try {
    await completeAttempt(currentAttempt.value.attemptId);
    testResults.value.replay = '异常: 竟然重复成功了(未被拦截)';
  } catch (err: any) {
    testResults.value.replay = `成功拦截防重放! HTTP ${err.status}: ${err.data?.error || err.message}`;
  } finally {
    loadingReplay.value = false;
  }
}

async function testTamperedCookie() {
  if (!currentAttempt.value) return;
  loadingCookie.value = true;
  testResults.value.cookie = '正在测试...';
  try {
    const res = await fetch(`/api/login/attempts/${currentAttempt.value.attemptId}`, {
      headers: { Cookie: 'AUTH_BIND=INVALID_FAKE_TOKEN_43_CHARS_LONG_TEST_A' },
    });
    const data = await res.json();
    testResults.value.cookie = `成功拦截 Cookie 伪造! HTTP ${res.status}: ${data.error || '跨浏览器绑定拒绝'}`;
  } catch (err: any) {
    testResults.value.cookie = `拦截成功: ${err.message}`;
  } finally {
    loadingCookie.value = false;
  }
}

function handleAttemptUpdate(updated: AttemptView) {
  currentAttempt.value = updated;
}

function hasFactor(type: string): boolean {
  if (!currentAttempt.value || !currentAttemptAst.value) return false;
  return Object.keys(currentAttempt.value.progress).some((nodeId) =>
    checkFactorType(currentAttemptAst.value, nodeId, type)
  );
}

function checkFactorType(node: PolicyNode | null, nodeId: string, targetType: string): boolean {
  if (!node) return false;
  if (node.op === 'FACTOR') return node.id === nodeId && node.factor === targetType;
  if (node.children) return node.children.some((c) => checkFactorType(c, nodeId, targetType));
  return false;
}

function getFactorNodeId(type: string): string {
  if (!currentAttempt.value || !currentAttemptAst.value) return '';
  for (const nodeId of Object.keys(currentAttempt.value.progress)) {
    if (checkFactorType(currentAttemptAst.value, nodeId, type)) return nodeId;
  }
  return '';
}

function getFactorProgress(type: string) {
  const nodeId = getFactorNodeId(type);
  if (!nodeId || !currentAttempt.value) return 'PENDING';
  return currentAttempt.value.progress[nodeId] || 'PENDING';
}

function isFactorAvailable(type: string): boolean {
  const nodeId = getFactorNodeId(type);
  if (!nodeId || !currentAttempt.value) return false;
  return currentAttempt.value.available.some((a) => a.nodeId === nodeId);
}

function fillUser(name: string) {
  loginUsername.value = name;
}

function resetAttempt() {
  currentAttempt.value = null;
  completedSession.value = null;
  sessionUser.value = null;
  testResults.value = { replay: '', cookie: '' };
}

onMounted(loadActive);
</script>

<template>
  <div class="flex flex-col min-h-screen">
    <!-- 顶部导航栏 -->
    <header class="bg-white border-b border-gray-200 sticky top-0 z-40 shadow-sm">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex justify-between items-center h-16">
          <div class="flex items-center space-x-3">
            <div class="w-10 h-10 rounded-xl bg-gradient-to-tr from-blue-600 to-indigo-600 flex items-center justify-center text-white shadow-md">
              <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z"/></svg>
            </div>
            <div>
              <h1 class="text-lg font-bold text-gray-900 leading-tight">Auth Policy Engine</h1>
              <p class="text-xs text-gray-500">动态多因子认证策略引擎 · 流程验证台</p>
            </div>
          </div>

          <nav class="flex space-x-1 sm:space-x-2">
            <button
              @click="activeTab = 'login'"
              :class="[activeTab === 'login' ? 'bg-blue-50 text-blue-700 font-semibold border-b-2 border-blue-600' : 'text-gray-600 hover:text-gray-900 hover:bg-gray-100']"
              class="px-3 py-2 rounded-lg text-sm transition flex items-center space-x-1.5"
            >
              <span>🔑</span>
              <span>登录流程验证</span>
            </button>
            <button
              @click="activeTab = 'policy'"
              :class="[activeTab === 'policy' ? 'bg-blue-50 text-blue-700 font-semibold border-b-2 border-blue-600' : 'text-gray-600 hover:text-gray-900 hover:bg-gray-100']"
              class="px-3 py-2 rounded-lg text-sm transition flex items-center space-x-1.5"
            >
              <span>⚙️</span>
              <span>策略编排与热更</span>
            </button>
            <button
              @click="activeTab = 'app'"
              :class="[activeTab === 'app' ? 'bg-blue-50 text-blue-700 font-semibold border-b-2 border-blue-600' : 'text-gray-600 hover:text-gray-900 hover:bg-gray-100']"
              class="px-3 py-2 rounded-lg text-sm transition flex items-center space-x-1.5"
            >
              <span>📱</span>
              <span>APP 扫码模拟端</span>
            </button>
            <button
              @click="activeTab = 'logs'"
              :class="[activeTab === 'logs' ? 'bg-blue-50 text-blue-700 font-semibold border-b-2 border-blue-600' : 'text-gray-600 hover:text-gray-900 hover:bg-gray-100']"
              class="px-3 py-2 rounded-lg text-sm transition flex items-center space-x-1.5"
            >
              <span>📡</span>
              <span>API 审计日志</span>
              <span v-if="apiLogs.length" class="ml-1 bg-gray-200 text-gray-700 text-xs px-1.5 py-0.5 rounded-full">{{ apiLogs.length }}</span>
            </button>
          </nav>

          <div class="flex items-center space-x-3 text-xs">
            <span class="inline-flex items-center px-2.5 py-1 rounded-full font-medium bg-green-100 text-green-800">
              <span class="w-1.5 h-1.5 mr-1.5 bg-green-500 rounded-full"></span>
              服务在线
            </span>
            <span v-if="activePolicy" class="text-gray-500 hidden md:inline">
              生效策略: <strong class="text-gray-700">{{ activePolicy.code }} v{{ activePolicy.version }}</strong>
            </span>
          </div>
        </div>
      </div>
    </header>

    <!-- 演示账号快捷提示条 -->
    <div class="bg-gradient-to-r from-blue-50 via-indigo-50 to-blue-50 border-b border-blue-100 px-4 py-2 text-xs text-blue-900">
      <div class="max-w-7xl mx-auto flex flex-wrap items-center justify-between gap-2">
        <div class="flex items-center space-x-2">
          <span class="font-bold text-blue-800">💡 本地演示预置账号：</span>
          <button @click="fillUser('alice')" class="px-2 py-0.5 bg-white border border-blue-200 rounded hover:bg-blue-100 transition font-mono">
            alice / Demo@123456 (13800000000 · 普通用户)
          </button>
          <button @click="fillUser('admin')" class="px-2 py-0.5 bg-white border border-blue-200 rounded hover:bg-blue-100 transition font-mono">
            admin / Admin@123456 (13900000000 · 管理员)
          </button>
        </div>
        <div class="flex items-center space-x-2">
          <span class="text-blue-700">短信控制台模拟:</span>
          <span class="font-semibold text-green-700">自动检测已就绪</span>
        </div>
      </div>
    </div>

    <!-- 主体区域 -->
    <main class="flex-1 max-w-7xl w-full mx-auto p-4 sm:p-6 lg:p-8">
      <!-- TAB 1: 登录验证台 -->
      <div v-show="activeTab === 'login'" class="space-y-6">
        <div v-if="!currentAttempt" class="bg-white rounded-2xl p-6 sm:p-8 shadow-sm border border-gray-200 max-w-2xl mx-auto">
          <div class="text-center mb-6">
            <div class="w-14 h-14 bg-blue-100 text-blue-600 rounded-2xl flex items-center justify-center mx-auto mb-3 shadow-inner">
              <svg class="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 16l-4-4m0 0l4-4m-4 4h14m-5 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h7a3 3 0 013 3v1"/></svg>
            </div>
            <h2 class="text-xl font-bold text-gray-900">发起动态认证流程</h2>
            <p class="text-sm text-gray-500 mt-1">提交用户名，引擎将读取当前生效的策略 AST 并生成不可变的流程快照</p>
          </div>

          <form @submit.prevent="handleCreateAttempt" class="space-y-4">
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">登录用户名</label>
              <input
                v-model="loginUsername"
                type="text"
                required
                placeholder="例如: alice 或 admin"
                class="w-full px-4 py-2.5 border border-gray-300 rounded-xl focus:ring-2 focus:ring-blue-500 focus:border-blue-500 text-sm font-mono"
              />
            </div>

            <div class="bg-gray-50 rounded-xl p-3 text-xs text-gray-600 space-y-1 border border-gray-200">
              <div class="flex justify-between">
                <span>当前生效策略代码:</span>
                <span class="font-mono font-semibold">{{ activePolicy?.code || 'WEB_LOGIN' }}</span>
              </div>
              <div class="flex justify-between">
                <span>策略版本:</span>
                <span class="font-mono font-semibold">Version {{ activePolicy?.version || 1 }}</span>
              </div>
              <div class="flex justify-between">
                <span>浏览器安全绑定:</span>
                <span class="text-indigo-600 font-semibold">AUTH_BIND (HttpOnly/Strict Cookie)</span>
              </div>
            </div>

            <button
              type="submit"
              :disabled="loadingCreate"
              class="w-full py-3 px-4 bg-gradient-to-r from-blue-600 to-indigo-600 text-white rounded-xl font-semibold shadow hover:from-blue-700 hover:to-indigo-700 transition disabled:opacity-50 flex items-center justify-center space-x-2"
            >
              <span v-if="loadingCreate" class="animate-spin mr-2">🔄</span>
              <span>创建登录 Attempt</span>
            </button>
          </form>
        </div>

        <div v-else class="grid grid-cols-1 lg:grid-cols-12 gap-6">
          <!-- 左栏：策略树拓扑 -->
          <div class="lg:col-span-5 space-y-6">
            <div class="bg-white rounded-2xl p-5 shadow-sm border border-gray-200">
              <div class="flex items-center justify-between pb-3 border-b border-gray-100">
                <div class="flex items-center space-x-2">
                  <span class="text-blue-600 font-bold">🌳 策略树拓扑</span>
                  <span class="text-xs bg-blue-100 text-blue-800 px-2 py-0.5 rounded-full font-mono font-semibold">
                    v{{ currentAttempt.policyVersion }} 快照
                  </span>
                </div>
                <button @click="resetAttempt" class="text-xs text-gray-500 hover:text-red-600 transition flex items-center space-x-1">
                  <span>❌ 放弃本次流程</span>
                </button>
              </div>

              <!-- Attempt 元信息 -->
              <div class="my-3 p-3 bg-gray-50 rounded-xl text-xs space-y-1.5 border border-gray-100">
                <div class="flex justify-between items-center">
                  <span class="text-gray-500">Attempt ID:</span>
                  <span class="font-mono text-gray-800 truncate max-w-[200px]" :title="currentAttempt.attemptId">
                    {{ currentAttempt.attemptId }}
                  </span>
                </div>
                <div class="flex justify-between items-center">
                  <span class="text-gray-500">状态:</span>
                  <span class="font-bold px-2 py-0.5 rounded text-xs font-mono bg-blue-100 text-blue-800">
                    {{ currentAttempt.status }}
                  </span>
                </div>
                <div class="flex justify-between items-center">
                  <span class="text-gray-500">过期倒计时:</span>
                  <span class="font-mono text-indigo-600 font-semibold">{{ attemptCountdownText }}</span>
                </div>
                <div class="flex justify-between items-center">
                  <span class="text-gray-500">条件已全部满足:</span>
                  <span :class="currentAttempt.canComplete ? 'text-green-600 font-bold' : 'text-gray-400 font-semibold'">
                    {{ currentAttempt.canComplete ? '✅ YES (可签发令牌)' : '⏳ NO (仍需满足条件)' }}
                  </span>
                </div>
              </div>

              <!-- 拓扑节点可视化 -->
              <div class="mt-4 space-y-3">
                <h4 class="text-xs font-semibold text-gray-500 uppercase tracking-wider">执行状态树</h4>
                <div class="bg-gray-50 p-4 rounded-xl border border-gray-200">
                  <PolicyNodeViewer
                    :node="currentAttemptAst"
                    :progress="currentAttempt.progress"
                    :available="currentAttempt.available"
                  />
                </div>
              </div>

              <!-- 决策解读 -->
              <div class="mt-4 p-3 bg-blue-50/60 rounded-xl border border-blue-100 text-xs text-blue-900 leading-relaxed">
                <span class="font-bold">🔍 引擎决策解析：</span>
                <p class="mt-1 text-blue-800">{{ policyDecisionExplanation }}</p>
              </div>
            </div>
          </div>

          <!-- 右栏：因子交互区 -->
          <div class="lg:col-span-7 space-y-6">
            <div class="bg-white rounded-2xl p-6 shadow-sm border border-gray-200 space-y-6">
              <div class="flex items-center justify-between border-b border-gray-100 pb-4">
                <div>
                  <h3 class="text-base font-bold text-gray-900">认证因子执行区</h3>
                  <p class="text-xs text-gray-500">
                    当前可用认证因子: {{ currentAttempt.available.map((a) => a.nodeId).join(', ') || '无' }}
                  </p>
                </div>
                <button
                  @click="handleRefreshStatus"
                  :disabled="loadingRefresh"
                  class="px-2.5 py-1 text-xs bg-gray-100 text-gray-700 hover:bg-gray-200 rounded-lg transition flex items-center space-x-1"
                >
                  <span :class="{ 'animate-spin': loadingRefresh }">🔄</span>
                  <span>刷新状态</span>
                </button>
              </div>

              <!-- 全部满足时显示完成按钮 -->
              <div
                v-if="currentAttempt.canComplete && currentAttempt.status === 'IN_PROGRESS'"
                class="bg-green-50 border border-green-200 rounded-2xl p-6 text-center space-y-3 shadow-sm"
              >
                <div class="w-12 h-12 bg-green-100 text-green-600 rounded-full flex items-center justify-center mx-auto text-xl">🎉</div>
                <h4 class="text-base font-bold text-green-900">恭喜！策略树全部条件已达成</h4>
                <p class="text-xs text-green-700 max-w-md mx-auto">认证决策已满足 SATISFIED 状态。请点击下方按钮原子消费 Attempt 并获取 Bearer 会话令牌。</p>
                <button
                  @click="handleCompleteLogin"
                  :disabled="loadingComplete"
                  class="px-6 py-3 bg-green-600 hover:bg-green-700 text-white font-bold rounded-xl shadow-lg hover:shadow-xl transition transform active:scale-95 disabled:opacity-50 flex items-center justify-center mx-auto space-x-2"
                >
                  <span v-if="loadingComplete" class="animate-spin">🔄</span>
                  <span>🚀 完成登录流程并签发会话令牌</span>
                </button>
              </div>

              <!-- 密码因子 -->
              <FactorPassword
                v-if="hasFactor('PASSWORD')"
                :attempt="currentAttempt"
                :node-id="getFactorNodeId('PASSWORD')"
                :is-available="isFactorAvailable('PASSWORD')"
                :progress="getFactorProgress('PASSWORD')"
                @updated="handleAttemptUpdate"
              />

              <!-- 短信验证码因子 -->
              <FactorSms
                v-if="hasFactor('SMS_OTP')"
                :attempt="currentAttempt"
                :node-id="getFactorNodeId('SMS_OTP')"
                :is-available="isFactorAvailable('SMS_OTP')"
                :progress="getFactorProgress('SMS_OTP')"
                :username="loginUsername"
                @updated="handleAttemptUpdate"
              />

              <!-- APP 扫码因子 -->
              <FactorQr
                v-if="hasFactor('APP_QR_APPROVAL')"
                :attempt="currentAttempt"
                :node-id="getFactorNodeId('APP_QR_APPROVAL')"
                :is-available="isFactorAvailable('APP_QR_APPROVAL')"
                :progress="getFactorProgress('APP_QR_APPROVAL')"
                :username="loginUsername"
                @updated="handleAttemptUpdate"
              />
            </div>

            <!-- 登录完成与安全实验室 -->
            <div v-if="completedSession" class="bg-white rounded-2xl p-6 shadow-sm border border-green-200 space-y-5">
              <div class="flex items-center justify-between pb-3 border-b border-green-100">
                <div class="flex items-center space-x-2">
                  <span class="w-7 h-7 bg-green-100 text-green-700 rounded-lg flex items-center justify-center font-bold">🛡️</span>
                  <h3 class="text-base font-bold text-green-900">会话签发与安全防御实验室</h3>
                </div>
                <span class="text-xs bg-green-100 text-green-800 font-bold px-2 py-0.5 rounded-full">AUTHENTICATED</span>
              </div>

              <div class="p-4 bg-gray-900 text-green-400 rounded-xl font-mono text-xs space-y-2">
                <div class="flex justify-between">
                  <span class="text-gray-400">Token Type:</span>
                  <span class="text-white">{{ completedSession.tokenType || 'Bearer' }}</span>
                </div>
                <div>
                  <span class="text-gray-400 block mb-1">Access Token:</span>
                  <div class="bg-gray-800 p-2 rounded break-all select-all text-green-300 font-bold">
                    {{ completedSession.accessToken }}
                  </div>
                </div>
                <div class="flex justify-between">
                  <span class="text-gray-400">Current User (/api/me):</span>
                  <span class="text-yellow-400 font-bold">{{ sessionUser?.username || '未获取' }}</span>
                </div>
              </div>

              <div class="space-y-2">
                <h4 class="text-xs font-bold text-gray-700 uppercase tracking-wider">安全边界实测 (Security Edge Cases):</h4>
                <div class="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
                  <div class="p-3 bg-red-50 border border-red-200 rounded-xl space-y-1.5">
                    <span class="font-bold text-red-900">🧪 测试防重放 (Replay Attack)</span>
                    <p class="text-red-700 text-[11px]">再次调用 /complete 消费同一 Attempt。预期返回 403 Forbidden。</p>
                    <button
                      @click="testReplayAttack"
                      :disabled="loadingReplay"
                      class="w-full py-1.5 bg-red-600 hover:bg-red-700 text-white rounded-lg font-semibold transition"
                    >
                      触发重复消费尝试
                    </button>
                    <p v-if="testResults.replay" class="font-mono text-[11px] p-1 bg-white rounded border border-red-200 text-red-800">
                      {{ testResults.replay }}
                    </p>
                  </div>

                  <div class="p-3 bg-indigo-50 border border-indigo-200 rounded-xl space-y-1.5">
                    <span class="font-bold text-indigo-900">🧪 测试伪造 Cookie 绑定</span>
                    <p class="text-indigo-700 text-[11px]">向后端查询 Attempt 状态但不带合法 AUTH_BIND 绑定。预期返回 403。</p>
                    <button
                      @click="testTamperedCookie"
                      :disabled="loadingCookie"
                      class="w-full py-1.5 bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg font-semibold transition"
                    >
                      伪造无效绑定凭证测试
                    </button>
                    <p v-if="testResults.cookie" class="font-mono text-[11px] p-1 bg-white rounded border border-indigo-200 text-indigo-800">
                      {{ testResults.cookie }}
                    </p>
                  </div>
                </div>
              </div>

              <div class="pt-2 flex justify-end">
                <button
                  @click="resetAttempt"
                  class="px-4 py-2 bg-gray-100 hover:bg-gray-200 text-gray-700 text-xs font-semibold rounded-lg transition"
                >
                  退出登录 / 测试下一个流程
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- TAB 2: 策略管理与热更新 -->
      <div v-show="activeTab === 'policy'">
        <PolicyStudio />
      </div>

      <!-- TAB 3: APP 扫码模拟端 -->
      <div v-show="activeTab === 'app'">
        <MobileAppSimulator @approved="handleAttemptUpdate" />
      </div>

      <!-- TAB 4: API 审计日志 -->
      <div v-show="activeTab === 'logs'">
        <AuditLogViewer />
      </div>
    </main>

    <footer class="bg-white border-t border-gray-200 py-3 px-4 text-center text-xs text-gray-500">
      Spring Boot 3.5.16 · Spring Security 6 · 动态认证策略引擎 MVP · 支持 AST 组合求值、CAS 原子状态机与快照隔离
    </footer>
  </div>
</template>
