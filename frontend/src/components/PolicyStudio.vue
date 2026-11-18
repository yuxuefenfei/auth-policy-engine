<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { getActivePolicy, listPolicyVersions, saveDraftPolicy, publishPolicy } from '@/api';
import type { PolicySnapshot, VersionInfo, PolicyNode } from '@/types/auth';

const activePolicy = ref<PolicySnapshot | null>(null);
const policyVersions = ref<VersionInfo[]>([]);
const policyEditorText = ref('');
const policyEditError = ref('');
const loadingDraft = ref(false);
const loadingPublish = ref(false);

const policyTemplates = [
  {
    name: '金融级组合认证 (Default)',
    badge: 'ALL + ANY',
    level: '🛡️ Level 3 双因子',
    desc: '密码或APP扫码二选一，另外必须短信动态码校验',
    summary: 'ALL(ANY(password, qr), sms)',
    negative: false,
    json: {
      op: 'ALL',
      children: [
        {
          op: 'ANY',
          children: [
            { op: 'FACTOR', id: 'password', factor: 'PASSWORD' },
            { op: 'FACTOR', id: 'qr', factor: 'APP_QR_APPROVAL' },
          ],
        },
        { op: 'FACTOR', id: 'sms', factor: 'SMS_OTP' },
      ],
    },
  },
  {
    name: '单密码认证策略',
    badge: 'FACTOR',
    level: '⚡ Level 1 基础认证',
    desc: '极简模式：只需验证一次正确密码即完成整个流程',
    summary: 'FACTOR(password)',
    negative: false,
    json: {
      op: 'FACTOR',
      id: 'password',
      factor: 'PASSWORD',
    },
  },
  {
    name: '双因子强认证 (Strict 2FA)',
    badge: 'ALL',
    level: '🔒 Level 2 强双因子',
    desc: '严格必须同时校验密码与手机短信动态验证码',
    summary: 'ALL(password, sms)',
    negative: false,
    json: {
      op: 'ALL',
      children: [
        { op: 'FACTOR', id: 'password', factor: 'PASSWORD' },
        { op: 'FACTOR', id: 'sms', factor: 'SMS_OTP' },
      ],
    },
  },
  {
    name: '二选一认证 (Either/Or)',
    badge: 'ANY',
    level: '🔄 灵活降级',
    desc: '用户在密码或短信中任意完成一个即可通行',
    summary: 'ANY(password, sms)',
    negative: false,
    json: {
      op: 'ANY',
      children: [
        { op: 'FACTOR', id: 'password', factor: 'PASSWORD' },
        { op: 'FACTOR', id: 'sms', factor: 'SMS_OTP' },
      ],
    },
  },
  {
    name: '三选二阈值认证 (Quorum)',
    badge: 'AT_LEAST 2',
    level: '🏛️ 阈值条件 (MFA)',
    desc: '密码、短信、APP扫码中，任意完成两项即可通过',
    summary: 'AT_LEAST(2, [pwd, sms, qr])',
    negative: false,
    json: {
      op: 'AT_LEAST',
      required: 2,
      children: [
        { op: 'FACTOR', id: 'password', factor: 'PASSWORD' },
        { op: 'FACTOR', id: 'sms', factor: 'SMS_OTP' },
        { op: 'FACTOR', id: 'qr', factor: 'APP_QR_APPROVAL' },
      ],
    },
  },
  {
    name: '顺序流转认证 (Sequence)',
    badge: 'SEQUENCE',
    level: '⏳ 严格按序',
    desc: '必须先完成第一步密码验证，才开放第二步短信验证',
    summary: 'SEQUENCE(password -> sms)',
    negative: false,
    json: {
      op: 'SEQUENCE',
      children: [
        { op: 'FACTOR', id: 'password', factor: 'PASSWORD' },
        { op: 'FACTOR', id: 'sms', factor: 'SMS_OTP' },
      ],
    },
  },
  {
    name: '未支持因子拦截测试',
    badge: '拒绝测试 ❌',
    level: '🛑 静态校验',
    desc: '策略包含未实现认证器 PASSKEY，验证发布强拦截',
    summary: 'ALL(password, PASSKEY)',
    negative: true,
    json: {
      op: 'ALL',
      children: [
        { op: 'FACTOR', id: 'password', factor: 'PASSWORD' },
        { op: 'FACTOR', id: 'passkey', factor: 'PASSKEY' },
      ],
    },
  },
  {
    name: '同类因子重复计数拦截',
    badge: '拒绝测试 ❌',
    level: '🛑 因子去重',
    desc: '策略重复使用相同 FactorType，验证防重复多因子校验',
    summary: 'ALL(pwd1, pwd2)',
    negative: true,
    json: {
      op: 'ALL',
      children: [
        { op: 'FACTOR', id: 'pwd1', factor: 'PASSWORD' },
        { op: 'FACTOR', id: 'pwd2', factor: 'PASSWORD' },
      ],
    },
  },
];

