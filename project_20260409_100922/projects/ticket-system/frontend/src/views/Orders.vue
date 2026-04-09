<template>
  <div class="orders">
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
            <span class="username">{{ userStore.user?.realName || '用户' }}</span>
            <el-button type="primary" plain @click="handleLogout">退出</el-button>
          </div>
        </div>
      </el-header>

      <!-- 主要内容 -->
      <el-main class="main">
        <h2 class="page-title">我的订单</h2>

        <div class="order-list" v-if="orders.length > 0">
          <el-card v-for="order in orders" :key="order.id" class="order-card">
            <div class="order-header">
              <div class="order-info">
                <span class="label">订单号:</span>
                <span class="value">{{ order.orderNo }}</span>
              </div>
              <el-tag :type="getStatusType(order.status)">
                {{ getStatusName(order.status) }}
              </el-tag>
            </div>

            <div class="order-content">
              <div class="train-info">
                <div class="train-no">{{ order.trainNo }}</div>
                <div class="route">
                  <span>{{ order.startStation }}</span>
                  <el-icon><Right /></el-icon>
                  <span>{{ order.endStation }}</span>
                </div>
                <div class="time">{{ order.trainDate }}</div>
              </div>

              <div class="price-info">
                <div class="price">¥{{ order.totalAmount }}</div>
                <div class="seat-type">{{ getSeatTypeName(order.seatType) }}</div>
              </div>
            </div>

            <div class="order-actions">
              <el-button
                  type="primary"
                  size="small"
                  @click="$router.push(`/order/${order.orderNo}`)"
              >
                查看详情
              </el-button>
              <el-button
                  v-if="order.status === 0"
                  type="success"
                  size="small"
                  @click="handlePay(order)"
              >
                立即支付
              </el-button>
              <el-button
                  v-if="order.status === 1"
                  type="warning"
                  size="small"
                  @click="handleRefund(order)"
              >
                申请退票
              </el-button>
            </div>
          </el-card>
        </div>

        <el-empty v-else description="暂无订单" />
      </el-main>
    </el-container>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

const router = useRouter()
const userStore = useUserStore()

const orders = ref([])

const loadOrders = async () => {
  try {
    const res = await request.get('/orders')
    orders.value = res.data
  } catch (error) {
    console.error('加载订单失败:', error)
  }
}

const getStatusName = (status) => {
  const statusNames = {
    0: '待支付',
    1: '已支付',
    2: '已退票',
    3: '已取消'
  }
  return statusNames[status] || '未知'
}

const getStatusType = (status) => {
  const types = {
    0: 'warning',
    1: 'success',
    2: 'info',
    3: 'info'
  }
  return types[status] || 'info'
}

const getSeatTypeName = (seatType) => {
  const seatTypeNames = {
    1: '商务座',
    2: '一等座',
    3: '二等座',
    4: '软卧',
    5: '硬卧',
    6: '硬座'
  }
  return seatTypeNames[seatType] || '未知'
}

const handlePay = async (order) => {
  try {
    await ElMessageBox.confirm(`确定要支付订单 ${order.orderNo} 吗？`, '确认支付', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    await request.post(`/orders/${order.orderNo}/pay`)
    ElMessage.success({
      message: '支付成功',
      duration: 1000
    })
    loadOrders()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('支付失败:', error)
    }
  }
}

const handleRefund = async (order) => {
  try {
    await ElMessageBox.confirm(`确定要退订单 ${order.orderNo} 吗？`, '确认退票', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    await request.post(`/orders/${order.orderNo}/refund`)
    ElMessage.success('退票成功')
    loadOrders()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('退票失败:', error)
    }
  }
}

const handleLogout = () => {
  userStore.logout()
  router.push('/login')
}

onMounted(() => {
  loadOrders()
})
</script>

<style lang="scss" scoped>
.orders {
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
  padding: 20px;
}

.page-title {
  font-size: 24px;
  font-weight: bold;
  color: #333333;
  margin-bottom: 20px;
}

.order-list {
  .order-card {
    margin-bottom: 20px;

    .order-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 15px;
      padding-bottom: 15px;
      border-bottom: 1px solid #eeeeee;

      .order-info {
        .label {
          color: #999999;
          margin-right: 5px;
        }

        .value {
          color: #333333;
          font-weight: bold;
        }
      }
    }

    .order-content {
      display: flex;
      justify-content: space-between;
      margin-bottom: 15px;

      .train-info {
        .train-no {
          font-size: 18px;
          font-weight: bold;
          color: #1890FF;
          margin-bottom: 5px;
        }

        .route {
          display: flex;
          align-items: center;
          gap: 5px;
          font-size: 16px;
          color: #333333;
          margin-bottom: 5px;
        }

        .time {
          color: #666666;
          font-size: 14px;
        }
      }

      .price-info {
        text-align: right;

        .price {
          font-size: 24px;
          font-weight: bold;
          color: #ff4d4f;
          margin-bottom: 5px;
        }

        .seat-type {
          color: #666666;
          font-size: 14px;
        }
      }
    }

    .order-actions {
      display: flex;
      gap: 10px;
      justify-content: flex-end;
    }
  }
}
</style>
