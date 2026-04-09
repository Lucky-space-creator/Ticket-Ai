import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const request = axios.create({
  baseURL: '/api',
  timeout: 10000
})

// 请求拦截器
request.interceptors.request.use(
    config => {
      const userStore = useUserStore()
      if (userStore.token) {
        config.headers.Authorization = `Bearer ${userStore.token}`
      }
      return config
    },
    error => {
      return Promise.reject(error)
    }
)

// 响应拦截器
request.interceptors.response.use(
    response => {
      const res = response.data

      if (res.code === 200) {
        return res
      } else {
        ElMessage.error({
          message: res.message || '请求失败',
          duration: 1000
        })
        return Promise.reject(new Error(res.message || '请求失败'))
      }
    },
    error => {
      console.error('请求错误:', error)

      if (error.response) {
        switch (error.response.status) {
          case 401:
            ElMessage.error({
              message: '未授权，请登录',
              duration: 1000
            })
            localStorage.removeItem('token')
            localStorage.removeItem('user')
            window.location.href = '/login'
            break
          case 403:
            ElMessage.error({
              message: '拒绝访问',
              duration: 1000
            })
            break
          case 404:
            ElMessage.error({
              message: '资源不存在',
              duration: 1000
            })
            break
          case 500:
            ElMessage.error({
              message: '服务器错误',
              duration: 1000
            })
            break
          default:
            ElMessage.error({
              message: error.response.data?.message || '请求失败',
              duration: 1000
            })
        }
      } else {
        ElMessage.error({
          message: '网络错误，请检查网络连接',
          duration: 1000
        })
      }

      return Promise.reject(error)
    }
)

export default request
