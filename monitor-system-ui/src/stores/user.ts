import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useUserStore = defineStore('user', () => {
  // 定义状态（C++ 里的全局变量）
  const token = ref(localStorage.getItem('sa-token') || '')
  const username = ref(localStorage.getItem('username') || '')

  // 登录成功后保存信息
  const setToken = (newToken: string, name: string) => {
    token.value = newToken
    username.value = name
    // 写入硬盘，防止刷新页面后丢失
    localStorage.setItem('sa-token', newToken)
    localStorage.setItem('username', name)
  }

  // 退出登录时清空
  const logout = () => {
    token.value = ''
    username.value = ''
    localStorage.removeItem('sa-token')
    localStorage.removeItem('username')
  }

  return { token, username, setToken, logout }
})