<script lang="ts" setup>
/**
 * TD-005 Canary 认证-only harness
 *
 * 目的：在受控 Canary 窗口复现真实认证链（验证码 → 租户 → login → permission-info），
 * 在 permission-info 成功后停止；绝不进入 Router/Dashboard，绝不调用 dict/video/业务 API。
 *
 * 与 userStore.login 的关键差异（跳转根源隔离）：
 * - 直接调用 loginApi/getUserInfo，绕过 userStore.login 的 afterLoginAction
 *   （后者会 router.replace(/dashboard) + buildRoutesAction，从而触发 dictStore.setDictMap
 *   和 Dashboard 的 /video/alert/**，全部超出认证 allowlist）。
 * - rememberMe 强制 false；不读取/显示/导出 token 值。
 *
 * 硬约束（评审核对）：
 * - 禁止 import：useRouter、@/views/dashboard/*、@/store/modules/dict、@/api/video/*、
 *   useUserStore.login / afterLoginAction。
 * - 禁止 router.push / router.replace / window.location 跳转。
 * - 页面不读取 token、不清理会话、不提供认证重试；会话清理必须进入独立批准窗口。
 *
 * 构建 gate：本组件所在路由仅在 VITE_TD005_AUTH_HARNESS=true 时注册
 * （见 router/routes/index.ts）；生产默认构建不含本页，访问返回 404。
 */
import { computed, reactive, ref } from 'vue'

import './td005AuthAllowlist.js'

import { Button, Form, Input } from 'ant-design-vue'

import { Verify } from '@/components/Verifition'
import { useGlobSetting } from '@/hooks/setting'
import { useMessage } from '@/hooks/web/useMessage'
import { usePermissionStore } from '@/store/modules/permission'
import { getTenantIdByName } from '@/api/base/login'
import { getUserInfo, loginApi } from '@/api/base/user'
import type { GetUserInfoModel } from '@/api/base/model/userModel'
import * as authUtil from '@/utils/auth'

const FormItem = Form.Item
const InputPassword = Input.Password

const { notification, createErrorModal } = useMessage()
const { tenantEnable, captchaEnable } = useGlobSetting()
const permissionStore = usePermissionStore()

const harnessEnabled = import.meta.env.VITE_TD005_AUTH_HARNESS === 'true'

type StepStatus = 'pending' | 'running' | 'ok' | 'fail'
type StepKey = 'tenant' | 'captcha' | 'login' | 'permission'
interface StepState extends Record<StepKey, StepStatus> {}

const loading = ref(false)
const verify = ref()
const captchaType = ref('blockPuzzle')
const attemptLocked = ref(false)
const authChainStarted = ref(false)

const formData = reactive({
  tenantName: '',
  username: '',
  password: '',
})

const steps = reactive<StepState>({
  tenant: 'pending',
  captcha: 'pending',
  login: 'pending',
  permission: 'pending',
})

const evidence = ref<GetUserInfoModel | null>(null)
const evidenceTenantId = ref<string | number | null>(null)

const done = computed(() => steps.permission === 'ok')

const stepList = computed<{ key: StepKey, label: string }[]>(() => [
  { key: 'tenant', label: 'tenant get-id-by-name' },
  { key: 'captcha', label: 'captcha get/check' },
  { key: 'login', label: 'auth/login' },
  { key: 'permission', label: 'auth/get-permission-info' },
])

async function resolveTenant() {
  steps.tenant = 'running'
  if (tenantEnable === 'true') {
    const res = await getTenantIdByName(formData.tenantName)
    authUtil.setTenantId(res.id)
    evidenceTenantId.value = res.id as string | number
  }
  steps.tenant = 'ok'
}

async function runAuthChain(captchaVerification: string) {
  loading.value = true
  try {
    // rememberMe 强制 false（sessionStorage，30 分钟内不持久化）
    authUtil.switchAuthStorage(false)

    steps.login = 'running'
    const result = await loginApi(
      {
        username: formData.username,
        password: formData.password,
        captchaVerification,
        rememberMe: false,
      },
      'none',
    )
    authUtil.setAccessToken(result.accessToken)
    authUtil.setRefreshToken(result.refreshToken)
    steps.login = 'ok'

    steps.permission = 'running'
    const userInfo = await getUserInfo()
    // changePermissionCode 是纯内存赋值，无 API/路由副作用
    permissionStore.changePermissionCode(userInfo.permissions ?? [])
    evidence.value = userInfo
    steps.permission = 'ok'

    notification.success({
      message: '认证-only 流程完成',
      description: 'permission-info 成功；流程已在此停止，未进入 Dashboard。',
      duration: 5,
    })
  }
  finally {
    loading.value = false
  }
}

