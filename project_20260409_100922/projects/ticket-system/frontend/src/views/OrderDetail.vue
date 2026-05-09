<template>
  <div class="order-detail">
    <el-container>
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

      <el-main class="main">
        <el-button icon="ArrowLeft" @click="$router.back()" style="margin-bottom: 20px;">
          返回
        </el-button>

        <el-skeleton v-if="loading" :rows="12" animated />

        <el-card v-else-if="order" class="order-card">
          <template #header>
            <div class="card-header">
              <span class="order-title">订单详情</span>
              <div class="header-tags">
                <el-tag v-if="routeTypeLabel" type="info" size="small">{{ routeTypeLabel }}</el-tag>
                <el-tag :type="getStatusType(order.status)">
                  {{ getStatusName(order.status) }}
                </el-tag>
              </div>
            </div>
          </template>

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
              <el-descriptions-item label="乘车日期">
                {{ formatDateOnly(order.trainDate) }}
              </el-descriptions-item>
              <el-descriptions-item label="创建时间">
                {{ formatDateTime(order.createdAt) }}
              </el-descriptions-item>
              <el-descriptions-item label="支付时间" v-if="order.payTime">
                {{ formatDateTime(order.payTime) }}
              </el-descriptions-item>
            </el-descriptions>
          </div>

          <!-- 全程摘要（与 legs 一致：首段发站 ~ 末段到站） -->
          <div class="order-section">
            <h3 class="section-title">车次信息</h3>
            <div class="route-summary" v-if="summaryFrom && summaryTo">
              <span class="summary-label">全程</span>
              <span class="summary-stations">{{ summaryFrom }} → {{ summaryTo }}</span>
            </div>

            <!-- 多段：每程单独展示车次与起终点时刻 -->
            <div v-if="isMultiLeg" class="legs-wrap">
              <el-alert
                  type="info"
                  :closable="false"
                  show-icon
                  class="transfer-hint"
                  title="本订单包含多段行程，请按乘车顺序乘车；换乘时请留意车站与发车时间。"
              />
              <div
                  v-for="(leg, idx) in sortedLegs"
                  :key="leg.id ?? `${leg.legSeq}-${leg.trainNo}`"
                  class="leg-card"
              >
                <div class="leg-card-head">
                  <span class="leg-index">第 {{ idx + 1 }} 程</span>
                  <span class="leg-train">{{ leg.trainNo }}</span>
                  <el-tag type="primary" size="small">{{ getTrainTypeName(leg.trainNo) }}</el-tag>
                </div>
                <div class="train-route leg-route">
                  <div class="station">
                    <div class="station-name">{{ leg.fromStation }}</div>
                    <div class="time">{{ formatTime(leg.plannedDepartAt) }}</div>
                    <div class="date">{{ formatDateOnly(leg.plannedDepartAt) }}</div>
                  </div>
                  <div class="arrow">→</div>
                  <div class="station">
                    <div class="station-name">{{ leg.toStation }}</div>
                    <div class="time">{{ formatTime(leg.plannedArriveAt) }}</div>
                    <div class="date">{{ formatDateOnly(leg.plannedArriveAt) }}</div>
                  </div>
                </div>
              </div>
            </div>

            <!-- 单段 -->
            <div v-else class="train-detail single">
              <div class="train-header">
                <div class="train-no">{{ primaryTrainNo }}</div>
                <el-tag type="primary">{{ getTrainTypeName(primaryTrainNo) }}</el-tag>
              </div>
              <div class="train-route">
                <div class="station">
                  <div class="station-name">{{ primaryFrom }}</div>
                  <div class="time">{{ formatTime(primaryDepart) }}</div>
                  <div class="date">{{ formatDateOnly(primaryDepart) }}</div>
                </div>
                <div class="arrow">→</div>
                <div class="station">
                  <div class="station-name">{{ primaryTo }}</div>
                  <div class="time">{{ formatTime(primaryArrive) }}</div>
                  <div class="date">{{ formatDateOnly(primaryArrive) }}</div>
                </div>
              </div>
            </div>
          </div>

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

        <el-empty v-else description="未找到订单或加载失败" />
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

/** 兼容 Jackson 对 LocalDate / LocalDateTime 的数组或对象序列化 */
function dayjsFromBackend(v) {
  if (v == null || v === '') return null
  if (typeof v === 'string') {
    const d = dayjs(v)
    return d.isValid() ? d : null
  }
  if (Array.isArray(v)) {
    if (v.length >= 6) {
      const [y, mo, d, h, mi, s] = v
      const dd = dayjs(new Date(y, mo - 1, d, h, mi, Math.floor(s)))
      return dd.isValid() ? dd : null
    }
    if (v.length >= 3) {
      const [y, mo, d] = v
      const dd = dayjs(new Date(y, mo - 1, d))
      return dd.isValid() ? dd : null
    }
  }
  if (typeof v === 'object') {
    if (v.year != null && v.monthValue != null && v.dayOfMonth != null) {
      const dd = dayjs(new Date(
          v.year,
          v.monthValue - 1,
          v.dayOfMonth,
          v.hour ?? 0,
          v.minute ?? 0,
          v.second ?? 0
      ))
      return dd.isValid() ? dd : null
    }
    if (v.year != null && v.month != null && v.day != null) {
      const dd = dayjs(new Date(v.year, v.month - 1, v.day))
      return dd.isValid() ? dd : null
    }
  }
  const d = dayjs(v)
  return d.isValid() ? d : null
}

