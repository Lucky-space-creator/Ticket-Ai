<template>
  <div class="order-detail">
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
        <el-button icon="ArrowLeft" @click="$router.back()" style="margin-bottom: 20px;">
          返回
        </el-button>

        <el-card v-if="order" class="order-card">
          <template #header>
            <div class="card-header">
              <span class="order-title">订单详情</span>
              <el-tag :type="getStatusType(order.status)">
                {{ getStatusName(order.status) }}
              </el-tag>
            </div>
          </template>

          <!-- 订单基本信息 -->
          <div class="order-section">
            <h3 class="section-title">订单信息</h3>
            <el-descriptions :column="2" border>
              <el-descriptions-item label="订单号">
                {{ order.orderNo }}
              </el-descriptions-item>
              <el-descriptions-item label="订单状态">
                <el-tag :type="getStatusType(order.status)" size="small">
                  {{ getStatusName(order.status) }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="创建时间">
                {{ formatDate(order.createdAt) }}
              </el-descriptions-item>
              <el-descriptions-item label="支付时间" v-if="order.payTime">
                {{ formatDate(order.payTime) }}
              </el-descriptions-item>
            </el-descriptions>
          </div>

          <!-- 车次信息 -->
          <div class="order-section">
            <h3 class="section-title">车次信息</h3>
            <div class="train-detail">
              <div class="train-header">
                <div class="train-no">{{ order.trainNo }}</div>
                <el-tag type="primary">{{ getTrainTypeName() }}</el-tag>
              </div>
              <div class="train-route">
                <div class="station">
                  <div class="station-name">{{ order.startStation }}</div>
                  <div class="time">{{ formatTime(order.departTime) }}</div>
                  <div class="date">{{ formatDate(order.trainDate) }}</div>
                </div>
                <div class="arrow">→</div>
                <div class="station">
                  <div class="station-name">{{ order.endStation }}</div>
                  <div class="date">{{ formatDate(order.trainDate) }}</div>
                </div>
              </div>
            </div>
          </div>

          <!-- 票务信息 -->
          <div class="order-section">
            <h3 class="section-title">票务信息</h3>
            <el-descriptions :column="2" border>
              <el-descriptions-item label="席别">
                {{ getSeatTypeName(order.seatType) }}
              </el-descriptions-item>
              <el-descriptions-item label="总金额">
                <span class="price">¥{{ order.totalAmount }}</span>
              </el-descriptions-item>
            </el-descriptions>
          </div>

          <!-- 操作按钮 -->
          <div class="order-actions" v-if="order.status === 0">
            <el-button type="success" @click="handlePay">
              立即支付
            </el-button>
            <el-button type="danger" @click="handleCancel">
              取消订单
            </el-button>
          </div>
          <div class="order-actions" v-else-if="order.status === 1">
            <el-button type="warning" @click="handleRefund">
              申请退票
            </el-button>
          </div>
        </el-card>

        <el-skeleton v-else :rows="10" animated />
      </el-main>
    </el-container>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import dayjs from 'dayjs'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const order = ref(null)
const loading = ref(false)

const activeMenu = computed(() => route.path)

const loadOrderDetail = async () => {
  loading.value = true
  try {
    const res = await request.get(`/orders/${route.params.orderNo}`)
    order.value = res.data
  } catch (error) {
    console.error('加载订单详情失败:', error)
    ElMessage.error({
      message: '加载订单详情失败',
      duration: 1000
    })
  } finally {
    loading.value = false
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

const getTrainTypeName = () => {
  // 这里可以根据 trainNo 判断车次类型
  // 简化处理，实际应该从后端获取
  if (order.value?.trainNo?.startsWith('G')) {
    return '高铁'
  } else if (order.value?.trainNo?.startsWith('D')) {
    return '动车'
  } else {
    return '普快'
  }
}

const formatDate = (date) => {
  return dayjs(date).format('YYYY-MM-DD HH:mm:ss')
}

const formatTime = (dateTime) => {
  return dayjs(dateTime).format('HH:mm')
}

const handlePay = async () => {
  try {
    await ElMessageBox.confirm(`确定要支付订单 ${order.value.orderNo} 吗？`, '确认支付', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    await request.post(`/orders/${order.value.orderNo}/pay`)
    ElMessage.success({
      message: '支付成功',
      duration: 1000
    })
    loadOrderDetail()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('支付失败:', error)
    }
  }
}

const handleRefund = async () => {
  try {
    await ElMessageBox.confirm(`确定要退订单 ${order.value.orderNo} 吗？退票后票款将原路返回。`, '确认退票', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    await request.post(`/orders/${order.value.orderNo}/refund`)
    ElMessage.success({
      message: '退票成功',
      duration: 1000
    })
    loadOrderDetail()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('退票失败:', error)
    }
  }
}

const handleCancel = async () => {
  try {
    await ElMessageBox.confirm(`确定要取消订单 ${order.value.orderNo} 吗？`, '确认取消', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    ElMessage.info({
      message: '取消订单功能开发中',
      duration: 1000
    })
  } catch (error) {
    if (error !== 'cancel') {
      console.error('取消订单失败:', error)
    }
  }
}

const handleLogout = () => {
  userStore.logout()
  router.push('/login')
}

onMounted(() => {
  loadOrderDetail()
})
</script>

<style lang="scss" scoped>
.order-detail {
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

.order-card {
  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .order-title {
      font-size: 18px;
      font-weight: bold;
      color: #333333;
    }
  }

  .order-section {
    margin-bottom: 30px;

    .section-title {
      font-size: 16px;
      font-weight: bold;
      color: #333333;
      margin-bottom: 15px;
    }

    .train-detail {
      background-color: #f8f9fa;
      padding: 20px;
      border-radius: 8px;

      .train-header {
        display: flex;
        align-items: center;
        gap: 10px;
        margin-bottom: 20px;

        .train-no {
          font-size: 24px;
          font-weight: bold;
          color: #1890FF;
        }
      }

      .train-route {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 0 40px;

        .station {
          text-align: center;

          .station-name {
            font-size: 20px;
            font-weight: bold;
            color: #333333;
            margin-bottom: 10px;
          }

          .time {
            font-size: 28px;
            font-weight: bold;
            color: #1890FF;
            margin-bottom: 5px;
          }

          .date {
            color: #666666;
            font-size: 14px;
          }
        }

        .arrow {
          font-size: 32px;
          color: #999999;
        }
      }
    }

    .price {
      font-size: 24px;
      font-weight: bold;
      color: #ff4d4f;
    }
  }

  .order-actions {
    display: flex;
    gap: 15px;
    padding-top: 20px;
    border-top: 1px solid #e4e7ed;
  }
}
</style>
