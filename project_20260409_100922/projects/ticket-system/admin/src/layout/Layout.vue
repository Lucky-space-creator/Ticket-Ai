<template>
  <div class="layout-container">
    <el-container>
      <el-aside width="200px">
        <div class="logo">
          <h2>12306后台</h2>
        </div>
        <el-menu
            router
            :default-active="$route.path"
            background-color="#304156"
            text-color="#fff"
            active-text-color="#409EFF"
        >
          <el-menu-item index="/dashboard">
            <el-icon><PieChart /></el-icon>
            <span>仪表盘</span>
          </el-menu-item>
          <el-menu-item index="/trains">
            <el-icon><Box /></el-icon>
            <span>车次管理</span>
          </el-menu-item>
          <el-menu-item index="/orders">
            <el-icon><List /></el-icon>
            <span>订单管理</span>
          </el-menu-item>
          <el-menu-item index="/users">
            <el-icon><User /></el-icon>
            <span>用户管理</span>
          </el-menu-item>
          <el-menu-item index="/faq">
            <el-icon><ChatDotRound /></el-icon>
            <span>FAQ问答管理</span>
          </el-menu-item>
          <el-menu-item index="/roles">
            <el-icon><Setting /></el-icon>
            <span>角色管理</span>
          </el-menu-item>
          <el-menu-item index="/permissions">
            <el-icon><Key /></el-icon>
            <span>权限管理</span>
          </el-menu-item>
          <el-menu-item index="/customer-service">
            <el-icon><Headset /></el-icon>
            <span>客服工作台</span>
          </el-menu-item>
        </el-menu>
      </el-aside>
      <el-container>
        <el-header>
          <div class="header-left">
            <span class="title">{{ $route.meta.title || '12306后台管理系统' }}</span>
          </div>
          <div class="header-right">
            <el-dropdown>
              <span class="user-info">
                <el-avatar :size="30" :src="userStore.userInfo.avatar || ''" />
                {{ userStore.userInfo.name || userStore.userInfo.phone }}
                <el-icon><ArrowDown /></el-icon>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="logout">退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </el-header>
        <el-main>
          <router-view />
        </el-main>
      </el-container>
    </el-container>
  </div>
</template>

<script setup>
import { useUserStore } from '@/stores/user'
import { useRouter } from 'vue-router'
import { PieChart, Box, List, User, ChatDotRound, ArrowDown, Setting, Key, Headset } from '@element-plus/icons-vue'

const userStore = useUserStore()
const router = useRouter()

const logout = () => {
  userStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.layout-container {
  height: 100vh;
}
.el-container {
  height: 100%;
}
.el-aside {
  background-color: #304156;
}
.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  border-bottom: 1px solid #2c3e50;
}
.el-menu {
  border-right: none;
}
.el-header {
  background-color: #fff;
  border-bottom: 1px solid #e6e6e6;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.header-left .title {
  font-size: 18px;
  font-weight: bold;
}
.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}
.el-main {
  background-color: #f5f7fa;
  padding: 20px;
}
</style>