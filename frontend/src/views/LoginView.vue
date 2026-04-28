<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import { isAxiosError } from 'axios'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getPasswordResetPolicy } from '../api/auth'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const form = reactive({
  username: '',
  password: '',
})

const loading = ref(false)
const passwordResetEnabled = ref(false)
const registerRoute = computed(() => ({
  name: 'register',
  query: route.query.redirect ? { redirect: route.query.redirect } : undefined,
}))
const forgotPasswordRoute = computed(() => ({
  name: 'forgot-password',
  query: route.query.redirect ? { redirect: route.query.redirect } : undefined,
}))

function loginErrorMessage(error: unknown) {
  if (isAxiosError<{ message?: string }>(error)) {
    return error.response?.data?.message ?? `登录失败：${error.message}`
  }
  return error instanceof Error ? `登录失败：${error.message}` : '登录失败，请检查后端服务是否正常'
}

async function submit() {
  loading.value = true
  try {
    await auth.login(form.username, form.password)
    await router.push((route.query.redirect as string) || '/images')
  } catch (error) {
    console.error('Login failed', error)
    ElMessage.error(loginErrorMessage(error))
  } finally {
    loading.value = false
  }
}

async function loadPasswordResetPolicy() {
  try {
    const policy = await getPasswordResetPolicy()
    passwordResetEnabled.value = policy.emailResetEnabled
  } catch {
    passwordResetEnabled.value = false
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
      <el-form class="login-form" :model="form" label-position="top" @submit.prevent="submit">
        <h2>登录系统</h2>
        <el-form-item label="用户名">
          <el-input v-model="form.username" :prefix-icon="User" size="large" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" :prefix-icon="Lock" type="password" size="large" show-password />
        </el-form-item>
        <el-button type="primary" size="large" native-type="submit" :loading="loading" style="width: 100%">登录</el-button>
        <div v-if="passwordResetEnabled" class="auth-form-link-row">
          <RouterLink :to="forgotPasswordRoute">忘记密码？</RouterLink>
        </div>
        <div class="auth-form-footer">
          <span>没有账号？</span>
          <RouterLink :to="registerRoute">注册账号</RouterLink>
        </div>
      </el-form>
    </section>
  </main>
</template>
