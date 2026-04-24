<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import { isAxiosError } from 'axios'
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const form = reactive({
  username: 'admin',
  password: 'admin123',
})

const loading = ref(false)

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
        <el-form-item label="账号">
          <el-input v-model="form.username" :prefix-icon="User" size="large" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" :prefix-icon="Lock" type="password" size="large" show-password />
        </el-form-item>
        <el-button type="primary" size="large" native-type="submit" :loading="loading" style="width: 100%">登录</el-button>
      </el-form>
    </section>
  </main>
</template>
