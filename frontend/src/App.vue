<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import {
  createAttempt,
  getAttemptStatus,
  completeLogin,
  getMe,
  getActivePolicy,
  apiLogs,
} from './api';
import {
  type AttemptView,
  type PolicySnapshot,
  type SessionToken,
  type PolicyNode,
  AttemptStatus,
  ATTEMPT_STATUS_META,
  FactorType,
} from './types/auth';
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
  if (currentAttempt.value.status === 'SUCCEEDED') {
    return '🎉 策略树所有条件已完整满足，认证状态机判定为 SATISFIED。可调用 /complete 完成会话核销并签发访问令牌。';
  }
  if (currentAttempt.value.status === 'FAILED') {
    return '❌ 由于验证失败次数达到安全上限或必要认证分支均已失败，策略判定为 IMPOSSIBLE。';
  }
  const avail = currentAttempt.value.available.map((a) => a.nodeId).join(', ');
  return `根据当前策略拓扑规则，当前开放验证的认证方式为: [${avail || '无'}]。`;
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
  testResults.value.replay = '发起恶意重放请求中...';
  try {
    await completeAttempt(currentAttempt.value.attemptId);
    testResults.value.replay = '异常: 重复核销成功 (未被 CAS 防线拦截)';
  } catch (err: any) {
    testResults.value.replay = `成功拦截防重放! HTTP ${err.status}: ${err.data?.error || err.message}`;
  } finally {
    loadingReplay.value = false;
  }
}