async function loadData() {
  try {
    const active = await getActivePolicy();
    activePolicy.value = active;
    if (!policyEditorText.value) {
      policyEditorText.value = JSON.stringify(active.root, null, 2);
    }
    policyVersions.value = await listPolicyVersions();
  } catch (err) {
    console.error('加载策略失败', err);
  }
}

function selectTemplate(tpl: any) {
  policyEditorText.value = JSON.stringify(tpl.json, null, 2);
  policyEditError.value = '';
}

function formatJson() {
  try {
    const obj = JSON.parse(policyEditorText.value);
    policyEditorText.value = JSON.stringify(obj, null, 2);
    policyEditError.value = '';
  } catch (err: any) {
    policyEditError.value = 'JSON 语法错误: ' + err.message;
  }
}

function formatDate(d: any) {
  if (!d) return '系统初始化';
  const parsed = new Date(d);
  return isNaN(parsed.getTime()) ? '系统初始化' : parsed.toLocaleString();
}

async function handleSaveDraft() {
  try {
    const ast: PolicyNode = JSON.parse(policyEditorText.value);
    loadingDraft.value = true;
    const res = await saveDraftPolicy('WEB_LOGIN', ast);
    alert(`成功保存为草稿版本: Version ${res.version}`);
    await loadData();
  } catch (err: any) {
    alert('保存草稿失败: ' + (err.data?.error || err.message));
  } finally {
    loadingDraft.value = false;
  }
}

async function handleSaveAndPublish() {
  try {
    const ast: PolicyNode = JSON.parse(policyEditorText.value);
    loadingPublish.value = true;
    const draft = await saveDraftPolicy('WEB_LOGIN', ast);
    const pub = await publishPolicy('WEB_LOGIN', draft.version);
    alert(`成功发布策略！当前生效版本已切换至: Version ${pub.version}`);
    await loadData();
  } catch (err: any) {
    alert('发布策略失败: ' + (err.data?.error || err.message));
  } finally {
    loadingPublish.value = false;
  }
}

async function handlePublishVersion(ver: number) {
  loadingPublish.value = true;
  try {
    const res = await publishPolicy('WEB_LOGIN', ver);
    alert(`已切换生效版本为: Version ${res.version}`);
    await loadData();
  } catch (err: any) {
    alert('切换版本失败: ' + (err.data?.error || err.message));
  } finally {
    loadingPublish.value = false;
  }
}

onMounted(loadData);
</script>

