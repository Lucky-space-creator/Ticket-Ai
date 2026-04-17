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
      // 优先使用登录响应中的用户信息
      if (res.data.user) {
        setUserInfo(res.data.user)
      } else {
        // 兼容性处理：如果登录响应中没有用户信息，则单独获取
        try {
          const userRes = await request.get('/api/user/profile')
          if (userRes.code === 200) {
            setUserInfo(userRes.data)
          }
        } catch (error) {
          console.warn('获取用户信息失败，但登录成功:', error)
        }
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