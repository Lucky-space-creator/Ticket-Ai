<template>
  <div class="register">
    <div class="register-card">
      <div class="logo">12306</div>
      <h2 class="title">注册</h2>

      <el-form :model="form" :rules="rules" ref="formRef" class="register-form">
        <el-form-item prop="realName">
          <el-input
              v-model="form.realName"
              placeholder="请输入真实姓名"
              prefix-icon="User"
              size="large"
              autocomplete="off"
              clearable
          />
        </el-form-item>

        <el-form-item prop="idCard">
          <el-input
              v-model="form.idCard"
              placeholder="请输入身份证号"
              prefix-icon="Postcard"
              size="large"
              autocomplete="off"
              clearable
          />
        </el-form-item>

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
          />
        </el-form-item>

        <el-form-item prop="confirmPassword">
          <el-input
              v-model="form.confirmPassword"
              type="password"
              placeholder="请确认密码"
              prefix-icon="Lock"
              size="large"
              autocomplete="new-password"
              show-password
          />
        </el-form-item>

        <el-form-item>
          <el-button
              type="primary"
              size="large"
              class="register-button"
              :loading="loading"
              @click="handleRegister"
          >
            注册
          </el-button>
        </el-form-item>
      </el-form>

      <div class="footer">
        <span>已有账号？</span>
        <el-link type="primary" @click="$router.push('/login')">立即登录</el-link>
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
  realName: '',
  idCard: '',
  phone: '',
  password: '',
  confirmPassword: ''
})

// 页面加载时清空表单
onMounted(() => {
  form.value = {
    realName: '',
    idCard: '',
    phone: '',
    password: '',
    confirmPassword: ''
  }
  if (formRef.value) {
    formRef.value.clearValidate()
  }
})

const validateConfirmPassword = (rule, value, callback) => {
  if (value !== form.value.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules = {
  realName: [
    { required: true, message: '请输入真实姓名', trigger: 'blur' },
    { min: 2, max: 20, message: '姓名长度在 2 到 20 个字符', trigger: 'blur' }
  ],
  idCard: [
    { required: true, message: '请输入身份证号', trigger: 'blur' },
    { pattern: /^[1-9]\d{5}(18|19|20)\d{2}((0[1-9])|(1[0-2]))(([0-2][1-9])|10|20|30|31)\d{3}[0-9Xx]$/, message: '身份证号格式不正确', trigger: 'blur' }
  ],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度至少6位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
}

const handleRegister = async () => {
  await formRef.value.validate(async (valid) => {
    if (!valid) return

    loading.value = true
    try {
      const res = await request.post('/auth/register', {
        realName: form.value.realName,
        idCard: form.value.idCard,
        phone: form.value.phone,
        password: form.value.password
      })
      userStore.setToken(res.data.token)
      userStore.setUser(res.data.user)
      ElMessage.success({
        message: '注册成功',
        duration: 1000
      })

      // 注册成功后清空表单
      form.value = {
        realName: '',
        idCard: '',
        phone: '',
        password: '',
        confirmPassword: ''
      }
      formRef.value.clearValidate()

      router.push('/home')
    } catch (error) {
      console.error('注册失败:', error)
    } finally {
      loading.value = false
    }
  })
}
</script>

<style lang="scss" scoped>
.register {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1890FF 0%, #40A9FF 100%);
}

.register-card {
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

  .register-form {
    .register-button {
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
