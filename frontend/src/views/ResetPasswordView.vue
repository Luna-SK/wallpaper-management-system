<script setup lang="ts">
import { ElMessage } from 'element-plus/es/components/message/index'
import type { FormInstance, FormRules } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import { Lock } from '@element-plus/icons-vue'
import { isAxiosError } from 'axios'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { confirmPasswordReset, getPasswordResetPolicy } from '../api/auth'

const route = useRoute()
const router = useRouter()

const formRef = ref<FormInstance>()
const form = reactive({
  password: '',
  confirmPassword: '',
})
const loading = ref(false)
const policyLoading = ref(true)
const emailResetEnabled = ref(false)
const disabledMessage = '邮件找回密码已关闭，请登录后使用原密码修改密码或联系管理员'
const token = computed(() => typeof route.query.token === 'string' ? route.query.token : '')
const loginRoute = computed(() => ({
  name: 'login',
  query: route.query.redirect ? { redirect: route.query.redirect } : undefined,
}))

const rules: FormRules = {
  password: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码至少需要 6 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        if (value !== form.password) {
          callback(new Error('两次输入的密码不一致'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
}

function errorMessage(error: unknown) {
  if (isAxiosError<{ message?: string }>(error)) {
    return error.response?.data?.message ?? `密码重置失败：${error.message}`
  }
  return error instanceof Error ? `密码重置失败：${error.message}` : '密码重置失败，请重新申请'
}

async function submit() {
  if (!emailResetEnabled.value) {
    ElMessage.warning(disabledMessage)
    return
  }
  if (!token.value) {
    ElMessage.error('重置链接无效，请重新申请')
    return
  }
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await confirmPasswordReset({ token: token.value, newPassword: form.password })
    ElMessage.success('密码已重置，请重新登录')
    await router.push(loginRoute.value)
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}

async function loadPasswordResetPolicy() {
  policyLoading.value = true
  try {
    const policy = await getPasswordResetPolicy()
    emailResetEnabled.value = policy.emailResetEnabled
  } catch {
    emailResetEnabled.value = false
  } finally {
    policyLoading.value = false
  }
}

onMounted(loadPasswordResetPolicy)
</script>

<template>
  <main class="login-page">
    <section class="login-intro">
      <img class="login-logo" src="/logo.png" alt="" />
      <h1>图片管理系统</h1>
    </section>

    <section class="login-panel">
      <el-form ref="formRef" class="login-form" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
        <h2>重置密码</h2>
        <el-alert
          v-if="!policyLoading && !emailResetEnabled"
          type="warning"
          show-icon
          :closable="false"
          :title="disabledMessage"
          style="margin-bottom: 18px"
        />
        <el-alert
          v-if="!token"
          type="warning"
          show-icon
          :closable="false"
          title="重置链接无效，请重新申请"
          style="margin-bottom: 18px"
        />
        <el-form-item label="新密码" prop="password">
          <el-input
            v-model="form.password"
            :prefix-icon="Lock"
            type="password"
            size="large"
            show-password
            autocomplete="new-password"
            :disabled="!emailResetEnabled"
          />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            :prefix-icon="Lock"
            type="password"
            size="large"
            show-password
            autocomplete="new-password"
            :disabled="!emailResetEnabled"
          />
        </el-form-item>
        <el-button
          type="primary"
          size="large"
          native-type="submit"
          :loading="loading || policyLoading"
          :disabled="!token || !emailResetEnabled"
          style="width: 100%"
        >
          重置密码
        </el-button>
        <div class="auth-form-footer">
          <RouterLink :to="loginRoute">返回登录</RouterLink>
        </div>
      </el-form>
    </section>
  </main>
</template>
