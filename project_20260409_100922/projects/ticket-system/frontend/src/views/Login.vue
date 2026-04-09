<template>
  <div class="login">
    <div class="login-card">
      <div class="logo">12306</div>
      <h2 class="title">登录</h2>

      <el-form :model="form" :rules="rules" ref="formRef" class="login-form">
        <el-form-item prop="phone">
          <el-input
              v-model="form.phone"
              placeholder="请输入手机号"
              prefix-icon="Phone"
              size="large"
              autocomplete="off"
              clearable
          />
        </el-form-item>

        <el-form-item prop="password">
          <el-input
              v-model="form.password"
              type="password"
              placeholder="请输入密码"
              prefix-icon="Lock"
              size="large"
              autocomplete="new-password"
              show-password
              @keyup.enter="handleLogin"
          />
        </el-form-item>

        <el-form-item>
          <el-button
              type="primary"
              size="large"
              class="login-button"
              :loading="loading"
              @click="handleLogin"
          >
            登录
          </el-button>
        </el-form-item>
      </el-form>

      <div class="footer">
        <span>还没有账号？</span>
        <el-link type="primary" @click="$router.push('/register')">立即注册</el-link>
      </div>

      <div class="back">
        <el-link type="info" @click="$router.push('/home')">返回首页</el-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref()
const loading = ref(false)

const form = ref({
  phone: '',
  password: ''
})

// 页面加载时清空表单
onMounted(() => {
  form.value = {
    phone: '',
    password: ''
  }
  // 如果表单有 reset 方法，也可以使用
  if (formRef.value) {
    formRef.value.clearValidate()
  }
})

const rules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度至少6位', trigger: 'blur' }
  ]
}

const handleLogin = async () => {
  await formRef.value.validate(async (valid) => {
    if (!valid) return

    loading.value = true
    try {
      const res = await request.post('/auth/login', form.value)
      userStore.setToken(res.data.token)
      userStore.setUser(res.data.user)
      ElMessage.success({
        message: '登录成功',
        duration: 1000
      })

      // 登录成功后清空表单
      form.value = {
        phone: '',
        password: ''
      }
      formRef.value.clearValidate()

      router.push('/home')
    } catch (error) {
      console.error('登录失败:', error)
    } finally {
      loading.value = false
    }
  })
}
</script>

<style lang="scss" scoped>
.login {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1890FF 0%, #40A9FF 100%);
}

.login-card {
  width: 400px;
  background-color: #ffffff;
  border-radius: 8px;
  padding: 40px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);

  .logo {
    font-size: 32px;
    font-weight: bold;
    color: #1890FF;
    text-align: center;
    margin-bottom: 10px;
  }

  .title {
    font-size: 24px;
    color: #333333;
    text-align: center;
    margin-bottom: 30px;
  }

  .login-form {
    .login-button {
      width: 100%;
    }
  }

  .footer {
    text-align: center;
    margin-top: 20px;
    color: #666666;
  }

  .back {
    text-align: center;
    margin-top: 10px;
  }
}
</style>
