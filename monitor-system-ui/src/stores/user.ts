/**
 * 用户状态管理 (Pinia Store)
 *
 * 管理登录态和用户信息:
 *   - token: Sa-Token 令牌，持久化到 localStorage
 *   - username: 当前登录用户名
 *   - setToken: 登录成功后保存令牌和用户名
 *   - logout: 退出登录时清空状态和本地存储
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('sa-token') || '')
  const username = ref(localStorage.getItem('username') || '')

  const setToken = (newToken: string, name: string) => {
    token.value = newToken
    username.value = name
    localStorage.setItem('sa-token', newToken)
    localStorage.setItem('username', name)
  }

  const logout = () => {
    token.value = ''
    username.value = ''
    localStorage.removeItem('sa-token')
    localStorage.removeItem('username')
  }

  return { token, username, setToken, logout }
})