<template>
  <div class="space-y-6">
    <!-- 管理状态顶栏 -->
    <div class="glass-panel rounded-2xl p-6 border border-slate-800 flex flex-wrap items-center justify-between gap-4 shadow-xl">
      <div>
        <h2 class="text-base font-bold text-slate-100 flex items-center space-x-2">
          <span>⚙️ 策略编排与版本热更控制台 (Policy Studio)</span>
          <span class="text-[10px] px-2 py-0.5 rounded-full bg-indigo-500/20 text-indigo-300 font-mono border border-indigo-500/30">
            ROLE_ADMIN
          </span>
        </h2>
        <p class="text-xs text-slate-400 mt-1">
          管理接口受 Spring Security Basic Auth 保护 (<code>/api/admin/**</code>)，支持运行时热更新策略树并保持快照隔离
        </p>
      </div>
      <div class="flex items-center space-x-3 text-xs">
        <span class="text-slate-400">凭证:</span>
        <code class="bg-slate-900 border border-slate-800 px-2 py-1 rounded-lg font-mono text-indigo-300">admin:Admin@123456</code>
        <button
          @click="loadData"
          class="px-3 py-1.5 bg-slate-800 hover:bg-slate-700 text-slate-200 border border-slate-700 rounded-xl font-medium transition flex items-center space-x-1"
        >
          <span>🔄 刷新策略数据</span>
        </button>
      </div>
    </div>

    <!-- 预置策略模板库 (8 种场景) -->
    <div class="glass-panel rounded-2xl p-6 border border-slate-800 space-y-4 shadow-xl">
      <div class="flex items-center justify-between">
        <div>
          <h3 class="text-sm font-bold text-slate-100 flex items-center space-x-2">
            <span>📚 预置策略 AST 模板库</span>
            <span class="text-[11px] text-slate-400 font-normal">点击任意模板快速填入编辑器并一键发布测试</span>
          </h3>
        </div>
      </div>

      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3 text-xs">
        <div
          v-for="tpl in policyTemplates"
          :key="tpl.name"
          @click="selectTemplate(tpl)"
          class="p-4 rounded-xl border transition-all duration-200 cursor-pointer flex flex-col justify-between space-y-3 group"
          :class="[
            tpl.negative
              ? 'bg-rose-950/20 border-rose-900/40 hover:border-rose-500/60 hover:bg-rose-950/40'
              : 'bg-slate-900/60 border-slate-800 hover:border-indigo-500/60 hover:bg-slate-900/90'
          ]"
        >
          <div class="space-y-1.5">
            <div class="flex items-center justify-between">
              <span class="font-bold text-slate-200 group-hover:text-indigo-300 transition-colors">{{ tpl.name }}</span>
              <span
                class="px-1.5 py-0.5 rounded text-[10px] font-mono font-semibold"
                :class="tpl.negative ? 'bg-rose-500/20 text-rose-300' : 'bg-indigo-500/20 text-indigo-300'"
              >
                {{ tpl.badge }}
              </span>
            </div>
            <div class="text-[10px] text-slate-400 font-mono">{{ tpl.level }}</div>
            <p class="text-[11px] text-slate-400 leading-snug">{{ tpl.desc }}</p>
          </div>
          <code class="p-1.5 rounded bg-slate-950/80 border border-slate-800/80 font-mono text-[10px] text-slate-400 truncate block">
            {{ tpl.summary }}
          </code>
        </div>
      </div>
    </div>

    <!-- 编辑器与历史版本双栏 -->
    <div class="grid grid-cols-1 lg:grid-cols-12 gap-6">
      <!-- 左栏：JSON AST 编辑器 (7 列) -->
      <div class="lg:col-span-7 glass-panel rounded-2xl p-6 border border-slate-800 space-y-4 shadow-xl">
        <div class="flex items-center justify-between pb-3 border-b border-slate-800">
          <div>
            <h3 class="text-sm font-bold text-slate-100 flex items-center space-x-2">
              <span>AST 策略 JSON 编辑器</span>
              <span class="text-[10px] px-1.5 py-0.5 rounded bg-slate-800 text-slate-400 font-mono">AST White-list</span>
            </h3>
            <p class="text-xs text-slate-400">仅支持白名单操作符与认证方式，禁止执行任意动态表达式</p>
          </div>
          <button
            @click="formatJson"
            class="px-2.5 py-1 bg-slate-800 hover:bg-slate-700 text-slate-300 border border-slate-700 rounded-lg text-xs font-mono transition"
          >
            { } 格式化 JSON
          </button>
        </div>

        <div class="relative rounded-xl overflow-hidden border border-slate-800 bg-slate-950">
          <textarea
            v-model="policyEditorText"
            rows="16"
            class="w-full p-4 font-mono text-xs bg-slate-950 text-emerald-400 focus:outline-none focus:ring-1 focus:ring-indigo-500 resize-none leading-relaxed"
          ></textarea>
        </div>

        <div v-if="policyEditError" class="p-3 bg-rose-950/30 border border-rose-500/40 rounded-xl text-xs text-rose-300 flex items-center space-x-2">
          <span>⚠️</span>
          <span>{{ policyEditError }}</span>
        </div>

        <div class="flex flex-wrap items-center justify-between gap-3 pt-2">
          <div class="text-xs text-slate-400">
            生效版本: <strong class="text-indigo-400 font-mono">Version {{ activePolicy?.version }}</strong>
          </div>
          <div class="flex space-x-2">
            <button
              @click="handleSaveDraft"
              :disabled="loadingDraft"
              class="px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-200 border border-slate-700 rounded-xl text-xs font-semibold transition disabled:opacity-50 flex items-center space-x-1"
            >
              <span v-if="loadingDraft" class="animate-spin">🔄</span>
              <span>保存为草稿 (Draft)</span>
            </button>
            <button
              @click="handleSaveAndPublish"
              :disabled="loadingPublish"
              class="px-4 py-2 bg-gradient-to-r from-indigo-600 to-indigo-500 hover:from-indigo-500 hover:to-indigo-600 text-white rounded-xl text-xs font-semibold shadow-lg shadow-indigo-600/20 transition disabled:opacity-50 flex items-center space-x-1"
            >
              <span v-if="loadingPublish" class="animate-spin">🔄</span>
              <span>保存并立即发布 (Publish)</span>
            </button>
          </div>
        </div>
      </div>

      <!-- 右栏：版本历史与回滚 (5 列) -->
      <div class="lg:col-span-5 glass-panel rounded-2xl p-6 border border-slate-800 space-y-4 shadow-xl">
        <div class="flex items-center justify-between pb-3 border-b border-slate-800">
          <h3 class="text-sm font-bold text-slate-100">策略版本历史记录</h3>
          <span class="text-xs text-slate-400">共 {{ policyVersions.length }} 个版本</span>
        </div>

        <div class="space-y-3 max-h-[500px] overflow-y-auto pr-1">
          <div
            v-for="ver in policyVersions"
            :key="ver.version"
            class="p-4 rounded-xl border transition-all duration-200 space-y-2"
            :class="[
              ver.active
                ? 'bg-indigo-950/30 border-indigo-500/50 shadow-md shadow-indigo-500/10 ring-1 ring-indigo-500/20'
                : 'bg-slate-900/60 border-slate-800/80 hover:border-slate-700'
            ]"
          >
            <div class="flex items-center justify-between">
              <div class="flex items-center space-x-2">
                <span class="font-bold text-sm text-slate-100 font-mono">v{{ ver.version }}</span>
                <span
                  v-if="ver.active"
                  class="bg-emerald-500/20 text-emerald-300 border border-emerald-500/30 text-[10px] font-bold px-2 py-0.5 rounded-full font-mono"
                >
                  ● 当前生效 (ACTIVE)
                </span>
              </div>
              <button
                v-if="!ver.active"
                @click="handlePublishVersion(ver.version)"
                :disabled="loadingPublish"
                class="px-2.5 py-1 bg-slate-800 hover:bg-slate-700 border border-slate-700 text-indigo-300 rounded-lg text-xs font-semibold transition"
              >
                回退至此版本 ↺
              </button>
            </div>

            <div class="text-[11px] text-slate-400 font-mono">
              发布时间: {{ formatDate(ver.createdAt) }}
            </div>

            <pre class="bg-slate-950 p-2.5 rounded-lg border border-slate-800 font-mono text-[10px] text-slate-300 max-h-24 overflow-y-auto leading-tight">{{
              JSON.stringify(ver.root, null, 2)
            }}</pre>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
