import { defineStore } from 'pinia'
import { ref } from 'vue'
import request from '@/utils/request'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('admin_token') || '')
  const userInfo = ref(JSON.parse(localStorage.getItem('admin_user') || '{}'))

  const setToken = (newToken) => {
    token.value = newToken
    localStorage.setItem('admin_token', newToken)
  }

  const setUserInfo = (info) => {
    userInfo.value = info
    localStorage.setItem('admin_user', JSON.stringify(info))
  }

  const logout = () => {
    token.value = ''
    userInfo.value = {}
    localStorage.removeItem('admin_token')
    localStorage.removeItem('admin_user')
  }

  const login = async (phone, password) => {
    const res = await request.post('/api/auth/login', { phone, password })
    if (res.code === 200) {
      setToken(res.data.token)
      // 获取用户信息
      const userRes = await request.get('/api/user/profile')
      if (userRes.code === 200) {
        setUserInfo(userRes.data)
      }
      return true
    }
    return false
  }

  return {
    token,
    userInfo,
    setToken,
    setUserInfo,
    logout,
    login
  }
})