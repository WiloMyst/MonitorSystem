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
    // 1. 调用后端接口获取 Token
    const token = await loginApi(loginForm.value)
    
    // 2. 存入 Pinia 状态机
    userStore.setToken(token, loginForm.value.username)
    
    ElMessage.success('登录成功')
    
    // 3. 页面跳转到控制台大屏
    router.push('/')
  } catch (error) {
    // 错误在 request.ts 中已经统一拦截提示，这里只需捕获即可
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
  background-color: #2d3a4b; /* 国企偏爱的沉稳蓝黑底色 */
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