async function onCaptchaSuccess(payload: { captchaVerification: string }) {
  if (!attemptLocked.value || authChainStarted.value)
    return

  authChainStarted.value = true
  steps.captcha = 'ok'
  try {
    await runAuthChain(payload.captchaVerification)
  }
  catch (error) {
    failCurrentStep(error)
  }
}

function onCaptchaError(error?: unknown) {
  if (!attemptLocked.value || authChainStarted.value)
    return

  authChainStarted.value = true
  steps.captcha = 'fail'
  failCurrentStep(error ?? new Error('captcha error'))
}

async function start() {
  if (!harnessEnabled) {
    createErrorModal({ title: 'harness 未启用', content: '本构建未启用认证-only harness（VITE_TD005_AUTH_HARNESS!=true）。' })
    return
  }
  if (!formData.username || !formData.password || (tenantEnable === 'true' && !formData.tenantName)) {
    createErrorModal({ title: '信息缺失', content: '租户名（如启用）、用户名、密码均需填写。' })
    return
  }
  if (attemptLocked.value) {
    createErrorModal({ title: '认证已锁定', content: '本页面只允许一次认证尝试；请勿刷新或重试。' })
    return
  }

  // 从首次租户查询开始永久锁定；成功、失败或验证码关闭后均不在本页解锁。
  attemptLocked.value = true

  try {
    await resolveTenant()
    if (captchaEnable === 'true') {
      steps.captcha = 'running'
      verify.value?.show()
    }
    else {
      steps.captcha = 'ok'
      authChainStarted.value = true
      await runAuthChain('')
    }
  }
  catch (error) {
    failCurrentStep(error)
  }
}

function failCurrentStep(error: unknown) {
  const msg = (error as Error)?.message || 'unknown error'
  ;(Object.keys(steps) as StepKey[]).forEach((k) => {
    if (steps[k] === 'running')
      steps[k] = 'fail'
  })
  createErrorModal({
    title: '认证步骤失败',
    content: msg,
  })
}

</script>

<template>
  <div class="td005-auth-harness">
    <h2>TD-005 Canary 认证-only harness</h2>
    <p class="muted">
      本页仅执行认证链，permission-info 成功后停止；不进入 Dashboard，不调用 dict/video/业务 API。
    </p>

    <Form class="form" :model="formData" layout="vertical">
      <FormItem v-if="tenantEnable === 'true'" label="租户名">
        <Input v-model:value="formData.tenantName" size="large" placeholder="租户名" class="fix-auto-fill" />
      </FormItem>
      <FormItem label="用户名">
        <Input v-model:value="formData.username" size="large" placeholder="用户名" class="fix-auto-fill" />
      </FormItem>
      <FormItem label="密码">
        <InputPassword v-model:value="formData.password" size="large" visibility-toggle placeholder="密码" class="fix-auto-fill" />
      </FormItem>
      <FormItem>
        <Button type="primary" size="large" block :loading="loading" :disabled="attemptLocked" @click="start">
          {{ attemptLocked ? '认证已锁定（不可重试）' : '开始认证（rememberMe=false）' }}
        </Button>
      </FormItem>
    </Form>

    <div class="steps">
      <div v-for="s in stepList" :key="s.key" class="step">
        <span class="dot" :class="steps[s.key]" />
        <span>{{ s.label }}</span>
      </div>
    </div>

    <div v-if="done && evidence" class="evidence">
      <h3>认证成功（已停止）</h3>
      <ul>
        <li>user.id：{{ evidence.user?.id }}</li>
        <li>user.nickname：{{ evidence.user?.nickname }}</li>
        <li>tenantId：{{ evidenceTenantId }}</li>
        <li>permissions 数量：{{ evidence.permissions?.length ?? 0 }}</li>
        <li>roles 数量：{{ evidence.roles?.length ?? 0 }}</li>
      </ul>
      <p class="muted">
        请 owner 在 system_oauth2_access_token / system_oauth2_refresh_token 核对新增 access/refresh 各 1 条；
        本窗口不显示、不导出 token 值。
      </p>
    </div>

    <Verify
      ref="verify"
      mode="pop"
      :captcha-type="captchaType"
      :img-size="{ width: '360px', height: '180px' }"
      @success="onCaptchaSuccess"
      @error="onCaptchaError"
    />
  </div>
</template>

<style scoped>
.td005-auth-harness {
  max-width: 480px;
  margin: 40px auto;
  padding: 24px;
}

.muted {
  color: #888;
  font-size: 12px;
}

.form {
  margin-top: 16px;
}

.steps {
  margin-top: 16px;
}

.step {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 6px 0;
}

.dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  display: inline-block;
  background: #ccc;
}

.dot.running {
  background: #1890ff;
}

.dot.ok {
  background: #52c41a;
}

.dot.fail {
  background: #f5222d;
}

.evidence {
  margin-top: 24px;
  padding: 16px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
}
</style>