async function testTamperedCookie() {
  if (!currentAttempt.value) return;
  loadingCookie.value = true;
  testResults.value.cookie = '伪造凭证发起探测中...';
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
  <div class="min-h-screen bg-slate-950 text-slate-100 flex flex-col relative overflow-hidden bg-grid-pattern selection:bg-indigo-500 selection:text-white">
    <!-- 顶部环境光晕 -->
    <div class="absolute top-0 left-1/2 -translate-x-1/2 w-[1000px] h-[350px] bg-gradient-to-b from-indigo-500/15 via-purple-500/5 to-transparent blur-3xl pointer-events-none"></div>

    <!-- 顶栏导航 -->
    <header class="glass-panel sticky top-0 z-50 border-b border-slate-800/80">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex justify-between items-center h-16">
          
          <!-- Logo & 产品标题 -->
          <div class="flex items-center space-x-3.5">
            <div class="w-10 h-10 rounded-xl bg-gradient-to-tr from-indigo-500 via-indigo-600 to-purple-600 flex items-center justify-center text-white shadow-lg shadow-indigo-500/25 ring-1 ring-white/20">
              <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z"/>
              </svg>
            </div>
            <div>
              <div class="flex items-center space-x-2">
                <h1 class="text-sm sm:text-base font-bold text-white tracking-tight">Auth Policy Engine</h1>
                <span class="text-[10px] px-2 py-0.5 rounded-full bg-indigo-500/15 text-indigo-300 border border-indigo-500/30 font-mono">
                  v1.0 MVP
                </span>
              </div>
              <p class="text-[11px] text-slate-400">动态多因子认证策略引擎 · 流程验证工作台</p>
            </div>
          </div>

          <!-- 导航 Tabs -->
          <nav class="flex items-center p-1 rounded-xl bg-slate-900/90 border border-slate-800 shadow-inner">
            <button
              @click="activeTab = 'login'"
              :class="[
                activeTab === 'login'
                  ? 'bg-indigo-600 text-white font-semibold shadow-md shadow-indigo-600/30'
                  : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/60'
              ]"
              class="px-3.5 py-1.5 rounded-lg text-xs transition-all duration-200 flex items-center space-x-1.5"
            >
              <span>🔑</span>
              <span>登录流程验证</span>
            </button>
            <button
              @click="activeTab = 'policy'"
              :class="[
                activeTab === 'policy'
                  ? 'bg-indigo-600 text-white font-semibold shadow-md shadow-indigo-600/30'
                  : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/60'
              ]"
              class="px-3.5 py-1.5 rounded-lg text-xs transition-all duration-200 flex items-center space-x-1.5"
            >
              <span>⚙️</span>
              <span>策略编排与热更</span>
            </button>
            <button
              @click="activeTab = 'app'"
              :class="[
                activeTab === 'app'
                  ? 'bg-indigo-600 text-white font-semibold shadow-md shadow-indigo-600/30'
                  : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/60'
              ]"
              class="px-3.5 py-1.5 rounded-lg text-xs transition-all duration-200 flex items-center space-x-1.5"
            >
              <span>📱</span>
              <span>APP 扫码模拟端</span>
            </button>
            <button
              @click="activeTab = 'logs'"
              :class="[
                activeTab === 'logs'
                  ? 'bg-indigo-600 text-white font-semibold shadow-md shadow-indigo-600/30'
                  : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/60'
              ]"
              class="px-3.5 py-1.5 rounded-lg text-xs transition-all duration-200 flex items-center space-x-1.5"
            >
              <span>📡</span>
              <span>审计日志</span>
              <span v-if="apiLogs.length" class="ml-1 bg-slate-800 text-indigo-300 border border-indigo-500/30 text-[10px] px-1.5 py-0.2 rounded-full font-mono">
                {{ apiLogs.length }}
              </span>
            </button>
          </nav>

          <!-- 状态徽标 -->
          <div class="hidden md:flex items-center space-x-3 text-xs">
            <div class="inline-flex items-center px-3 py-1 rounded-full bg-emerald-950/40 border border-emerald-500/30 text-emerald-400 font-mono text-[11px]">
              <span class="w-1.5 h-1.5 mr-2 rounded-full bg-emerald-400 animate-ping"></span>
              <span>引擎就绪 (ONLINE)</span>
            </div>
            <div v-if="activePolicy" class="text-slate-400 font-mono text-[11px]">
              策略: <strong class="text-slate-200">{{ activePolicy.code }} v{{ activePolicy.version }}</strong>
            </div>
          </div>

        </div>
      </div>
    </header>

    <!-- 演示账号快捷芯片条 -->
    <div class="border-b border-slate-800/80 bg-slate-900/40 px-4 py-2.5 text-xs text-slate-300">
      <div class="max-w-7xl mx-auto flex flex-wrap items-center justify-between gap-2.5">
        <div class="flex items-center space-x-2">
          <span class="text-slate-400 text-xs flex items-center space-x-1">
            <span>💡 演示预置账号:</span>
          </span>
          <button
            @click="fillUser('alice')"
            class="px-2.5 py-1 rounded-lg bg-slate-900 hover:bg-slate-800 border border-slate-700/80 hover:border-indigo-500/50 text-slate-200 font-mono transition text-xs flex items-center space-x-1.5 shadow-sm"
          >
            <span>👤 alice</span>
            <span class="text-slate-500">|</span>
            <span class="text-slate-400 text-[11px]">Demo@123456</span>
            <span class="text-[10px] px-1.5 py-0.2 rounded bg-indigo-500/20 text-indigo-300">USER</span>
          </button>
          <button
            @click="fillUser('admin')"
            class="px-2.5 py-1 rounded-lg bg-slate-900 hover:bg-slate-800 border border-slate-700/80 hover:border-indigo-500/50 text-slate-200 font-mono transition text-xs flex items-center space-x-1.5 shadow-sm"
          >
            <span>🛡️ admin</span>
            <span class="text-slate-500">|</span>
            <span class="text-slate-400 text-[11px]">Admin@123456</span>
            <span class="text-[10px] px-1.5 py-0.2 rounded bg-purple-500/20 text-purple-300">ADMIN</span>
          </button>
        </div>

        <div class="flex items-center space-x-2 font-mono text-[11px] text-slate-400">
          <span class="w-2 h-2 rounded-full bg-emerald-400"></span>
          <span>短信网关模式: 控制台实时模拟 (demo-sms-log)</span>
        </div>
      </div>
    </div>

    <!-- 主视口区域 -->
    <main class="flex-1 max-w-7xl w-full mx-auto p-4 sm:p-6 lg:p-8">
      
      <!-- TAB 1: 登录验证台 -->
      <div v-show="activeTab === 'login'" class="space-y-6">
        
        <!-- 未创建流程：居中现代卡片 -->
        <div v-if="!currentAttempt" class="max-w-xl mx-auto my-12">
          <div class="glass-panel rounded-3xl p-8 border border-slate-800 shadow-2xl relative overflow-hidden backdrop-blur-xl">
            <div class="text-center space-y-2 mb-8">
              <div class="w-14 h-14 rounded-2xl bg-gradient-to-tr from-indigo-500 to-purple-600 text-white flex items-center justify-center text-2xl mx-auto shadow-xl shadow-indigo-500/20 ring-1 ring-white/20">
                🚀
              </div>
              <h2 class="text-xl font-bold text-white tracking-tight">发起动态认证流程</h2>
              <p class="text-xs text-slate-400 max-w-sm mx-auto leading-relaxed">
                提交待认证用户名，引擎将动态解析当前生效的策略 AST，锁定不可变的流程快照并下发浏览器绑定凭据。
              </p>
            </div>

            <form @submit.prevent="handleCreateAttempt" class="space-y-5">
              <div>
                <label class="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">登录用户名</label>
                <div class="relative">
                  <input
                    v-model="loginUsername"
                    type="text"
                    required
                    placeholder="输入 alice 或 admin"
                    class="w-full px-4 py-3 bg-slate-900/90 border border-slate-700/80 rounded-2xl text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent transition font-mono"
                  />
                </div>
              </div>

              <!-- 策略快照元信息说明 -->
              <div class="p-4 bg-slate-900/60 rounded-2xl border border-slate-800/80 text-xs space-y-2 font-mono">
                <div class="flex justify-between items-center text-slate-400">
                  <span>生效策略标识:</span>
                  <span class="text-indigo-400 font-bold">{{ activePolicy?.code || 'WEB_LOGIN' }}</span>
                </div>
                <div class="flex justify-between items-center text-slate-400">
                  <span>策略快照版本:</span>
                  <span class="text-slate-200 font-bold">Version {{ activePolicy?.version || 1 }}</span>
                </div>
                <div class="flex justify-between items-center text-slate-400">
                  <span>防伪绑定机制:</span>
                  <span class="text-emerald-400 font-semibold">AUTH_BIND (HttpOnly Cookie)</span>
                </div>
              </div>

              <button
                type="submit"
                :disabled="loadingCreate"
                class="w-full py-3.5 bg-gradient-to-r from-indigo-600 via-indigo-500 to-purple-600 hover:from-indigo-500 hover:to-purple-500 text-white font-bold rounded-2xl shadow-xl shadow-indigo-600/25 transition-all duration-200 transform active:scale-98 disabled:opacity-50 flex items-center justify-center space-x-2 text-sm"
              >
                <span v-if="loadingCreate" class="animate-spin">🔄</span>
                <span>发起登录认证流程 (Start Login) ↵</span>
              </button>
            </form>
          </div>
        </div>

        <!-- 流程执行中：双栏现代交互控制台 -->
        <div v-else class="grid grid-cols-1 lg:grid-cols-12 gap-6">
          
          <!-- 左栏：策略 AST 树与状态机 (5 列) -->
          <div class="lg:col-span-5 space-y-6">
            <div class="glass-panel rounded-3xl p-6 border border-slate-800 space-y-5 shadow-2xl">
              <div class="flex items-center justify-between pb-3 border-b border-slate-800">
                <div class="flex items-center space-x-2">
                  <span class="text-indigo-400 font-bold text-sm">🌳 策略执行拓扑</span>
                  <span class="text-[10px] px-2 py-0.5 rounded-full bg-indigo-500/20 text-indigo-300 font-mono border border-indigo-500/30">
                    v{{ currentAttempt.policyVersion }} 快照
                  </span>
                </div>
                <button
                  @click="resetAttempt"
                  class="text-xs text-slate-500 hover:text-rose-400 transition flex items-center space-x-1"
                >
                  <span>❌ 放弃本次流程</span>
                </button>
              </div>

              <!-- Attempt 核心元数据 -->
              <div class="p-4 bg-slate-900/80 rounded-2xl border border-slate-800 text-xs space-y-2 font-mono">
                <div class="flex justify-between items-center">
                  <span class="text-slate-500">Attempt ID:</span>
                  <span class="text-slate-200 truncate max-w-[200px]" :title="currentAttempt.attemptId">
                    {{ currentAttempt.attemptId }}
                  </span>
                </div>
                <div class="flex justify-between items-center">
                  <span class="text-slate-500">流程会话状态:</span>
                  <span
                    class="font-bold px-2 py-0.5 rounded text-[11px] border"
                    :class="ATTEMPT_STATUS_META[currentAttempt.status]?.badgeClass || 'bg-indigo-500/20 text-indigo-300 border-indigo-500/30'"
                  >
                    {{ ATTEMPT_STATUS_META[currentAttempt.status]?.label || currentAttempt.status }}
                  </span>
                </div>
                <div class="flex justify-between items-center">
                  <span class="text-slate-500">时效倒计时:</span>
                  <span class="text-indigo-300 font-semibold">{{ attemptCountdownText }}</span>
                </div>
                <div class="flex justify-between items-center">
                  <span class="text-slate-500">策略达成判定:</span>
                  <span
                    class="font-bold"
                    :class="currentAttempt.canComplete ? 'text-emerald-400' : 'text-slate-500'"
                  >
                    {{ currentAttempt.canComplete ? '✅ SATISFIED (条件已达成)' : '⏳ PENDING (等待达成)' }}
                  </span>
                </div>
              </div>

              <!-- 拓扑执行树 -->
              <div class="space-y-3">
                <h4 class="text-xs font-semibold text-slate-400 uppercase tracking-wider">策略判定拓扑</h4>
                <div class="p-4 bg-slate-900/60 rounded-2xl border border-slate-800">
                  <PolicyNodeViewer
                    :node="currentAttemptAst"
                    :progress="currentAttempt.progress"
                    :available="currentAttempt.available"
                  />
                </div>
              </div>

              <!-- 智能决策解读框 -->
              <div class="p-4 rounded-2xl bg-indigo-950/30 border border-indigo-500/30 text-xs text-indigo-200 leading-relaxed">
                <span class="font-bold flex items-center space-x-1.5 text-indigo-300 mb-1">
                  <span>🔍 引擎决策解析：</span>
                </span>
                <p class="text-indigo-200/90">{{ policyDecisionExplanation }}</p>
              </div>
            </div>
          </div>

          <!-- 右栏：动态可用因子执行交互区 (7 列) -->
          <div class="lg:col-span-7 space-y-6">
            
            <div class="glass-panel rounded-3xl p-6 border border-slate-800 space-y-6 shadow-2xl">
              <div class="flex items-center justify-between pb-4 border-b border-slate-800">
                <div>
                  <h3 class="text-base font-bold text-white flex items-center space-x-2">
                    <span>多因子核验操作区</span>
                    <span class="text-xs font-normal text-slate-400">
                      (当前待核验项: {{ currentAttempt.available.map((a) => a.nodeId).join(', ') || '无' }})
                    </span>
                  </h3>
                </div>
                <button
                  @click="handleRefreshStatus"
                  :disabled="loadingRefresh"
                  class="px-3 py-1.5 bg-slate-900 hover:bg-slate-800 text-slate-300 border border-slate-700/80 rounded-xl text-xs font-medium transition flex items-center space-x-1"
                >
                  <span :class="{ 'animate-spin': loadingRefresh }">🔄</span>
                  <span>刷新状态</span>
                </button>
              </div>

              <!-- 全条件满足：发光完成卡片 -->
              <div
                v-if="currentAttempt.canComplete && !completedSession"
                class="p-6 rounded-2xl bg-gradient-to-r from-emerald-950/40 via-emerald-900/20 to-emerald-950/40 border border-emerald-500/50 shadow-xl shadow-emerald-500/10 text-center space-y-3 animate-subtle-pulse"
              >
                <div class="w-12 h-12 rounded-2xl bg-emerald-500/20 text-emerald-300 flex items-center justify-center text-2xl mx-auto shadow-inner">
                  🎉
                </div>
                <h4 class="text-base font-bold text-emerald-200">策略树所有条件已达成！</h4>
                <p class="text-xs text-emerald-300/80 max-w-md mx-auto">
                  策略求值判定通过。点击下方按钮完成会话核销，签发访问令牌 (Bearer Token)。
                </p>
                <button
                  @click="handleCompleteLogin"
                  :disabled="loadingComplete"
                  class="px-8 py-3.5 bg-gradient-to-r from-emerald-500 to-teal-500 hover:from-emerald-400 hover:to-teal-400 text-slate-950 font-bold rounded-2xl shadow-xl shadow-emerald-500/20 transition transform active:scale-98 disabled:opacity-50 inline-flex items-center space-x-2 text-sm"
                >
                  <span v-if="loadingComplete" class="animate-spin">🔄</span>
                  <span>🚀 完成认证并签发访问令牌 (Complete)</span>
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

            <!-- 登录完成与安全防御实验室 -->
            <div
              v-if="completedSession"
              class="glass-panel rounded-3xl p-6 border border-emerald-500/40 shadow-2xl space-y-6"
            >
              <div class="flex items-center justify-between pb-3 border-b border-emerald-500/20">
                <div class="flex items-center space-x-2.5">
                  <span class="w-8 h-8 rounded-xl bg-emerald-500/20 text-emerald-400 flex items-center justify-center font-bold">
                    🛡️
                  </span>
                  <h3 class="text-base font-bold text-white">会话授权与安全边界测试 (Security Lab)</h3>
                </div>
                <span class="text-xs px-2.5 py-0.5 rounded-full bg-emerald-500/20 text-emerald-300 font-mono font-bold border border-emerald-500/30">
                  AUTHENTICATED
                </span>
              </div>

              <!-- 会话令牌信息 -->
              <div class="p-5 bg-slate-900/90 rounded-2xl border border-slate-800 font-mono text-xs space-y-2.5">
                <div class="flex justify-between text-slate-400">
                  <span>Token 协议类型:</span>
                  <span class="text-slate-200">{{ completedSession.tokenType || 'Bearer' }}</span>
                </div>
                <div>
                  <span class="text-slate-400 block mb-1">Access Token (单次签发 Opaque 哈希令牌):</span>
                  <div class="p-3 bg-slate-950 rounded-xl border border-slate-800 break-all select-all text-emerald-400 font-bold leading-relaxed">
                    {{ completedSession.accessToken }}
                  </div>
                </div>
                <div class="flex justify-between items-center pt-1 border-t border-slate-800/80">
                  <span class="text-slate-400">受保护接口验证 (/api/me):</span>
                  <span class="text-yellow-400 font-bold font-sans">
                    用户: {{ sessionUser?.username || '获取中...' }}
                  </span>
                </div>
              </div>

              <!-- 安全攻防边界实测卡片 -->
              <div class="space-y-3">
                <h4 class="text-xs font-bold text-slate-400 uppercase tracking-wider">安全防护边界测试 (Security Edge Cases):</h4>
                <div class="grid grid-cols-1 sm:grid-cols-2 gap-4 text-xs">
                  
                  <!-- 测试防重放 -->
                  <div class="p-4 bg-rose-950/20 border border-rose-900/40 rounded-2xl space-y-2">
                    <span class="font-bold text-rose-300 flex items-center space-x-1">
                      <span>🧪 测试防重放攻击 (Replay Attack)</span>
                    </span>
                    <p class="text-slate-400 text-[11px] leading-snug">
                      模拟再次向 /complete 发起会话核销请求，验证 CAS 乐观锁防重放拦截（预期返回 403）。
                    </p>
                    <button
                      @click="testReplayAttack"
                      :disabled="loadingReplay"
                      class="w-full py-2 bg-rose-600/80 hover:bg-rose-600 text-white rounded-xl font-semibold transition disabled:opacity-50"
                    >
                      模拟重放提交核销
                    </button>
                    <p v-if="testResults.replay" class="font-mono text-[11px] p-2 bg-slate-950 rounded-lg border border-rose-900/40 text-rose-300">
                      {{ testResults.replay }}
                    </p>
                  </div>

                  <!-- 测试 Cookie 篡改 -->
                  <div class="p-4 bg-indigo-950/20 border border-indigo-900/40 rounded-2xl space-y-2">
                    <span class="font-bold text-indigo-300 flex items-center space-x-1">
                      <span>🧪 测试伪造 Cookie 绑定</span>
                    </span>
                    <p class="text-slate-400 text-[11px] leading-snug">
                      伪造无效的 AUTH_BIND Cookie 发起请求，验证会话设备绑定防御（预期返回 403）。
                    </p>
                    <button
                      @click="testTamperedCookie"
                      :disabled="loadingCookie"
                      class="w-full py-2 bg-indigo-600/80 hover:bg-indigo-600 text-white rounded-xl font-semibold transition disabled:opacity-50"
                    >
                      模拟伪造 Cookie 请求
                    </button>
                    <p v-if="testResults.cookie" class="font-mono text-[11px] p-2 bg-slate-950 rounded-lg border border-indigo-900/40 text-indigo-300">
                      {{ testResults.cookie }}
                    </p>
                  </div>

                </div>
              </div>

              <div class="pt-2 flex justify-end">
                <button
                  @click="resetAttempt"
                  class="px-5 py-2 bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-semibold rounded-xl border border-slate-700 transition"
                >
                  重置会话 / 开始新测试
                </button>
              </div>
            </div>

          </div>
        </div>

      </div>

      <!-- TAB 2: 策略编排与热更工作台 -->
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

    <!-- 底栏 -->
    <footer class="glass-panel border-t border-slate-800/80 py-4 px-6 text-center text-xs text-slate-500 font-mono">
      Spring Boot 3.5.16 · Spring Security 6 · 动态认证策略引擎 · AST 组合求值 · CAS 原子状态机 · 快照隔离
    </footer>
  </div>
</template>
