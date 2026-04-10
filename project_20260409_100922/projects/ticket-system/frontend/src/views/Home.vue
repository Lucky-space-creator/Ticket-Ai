<template>
  <div class="home">
    <el-container>
      <!-- 顶部导航 -->
      <el-header class="header">
        <div class="header-content">
          <div class="logo">12306</div>
          <el-menu
              :default-active="activeMenu"
              mode="horizontal"
              router
              background-color="#1890FF"
              text-color="#ffffff"
              active-text-color="#ffffff"
          >
            <el-menu-item index="/home">首页</el-menu-item>
            <el-menu-item index="/search">车票查询</el-menu-item>
            <el-menu-item index="/orders">我的订单</el-menu-item>
          </el-menu>
          <div class="user-actions">
            <template v-if="userStore.token">
              <span class="username">{{ userStore.user?.realName || '用户' }}</span>
              <el-button type="primary" plain @click="handleLogout">退出</el-button>
            </template>
            <template v-else>
              <el-button type="primary" plain @click="$router.push('/login')">登录</el-button>
              <el-button type="success" plain @click="$router.push('/register')">注册</el-button>
            </template>
          </div>
        </div>
      </el-header>

      <!-- 主要内容 -->
      <el-main class="main">
        <div class="hero">
          <h1 class="title">12306购票系统</h1>
          <p class="subtitle">便捷、快速、安全的铁路票务服务平台</p>
          <div class="actions">
            <el-button type="primary" size="large" class="action-btn" @click="$router.push('/search')">
              立即购票
            </el-button>
            <el-button type="default" size="large" class="action-btn" @click="$router.push('/orders')">
              查看订单
            </el-button>
          </div>
        </div>

        <!-- 功能特色 -->
        <div class="features">
          <el-row :gutter="20">
            <el-col :span="8">
              <div class="feature-card">
                <el-icon size="40" color="#1890FF"><Tickets /></el-icon>
                <h3>在线购票</h3>
                <p>随时随地购买火车票，支持多种支付方式</p>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="feature-card">
                <el-icon size="40" color="#1890FF"><Clock /></el-icon>
                <h3>实时查询</h3>
                <p>实时查询车次信息和余票情况</p>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="feature-card">
                <el-icon size="40" color="#1890FF"><Service /></el-icon>
                <h3>智能客服</h3>
                <p>7×24小时在线客服，解答您的疑问</p>
              </div>
            </el-col>
          </el-row>
        </div>
      </el-main>
    </el-container>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const activeMenu = computed(() => route.path)

const handleLogout = () => {
  ElMessageBox.confirm('确定要退出登录吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    userStore.logout()
    router.push('/login')
  })
}
</script>

<style lang="scss" scoped>
.home {
  min-height: 100vh;
  background-color: #f5f5f5;
}

.header {
  background-color: #1890FF;
  padding: 0;
  border-bottom: none;

  .header-content {
    display: flex;
    align-items: center;
    justify-content: space-between;
    height: 60px;
    max-width: 1200px;
    margin: 0 auto;
    padding: 0 20px;

    .logo {
      font-size: 24px;
      font-weight: bold;
      color: #ffffff;
      margin-right: 40px;
    }

    .user-actions {
      display: flex;
      align-items: center;
      gap: 10px;

      .username {
        color: #ffffff;
        margin-right: 10px;
      }
    }
  }
}

.main {
  max-width: 1200px;
  margin: 0 auto;
  padding: 40px 20px;
}

.hero {
  text-align: center;
  padding: 80px 20px;
  background: linear-gradient(135deg, #1890FF 0%, #40A9FF 100%);
  border-radius: 8px;
  color: #ffffff;
  margin-bottom: 40px;

  .title {
    font-size: 48px;
    font-weight: bold;
    margin-bottom: 20px;
  }

  .subtitle {
    font-size: 20px;
    margin-bottom: 40px;
    opacity: 0.9;
  }

  .actions {
    display: flex;
    justify-content: center;
    gap: 20px;

    .el-button {
      font-size: 16px;
      padding: 12px 32px;
      border-radius: 4px;
    }

    .action-btn {
      background-color: rgba(255, 255, 255, 0.2);
      border: 2px solid #ffffff;
      color: #ffffff;

      &:hover {
        background-color: rgba(255, 255, 255, 0.3);
      }
    }

    .el-button--primary.action-btn {
      background-color: #ffffff;
      color: #1890FF;
      border-color: #ffffff;

      &:hover {
        background-color: #f0f0f0;
        border-color: #f0f0f0;
      }
    }
  }
}

.features {
  .feature-card {
    background-color: #ffffff;
    padding: 40px 20px;
    border-radius: 8px;
    text-align: center;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
    transition: transform 0.3s, box-shadow 0.3s;
    min-height: 220px;
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;

    &:hover {
      transform: translateY(-5px);
      box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
    }

    .el-icon {
      flex-shrink: 0;
    }

    h3 {
      margin: 20px 0 10px;
      font-size: 20px;
      color: #333333;
    }

    p {
      color: #666666;
      line-height: 1.6;
      margin: 0;
    }
  }
}
</style>