const sortedLegs = computed(() => {
  const legs = order.value?.legs
  if (!Array.isArray(legs) || legs.length === 0) return []
  return [...legs].sort((a, b) => (a.legSeq ?? 0) - (b.legSeq ?? 0))
})

const isMultiLeg = computed(() => sortedLegs.value.length > 1)

const routeTypeLabel = computed(() => {
  const t = order.value?.routeType
  if (t === 'TRANSFER') return '异车中转'
  if (t === 'DIRECT') return '同车多段'
  if (isMultiLeg.value) return '多段行程'
  return ''
})

const summaryFrom = computed(() => {
  const legs = sortedLegs.value
  if (legs.length) return legs[0].fromStation
  return order.value?.startStation
})

const summaryTo = computed(() => {
  const legs = sortedLegs.value
  if (legs.length) return legs[legs.length - 1].toStation
  return order.value?.endStation
})

const primaryLeg = computed(() => sortedLegs.value[0])

const primaryTrainNo = computed(() => {
  if (primaryLeg.value) return primaryLeg.value.trainNo
  return order.value?.trainNo
})

const primaryFrom = computed(() => {
  if (primaryLeg.value) return primaryLeg.value.fromStation
  return order.value?.startStation
})

const primaryTo = computed(() => {
  if (primaryLeg.value) return primaryLeg.value.toStation
  return order.value?.endStation
})

const primaryDepart = computed(() => {
  if (primaryLeg.value?.plannedDepartAt != null) return primaryLeg.value.plannedDepartAt
  return order.value?.departTime
})

const primaryArrive = computed(() => {
  if (primaryLeg.value?.plannedArriveAt != null) return primaryLeg.value.plannedArriveAt
  return null
})

const formatDateTime = (v) => {
  const d = dayjsFromBackend(v)
  return d ? d.format('YYYY-MM-DD HH:mm:ss') : ''
}

const formatDateOnly = (v) => {
  const d = dayjsFromBackend(v)
  return d ? d.format('YYYY-MM-DD') : ''
}

const formatTime = (v) => {
  const d = dayjsFromBackend(v)
  return d ? d.format('HH:mm') : '-'
}

const loadOrderDetail = async () => {
  loading.value = true
  order.value = null
  try {
    const res = await request.get(`/orders/${route.params.orderNo}`)
    order.value = res.data
  } catch (error) {
    console.error('加载订单详情失败:', error)
    order.value = null
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
  return statusNames[status] ?? '未知'
}

const getStatusType = (status) => {
  const types = {
    0: 'warning',
    1: 'success',
    2: 'info',
    3: 'info'
  }
  return types[status] ?? 'info'
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
  return seatTypeNames[seatType] ?? '未知'
}

const getTrainTypeName = (trainNo) => {
  if (!trainNo) return '列车'
  if (trainNo.startsWith?.('G')) return '高铁'
  if (trainNo.startsWith?.('D')) return '动车'
  return '普快'
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
    flex-wrap: wrap;
    gap: 10px;

    .order-title {
      font-size: 18px;
      font-weight: bold;
      color: #333333;
    }

    .header-tags {
      display: flex;
      align-items: center;
      gap: 8px;
      flex-wrap: wrap;
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

    .route-summary {
      margin-bottom: 16px;
      padding: 10px 14px;
      background: #e6f4ff;
      border-radius: 6px;
      font-size: 15px;

      .summary-label {
        color: #666;
        margin-right: 10px;
        font-weight: 500;
      }

      .summary-stations {
        color: #1890FF;
        font-weight: 600;
      }
    }

    .transfer-hint {
      margin-bottom: 16px;
    }

    .legs-wrap {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }

    .leg-card {
      background-color: #f8f9fa;
      border-radius: 8px;
      padding: 16px 20px;
      border: 1px solid #e8e8e8;

      .leg-card-head {
        display: flex;
        align-items: center;
        gap: 10px;
        margin-bottom: 16px;
        flex-wrap: wrap;

        .leg-index {
          font-weight: bold;
          color: #333;
        }

        .leg-train {
          font-size: 20px;
          font-weight: bold;
          color: #1890FF;
        }
      }

      .leg-route {
        padding: 0 20px;
      }
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
        gap: 12px;

        .station {
          text-align: center;
          flex: 1;
          min-width: 0;

          .station-name {
            font-size: 18px;
            font-weight: bold;
            color: #333333;
            margin-bottom: 10px;
            word-break: break-all;
          }

          .time {
            font-size: 26px;
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
          font-size: 28px;
          color: #999999;
          flex-shrink: 0;
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
