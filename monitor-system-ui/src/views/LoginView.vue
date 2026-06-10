<!--
  登录页
  调用后端 /auth/login 接口获取 Sa-Token，存入 Pinia 和 localStorage 后跳转仪表盘。
-->
<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { loginApi } from '../api/auth'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()

const loginForm = ref({ username: '', password: '' })
const loading = ref(false)

const handleLogin = async () => {
  if (!loginForm.value.username || !loginForm.value.password) {
    ElMessage.warning('请输入账号和密码')
    return
  }

  loading.value = true
  try {
    const token = await loginApi(loginForm.value)
    userStore.setToken(token, loginForm.value.username)
    ElMessage.success('登录成功')
    router.push('/')
  } catch (error) {
    // 错误已在 request.ts 响应拦截器中统一提示
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-container">
    <el-card class="login-card" shadow="always">
      <template #header>
        <h2 class="login-title">全网智能监控平台 (v1.0)</h2>
      </template>
      <el-input 
        v-model="loginForm.username" 
        placeholder="请输入账号 (如: admin)" 
        size="large" 
        style="margin-bottom: 20px;" 
      />
      <el-input 
        v-model="loginForm.password" 
        type="password" 
        placeholder="请输入密码 (如: 123456)" 
        size="large" 
        style="margin-bottom: 20px;" 
        @keyup.enter="handleLogin"
      />
      <el-button 
        type="primary" 
        size="large" 
        style="width: 100%;" 
        :loading="loading" 
        @click="handleLogin"
      >
        安全登录
      </el-button>
    </el-card>
  </div>
</template>

<style scoped>
.login-container {
  height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  background-color: #2d3a4b;
}
.login-card {
  width: 400px;
  border-radius: 8px;
}
.login-title {
  text-align: center;
  margin: 0;
  color: #303133;
}
</style>