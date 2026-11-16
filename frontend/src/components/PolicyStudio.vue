<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { getActivePolicy, getPolicyVersions, savePolicyDraft, publishPolicy } from '../api/auth';
import type { PolicySnapshot, VersionInfo, PolicyNode } from '../types/auth';

const activePolicy = ref<PolicySnapshot | null>(null);
const policyVersions = ref<VersionInfo[]>([]);
const policyEditorText = ref('');
const policyEditError = ref('');
const loadingDraft = ref(false);
const loadingPublish = ref(false);

const policyTemplates = [
  {
    name: '默认策略 (Default)',
    badge: 'ALL + ANY',
    desc: '密码/扫码二选一，另外必须短信动态验证码',
    summary: 'ALL(ANY(pwd, qr), sms)',
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
    name: '纯密码策略 (Single Factor)',
    badge: 'FACTOR',
    desc: '最精简策略：只需验证一次正确密码即完成',
    summary: 'FACTOR(password)',
    json: {
      op: 'FACTOR',
      id: 'password',
      factor: 'PASSWORD',
    },
  },
  {
    name: '双因子强认证 (Strict 2FA)',
    badge: 'ALL',
    desc: '必须同时验证密码及短信动态验证码',
    summary: 'ALL(password, sms)',
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
    desc: '密码或者短信任意验证一个即可登录',
    summary: 'ANY(password, sms)',
    json: {
      op: 'ANY',
      children: [
        { op: 'FACTOR', id: 'password', factor: 'PASSWORD' },
        { op: 'FACTOR', id: 'sms', factor: 'SMS_OTP' },
      ],
    },
  },
  {
    name: '三选二多因子投票 (Quorum)',
    badge: 'AT_LEAST 2',
    desc: '密码、短信、扫码三种因子中，任意完成两项即可',
    summary: 'AT_LEAST(2, [pwd, sms, qr])',
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
    name: '顺序流转策略 (Sequence)',
    badge: 'SEQUENCE',
    desc: '严格按步骤流转：第1步验证密码，通过后才开放第2步短信',
    summary: 'SEQUENCE(password -> sms)',
    json: {
      op: 'SEQUENCE',
      children: [
        { op: 'FACTOR', id: 'password', factor: 'PASSWORD' },
        { op: 'FACTOR', id: 'sms', factor: 'SMS_OTP' },
      ],
    },
  },
  {
    name: '非法 Passkey 拦截测试',
    badge: '拒绝测试 ❌',
    desc: '策略包含服务端尚未实现的 PASSKEY 认证器，验证发布拦截',
    summary: 'ALL(password, PASSKEY)',
    json: {
      op: 'ALL',
      children: [
        { op: 'FACTOR', id: 'password', factor: 'PASSWORD' },
        { op: 'FACTOR', id: 'passkey', factor: 'PASSKEY' },
      ],
    },
  },
  {
    name: '重复因子拦截测试',
    badge: '拒绝测试 ❌',
    desc: '策略重复使用相同 FactorType，验证防重复多因子校验',
    summary: 'ALL(pwd1, pwd2)',
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
    const versions = await getPolicyVersions();
    policyVersions.value = versions;
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

async function handleSaveDraft() {
  try {
    const ast: PolicyNode = JSON.parse(policyEditorText.value);
    loadingDraft.value = true;
    const res = await savePolicyDraft('WEB_LOGIN', ast);
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
    const draft = await savePolicyDraft('WEB_LOGIN', ast);
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
    <!-- 管理状态条 -->
    <div class="bg-white rounded-2xl p-5 shadow-sm border border-gray-200 flex flex-wrap items-center justify-between gap-4">
      <div>
        <h2 class="text-base font-bold text-gray-900">策略编排与版本热更控制台</h2>
        <p class="text-xs text-gray-500">接口受 Spring Security Basic Auth 保护 (/api/admin/**)，需要 ROLE_ADMIN 权限</p>
      </div>
      <div class="flex items-center space-x-3 text-xs">
        <span class="text-gray-500">当前管理凭证:</span>
        <code class="bg-gray-100 px-2 py-1 rounded font-mono text-gray-700">admin:Admin@123456</code>
        <button @click="loadData" class="px-3 py-1 bg-blue-50 text-blue-700 hover:bg-blue-100 rounded-lg font-semibold transition">
          🔄 刷新策略
        </button>
      </div>
    </div>

    <!-- 预置策略模板库 -->
    <div class="bg-white rounded-2xl p-5 shadow-sm border border-gray-200 space-y-3">
      <h3 class="text-sm font-bold text-gray-900 flex items-center space-x-2">
        <span>📚 预置策略 AST 模板库</span>
        <span class="text-xs font-normal text-gray-500">(点击任意模板快速填入编辑器并一键发布)</span>
      </h3>

      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3 text-xs">
        <div
          v-for="tpl in policyTemplates"
          :key="tpl.name"
          @click="selectTemplate(tpl)"
          class="p-3 rounded-xl border border-gray-200 hover:border-blue-500 hover:bg-blue-50/50 cursor-pointer transition flex flex-col justify-between space-y-2"
        >
          <div>
            <div class="flex items-center justify-between">
              <span class="font-bold text-gray-900">{{ tpl.name }}</span>
              <span class="px-1.5 py-0.5 rounded text-[10px] font-semibold bg-blue-100 text-blue-700">
                {{ tpl.badge }}
              </span>
            </div>
            <p class="text-gray-500 text-[11px] mt-1">{{ tpl.desc }}</p>
          </div>
          <code class="bg-gray-50 p-1.5 rounded font-mono text-[10px] text-gray-600 truncate block">{{ tpl.summary }}</code>
        </div>
      </div>
    </div>

    <!-- 编辑器与历史版本 -->
    <div class="grid grid-cols-1 lg:grid-cols-12 gap-6">
      <!-- 左栏：编辑器 -->
      <div class="lg:col-span-7 bg-white rounded-2xl p-6 shadow-sm border border-gray-200 space-y-4">
        <div class="flex items-center justify-between pb-3 border-b border-gray-100">
          <div>
            <h3 class="text-sm font-bold text-gray-900">AST 策略 JSON 编辑器</h3>
            <p class="text-xs text-gray-500">支持 FACTOR / ALL / ANY / AT_LEAST / SEQUENCE 操作符</p>
          </div>
          <button @click="formatJson" class="px-2.5 py-1 bg-gray-100 hover:bg-gray-200 text-gray-700 rounded-lg text-xs">
            格式化 JSON
          </button>
        </div>

        <textarea
          v-model="policyEditorText"
          rows="15"
          class="w-full p-3 font-mono text-xs bg-gray-900 text-green-400 rounded-xl border border-gray-700 focus:ring-2 focus:ring-blue-500 focus:outline-none"
        ></textarea>

        <div v-if="policyEditError" class="p-2.5 bg-red-50 border border-red-200 rounded-lg text-xs text-red-700">
          ⚠️ {{ policyEditError }}
        </div>

        <div class="flex flex-wrap items-center justify-between gap-3 pt-2">
          <div class="text-xs text-gray-500">
            当前生效: <span class="font-bold text-gray-800">Version {{ activePolicy?.version }}</span>
          </div>
          <div class="flex space-x-2">
            <button
              @click="handleSaveDraft"
              :disabled="loadingDraft"
              class="px-4 py-2 bg-gray-100 hover:bg-gray-200 text-gray-800 rounded-xl text-xs font-semibold transition"
            >
              <span v-if="loadingDraft" class="animate-spin mr-1">🔄</span>
              <span>保存为新版本草稿 (Draft)</span>
            </button>
            <button
              @click="handleSaveAndPublish"
              :disabled="loadingPublish"
              class="px-4 py-2 bg-gradient-to-r from-blue-600 to-indigo-600 text-white rounded-xl text-xs font-semibold shadow hover:from-blue-700 hover:to-indigo-700 transition"
            >
              <span v-if="loadingPublish" class="animate-spin mr-1">🔄</span>
              <span>保存并立即发布 (Publish)</span>
            </button>
          </div>
        </div>
      </div>

      <!-- 右栏：版本历史与回退 -->
      <div class="lg:col-span-5 bg-white rounded-2xl p-6 shadow-sm border border-gray-200 space-y-4">
        <div class="flex items-center justify-between pb-3 border-b border-gray-100">
          <h3 class="text-sm font-bold text-gray-900">版本历史记录</h3>
          <button @click="loadData" class="text-xs text-blue-600 hover:underline">刷新列表</button>
        </div>

        <div class="space-y-3 max-h-[460px] overflow-y-auto pr-1">
          <div
            v-for="ver in policyVersions"
            :key="ver.version"
            class="p-3 rounded-xl border transition"
            :class="ver.active ? 'bg-blue-50/70 border-blue-300' : 'bg-gray-50 border-gray-200'"
          >
            <div class="flex items-center justify-between mb-1.5">
              <div class="flex items-center space-x-2">
                <span class="font-bold text-sm text-gray-900">v{{ ver.version }}</span>
                <span v-if="ver.active" class="bg-blue-600 text-white text-[10px] font-bold px-1.5 py-0.5 rounded">
                  当前生效 (ACTIVE)
                </span>
              </div>
              <button
                v-if="!ver.active"
                @click="handlePublishVersion(ver.version)"
                :disabled="loadingPublish"
                class="px-2.5 py-1 bg-white hover:bg-gray-100 border text-blue-700 rounded text-xs font-semibold shadow-sm transition"
              >
                发布此版本
              </button>
            </div>
            <div class="text-[11px] text-gray-500 mb-1">
              创建时间: {{ new Date(ver.createdAt).toLocaleString() }}
            </div>
            <pre class="bg-white p-2 rounded border font-mono text-[10px] text-gray-700 max-h-24 overflow-y-auto">{{
              JSON.stringify(ver.root, null, 2)
            }}</pre>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
