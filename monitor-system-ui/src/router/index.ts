/**
 * 路由配置
 *
 * 页面路由:
 *   /login     → 登录页
 *   /          → 设备监控仪表盘
 *   /ai-chat   → AI 智能排障对话
 *
 * 全局前置守卫: 未登录用户访问非登录页时自动重定向到 /login
 */
import { createRouter, createWebHistory } from 'vue-router'
import DashboardView from '../views/DashboardView.vue'
import AiChatView from '../views/AiChatView.vue'
import LoginView from '../views/LoginView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: LoginView
    },
    {
      path: '/',
      name: 'dashboard',
      component: DashboardView
    },
    {
      path: '/ai-chat',
      name: 'aiChat',
      component: AiChatView
    }
  ]
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('sa-token')

  if (to.name !== 'login' && !token) {
    next({ name: 'login' })
  } else {
    next()
  }
})

export default router