<!--
  应用根组件
  布局结构:
    - 登录页: 全屏 router-view
    - 其他页面: Header + Aside(侧边菜单) + Main(内容区)
  功能:
    - 退出登录
    - 重置 AI 缓存（高危操作，二次确认）
-->
<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from './stores/user'
import { Monitor, Document, Setting, ChatDotRound, SwitchButton, Refresh } from '@element-plus/icons-vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import { refreshPromptCache } from './api/prompt'

const isCollapse = ref(false)
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const isRefreshing = ref(false)

const handleLogout = () => {
  ElMessageBox.confirm('确定要退出登录吗?', '提示', { type: 'warning' }).then(() => {
    userStore.logout()
    router.push('/login')
  }).catch(() => {})
}

const handleRefreshCache = () => {
  ElMessageBox.confirm(
    '此操作将清空 AI 专家的本地记忆，强制重载数据库里的最新提示词，是否继续？',
    '高危系统操作',
    { confirmButtonText: '确定重置', cancelButtonText: '取消', type: 'warning' }
  ).then(async () => {
    isRefreshing.value = true
    try {
      const msg = await refreshPromptCache('device_rag')
      ElMessage.success(msg || 'AI 缓存重置成功！')
    } catch (error) {
      // 错误已在统一拦截器处理
    } finally {
      isRefreshing.value = false
    }
  }).catch(() => {})
}
</script>

<template>
  <router-view v-if="route.name === 'login'" />

  <div v-else class="common-layout">
    <el-container class="layout-container">
      
      <el-header class="layout-header">
        <div class="logo-title">
          <el-icon :size="24" color="#fff" style="margin-right: 10px;"><Monitor /></el-icon>
          <span>分布式数据中心智能监控与预警平台 (v1.0)</span>
        </div>
        
        <div class="user-info">
          <el-button 
            type="danger" 
            text 
            bg 
            size="small"
            :icon="Refresh" 
            :loading="isRefreshing" 
            @click="handleRefreshCache"
            style="margin-right: 20px;"
          >
            重置 AI 缓存
          </el-button>

          <el-avatar size="small" src="https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png" />
          <span style="margin-left: 10px; color: #fff;">{{ userStore.username }} (管理员)</span>
          
          <el-button type="danger" link style="margin-left: 20px;" @click="handleLogout">
            <el-icon><SwitchButton /></el-icon> 退出
          </el-button>
        </div>
      </el-header>

      <el-container>
        <el-aside :width="isCollapse ? '64px' : '220px'" class="layout-aside">
          <el-menu active-text-color="#409EFF" background-color="#304156" class="el-menu-vertical" default-active="/" text-color="#bfcbd9" :collapse="isCollapse" router>
            <el-menu-item index="/"> <el-icon><Monitor /></el-icon><span>全网核心节点设备实时状态大屏</span></el-menu-item>
            <el-menu-item index="/logs"><el-icon><Document /></el-icon><span>历史预警日志</span></el-menu-item>
            <el-menu-item index="/ai-chat"><el-icon><ChatDotRound /></el-icon>
              <template #title><span>AI 智能排障 (RAG)</span><el-tag size="small" type="danger" style="margin-left: 10px;">Hot</el-tag></template>
            </el-menu-item>
            <el-menu-item index="4"><el-icon><Setting /></el-icon><span>系统权限管理</span></el-menu-item>
          </el-menu>
        </el-aside>
        <el-main style="background-color: #f0f2f5;">
          <el-card class="box-card" shadow="hover">
            <template #header><div class="card-header"><span style="font-weight: bold;">主控面板</span></div></template>
            <router-view />
          </el-card>
        </el-main>
      </el-container>
    </el-container>
  </div>
</template>

<style scoped>
.layout-container {
  height: 100vh;
}
.layout-header {
  background-color: #001529;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 20px;
}
.logo-title {
  color: #fff;
  font-size: 20px;
  font-weight: bold;
  display: flex;
  align-items: center;
  letter-spacing: 1px;
}
.layout-aside {
  background-color: #304156;
  transition: width 0.3s;
}
.el-menu-vertical {
  border-right: none;
}
.user-info { display: flex; align-items: center; }
</style>