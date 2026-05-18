import { createRouter, createWebHistory } from 'vue-router'
import DashboardView from '../views/DashboardView.vue'
import AiChatView from '../views/AiChatView.vue'
import LoginView from '../views/LoginView.vue' // 我们马上要建的登录页

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

// 【核心防线】：全局前置路由守卫
router.beforeEach((to, from, next) => {
  // 1. 去本地存储看有没有通行证
  const token = localStorage.getItem('sa-token')

  // 2. 如果他要去的地方不是登录页，且没有通行证
  if (to.name !== 'login' && !token) {
    // 强制踹回登录页
    next({ name: 'login' })
  } else {
    // 否则，直接放行
    next()
  }
})

export default router