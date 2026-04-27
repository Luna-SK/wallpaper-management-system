<script setup lang="ts">
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Message } from '@element-plus/icons-vue'
import { isAxiosError } from 'axios'
import { computed, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { requestPasswordReset } from '../api/auth'

const route = useRoute()

const formRef = ref<FormInstance>()
const form = reactive({ email: '' })
const loading = ref(false)
const sent = ref(false)

const loginRoute = computed(() => ({
  name: 'login',
  query: route.query.redirect ? { redirect: route.query.redirect } : undefined,
}))

const rules: FormRules = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入有效邮箱', trigger: 'blur' },
  ],
}

function errorMessage(error: unknown) {
  if (isAxiosError<{ message?: string }>(error)) {
    return error.response?.data?.message ?? `找回密码申请失败：${error.message}`
  }
  return error instanceof Error ? `找回密码申请失败：${error.message}` : '找回密码申请失败，请稍后重试'
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await requestPasswordReset(form.email.trim())
    sent.value = true
    ElMessage.success('如果邮箱存在，重置链接将发送到该邮箱')
  } catch (error) {
    ElMessage.error(errorMessage(error))
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
        <h2>找回密码</h2>
        <el-alert
          v-if="sent"
          type="success"
          show-icon
          :closable="false"
          title="如果邮箱存在，重置链接将发送到该邮箱"
          style="margin-bottom: 18px"
        />
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" :prefix-icon="Message" size="large" autocomplete="email" />
        </el-form-item>
        <el-button type="primary" size="large" native-type="submit" :loading="loading" style="width: 100%">发送重置邮件</el-button>
        <div class="auth-form-footer">
          <RouterLink :to="loginRoute">返回登录</RouterLink>
        </div>
      </el-form>
    </section>
  </main>
</template>
