<script setup lang="ts">
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Lock, Message, Phone, User } from '@element-plus/icons-vue'
import { isAxiosError } from 'axios'
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const formRef = ref<FormInstance>()
const form = reactive({
  username: '',
  displayName: '',
  password: '',
  confirmPassword: '',
  email: '',
  phone: '',
})

const loading = ref(false)
const loginRoute = computed(() => ({
  name: 'login',
  query: route.query.redirect ? { redirect: route.query.redirect } : undefined,
}))

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  displayName: [{ required: true, message: '请输入显示名称', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少需要 6 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
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

function registerErrorMessage(error: unknown) {
  if (isAxiosError<{ message?: string }>(error)) {
    return error.response?.data?.message ?? `注册失败：${error.message}`
  }
  return error instanceof Error ? `注册失败：${error.message}` : '注册失败，请检查后端服务是否正常'
}

function redirectTarget() {
  return typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/')
    ? route.query.redirect
    : '/images'
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await auth.register({
      username: form.username.trim(),
      displayName: form.displayName.trim(),
      password: form.password,
      email: form.email.trim() || null,
      phone: form.phone.trim() || null,
    })
    ElMessage.success('注册成功')
    await router.push(redirectTarget())
  } catch (error) {
    console.error('Register failed', error)
    ElMessage.error(registerErrorMessage(error))
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
      <el-form ref="formRef" class="login-form" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
        <h2>注册账号</h2>
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" :prefix-icon="User" size="large" autocomplete="username" />
        </el-form-item>
        <el-form-item label="显示名称" prop="displayName">
          <el-input v-model="form.displayName" :prefix-icon="User" size="large" autocomplete="name" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" :prefix-icon="Lock" type="password" size="large" show-password autocomplete="new-password" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" :prefix-icon="Lock" type="password" size="large" show-password autocomplete="new-password" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" :prefix-icon="Message" size="large" autocomplete="email" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" :prefix-icon="Phone" size="large" autocomplete="tel" />
        </el-form-item>
        <el-button type="primary" size="large" native-type="submit" :loading="loading" style="width: 100%">注册并进入系统</el-button>
        <div class="auth-form-footer">
          <span>已有账号？</span>
          <RouterLink :to="loginRoute">返回登录</RouterLink>
        </div>
      </el-form>
    </section>
  </main>
</template>
