<template>
  <div class="search">
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
        <div class="search-box">
          <el-card>
            <el-form :model="searchForm" inline>
              <el-form-item label="出发站">
                <el-select
                    v-model="searchForm.startStation"
                    placeholder="请选择出发站"
                    filterable
                    clearable
                    style="width: 200px"
                >
                  <el-option v-for="name in stationOptions" :key="name" :label="name" :value="name"/>
                </el-select>
              </el-form-item>
              <el-form-item label="到达站">
                <el-select
                    v-model="searchForm.endStation"
                    placeholder="请选择到达站"
                    filterable
                    clearable
                    style="width: 200px"
                >
                  <el-option v-for="name in stationOptions" :key="'e-' + name" :label="name" :value="name"/>
                </el-select>
              </el-form-item>
              <el-form-item label="乘车日期">
                <el-date-picker
                    v-model="searchForm.trainDate"
                    type="date"
                    placeholder="选择日期"
                    format="YYYY-MM-DD"
                    value-format="YYYY-MM-DD"
                    :disabled-date="disabledDate"
                />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="handleSearch" :loading="loading">
                  查询
                </el-button>
                <el-button @click="handleReset">清空</el-button>
              </el-form-item>
            </el-form>
          </el-card>
        </div>

        <!-- 联程/直达方案（searchRoutes） -->
        <div class="train-list" v-if="routeOptions.length > 0">
          <el-card v-for="(opt, idx) in routeOptions" :key="opt.routeSku + '-' + idx" class="train-card">
            <div class="train-info">
              <div class="train-header">
                <el-tag type="success">{{ opt.routeType }}</el-tag>
                <span class="route-sku">行程 SKU：{{ opt.routeSku }}</span>
              </div>
              <div v-for="leg in opt.legs" :key="leg.segmentId" class="leg-line">
                <strong>{{ leg.trainNo }}</strong>
                {{ leg.fromStation }} → {{ leg.toStation }}
                <span class="muted">{{ leg.startTime }} — {{ leg.endTime }}</span>
                <span>¥{{ leg.price }}</span>
              </div>
              <div class="route-summary">
                <span>合计 <b>¥{{ opt.totalPrice }}</b></span>
                <span>全程余票（各段最小）<el-tag :type="getSeatsColor(opt.minAvailableSeats)">{{ opt.minAvailableSeats }}</el-tag></span>
                <span v-if="opt.totalDurationMinutes != null">约 {{ opt.totalDurationMinutes }} 分钟</span>
              </div>
            </div>
            <div class="ticket-info">
              <el-button
                  type="primary"
                  :disabled="opt.minAvailableSeats <= 0"
                  @click="handleBuyRoute(opt)"
              >
                购买此方案
              </el-button>
            </div>
          </el-card>
        </div>

        <el-empty v-else-if="!loading && routeOptions.length === 0" :description="emptyHint" />
      </el-main>
    </el-container>

    <!-- 购票对话框 -->
    <el-dialog
        v-model="orderDialogVisible"
        title="订单确认"
        width="500px"
        :close-on-click-modal="!paying"
    >
      <div class="order-summary" v-if="selectedTrain && selectedStock">
        <div class="summary-item">
          <span class="label">车次：</span>
          <span class="value">{{ selectedTrain.trainNo }}</span>
        </div>
        <div class="summary-item">
          <span class="label">日期：</span>
          <span class="value">{{ selectedTrain.trainDate }}</span>
        </div>
        <div class="summary-item">
          <span class="label">行程：</span>
          <span class="value">{{ selectedTrain.startStation }} → {{ selectedTrain.endStation }}</span>
        </div>
        <div class="summary-item">
          <span class="label">席别：</span>
          <span class="value">{{ selectedStock.seatTypeName }}</span>
        </div>
        <div class="summary-item">
          <span class="label">单价：</span>
          <span class="value price">¥{{ selectedStock.price }}</span>
        </div>
      </div>

      <el-divider />

      <div class="passenger-section">
        <div class="section-title">选择乘客（最多可选{{ maxPassengers }}人）</div>

        <el-checkbox-group v-model="selectedPassengerIds" :disabled="paying" :max="maxPassengers">
          <!-- 本人选项 -->
          <div class="passenger-item self-item" v-if="currentUser?.realName">
            <el-checkbox :label="'self'" :disabled="!currentUser?.idCard">
              <div class="passenger-content">
                <div class="passenger-name">
                  <el-tag size="small" type="warning">本人</el-tag>
                  <span>{{ currentUser.realName }}</span>
                </div>
                <div class="passenger-idcard" v-if="currentUser?.idCard">
                  {{ formatIdCard(currentUser.idCard) }}
                </div>
                <div class="passenger-tip" v-else>
                  <span style="color: #ff4d4f;">请先在个人中心完善身份信息</span>
                </div>
              </div>
            </el-checkbox>
          </div>

          <!-- 联系人列表 -->
          <div class="passenger-item" v-for="passenger in passengers" :key="passenger.id">
            <el-checkbox :label="passenger.id">
              <div class="passenger-content">
                <div class="passenger-name">{{ passenger.name }}</div>
                <div class="passenger-idcard">{{ formatIdCard(passenger.idCard) }}</div>
              </div>
            </el-checkbox>
          </div>
        </el-checkbox-group>

        <!-- 空状态 -->
        <div class="empty-passengers" v-if="!currentUser?.realName && passengers.length === 0">
          <el-empty description="暂无乘客信息，请先完善个人身份信息或添加联系人" :image-size="60" />
        </div>
      </div>

      <!-- 费用汇总 -->
      <div class="total-section">
        <div class="total-info">
          <span>已选 {{ selectedPassengerIds.length }} 张票</span>
          <span class="unit-price">单价 ¥{{ selectedStock?.price || 0 }}</span>
        </div>
        <div class="total-price-wrapper">
          <span>合计：</span>
          <span class="total-price">¥{{ totalPrice }}</span>
        </div>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="orderDialogVisible = false" :disabled="paying">取消</el-button>
          <el-button
              type="primary"
              @click="handleSubmitOrder"
              :loading="paying"
              :disabled="selectedPassengerIds.length === 0"
          >
            {{ paying ? '提交中...' : '确认购票' }}
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import dayjs from 'dayjs'

const router = useRouter()
const userStore = useUserStore()

const searchForm = reactive({
  startStation: '',
  endStation: '',
  trainDate: dayjs().format('YYYY-MM-DD')
})

const routeOptions = ref([])
const loading = ref(false)
/** 站点列表（来自 GET /api/trains/stations） */
const stationOptions = ref([])

const emptyHint = computed(() => '请先填写出发站和到达站，再点击查询；查询后将在下方展示可选方案')


// 购票相关
const orderDialogVisible = ref(false)
const selectedTrain = ref(null)
const selectedStock = ref(null)
const passengers = ref([])
const selectedPassengerIds = ref([])
const paying = ref(false)

// 防重复提交锁（防止快速多次点击）
let isSubmitting = false

// 当前用户信息（从API获取的最新数据）
const currentUser = ref(null)

// 最多购票数量
const maxPassengers = 5

// 计算总价
const totalPrice = computed(() => {
  if (!selectedStock.value) return '0.00'
  return (selectedStock.value.price * selectedPassengerIds.value.length).toFixed(2)
})

const disabledDate = (time) => {
  return time.getTime() < Date.now() - 8.64e7
}

const handleSearch = async () => {
  const from = searchForm.startStation?.trim()
  const to = searchForm.endStation?.trim()
  if (!from || !to) {
    ElMessage.warning('请先选择出发站和到达站后再查询')
    routeOptions.value = []
    return
  }
  if (from === to) {
    ElMessage.warning('出发站与到达站不能相同')
    routeOptions.value = []
    return
  }
  loading.value = true
  routeOptions.value = []
  try {
    const res = await request.get('/trains/searchRoutes', {
      params: {
        startStation: from,
        endStation: to,
        trainDate: searchForm.trainDate,
        seatType: 3
      }
    })
    routeOptions.value = Array.isArray(res.data) ? res.data : []
    if (routeOptions.value.length === 0) {
      ElMessage.info({ message: '未找到联程/直达方案，可尝试更换站点或日期', duration: 1500 })
    } else {
      ElMessage.success({
        message: `找到 ${routeOptions.value.length} 条出行方案`,
        duration: 1000
      })
    }
  } catch (error) {
    console.error('查询失败:', error)
    ElMessage.error({
      message: '查询失败，请稍后重试',
      duration: 1000
    })
  } finally {
    loading.value = false
  }
}

const handleReset = () => {
  searchForm.startStation = ''
  searchForm.endStation = ''
  searchForm.trainDate = dayjs().format('YYYY-MM-DD')
  routeOptions.value = []
}

// 获取乘客列表（排除本人，避免重复显示）
const loadPassengers = async () => {
  try {
    // 先获取最新的用户信息
    let userIdCard = ''
    try {
      const profileRes = await request.get('/user/profile')
      if (profileRes.data) {
        // 同时更新 store 和本地变量
        userStore.setUser(profileRes.data)
        currentUser.value = profileRes.data
        userIdCard = profileRes.data.idCard || ''
        console.log('获取到用户信息:', profileRes.data)
      }
    } catch (e) {
      console.error('获取用户信息失败:', e)
    }

    // 获取联系人列表
    const res = await request.get('/passengers')
    console.log('获取到联系人:', res.data)

    // 过滤掉与本人相同的数据（使用解密后的身份证比较）
    passengers.value = (res.data || []).filter(p => {
      // 如果联系人身份证和本人身份证相同，则排除
      if (userIdCard && p.idCard === userIdCard) {
        console.log('过滤掉重复联系人:', p.name)
        return false
      }
      return true
    })
    console.log('过滤后联系人:', passengers.value)
  } catch (error) {
    console.error('获取乘客列表失败:', error)
    passengers.value = []
  }
}

// 格式化身份证号（隐藏中间部分）
const formatIdCard = (idCard) => {
  if (!idCard) return ''
  // 去掉所有空格和不可见字符
  idCard = idCard.trim().replace(/[\s\u00A0]/g, '')
  // 标准的18位身份证：前3位 + 11个* + 后4位
  if (/^\d{17}[\dXx]$/.test(idCard)) {
    return idCard.replace(/(\d{3})\d{11}(\d{4})/, '$1***********$2')
  }
  // 15位老式身份证
  if (/^\d{15}$/.test(idCard)) {
    return idCard.replace(/(\d{3})\d{8}(\d{4})/, '$1********$2')
  }
  // 如果是 Base64 编码（加密数据意外未解码），尝试解码
  if (/^[A-Za-z0-9+/=]{20,}$/.test(idCard)) {
    try {
      const decoded = atob(idCard)
      if (/^\d{17}[\dXx]$/.test(decoded)) {
        return decoded.replace(/(\d{3})\d{11}(\d{4})/, '$1***********$2')
      }
      if (/^\d{15}$/.test(decoded)) {
        return decoded.replace(/(\d{3})\d{8}(\d{4})/, '$1********$2')
      }
    } catch (e) {
      // Base64 解码失败，忽略
    }
  }
  // 如果包含数字但格式不标准，尝试提取连续数字序列
  const numberMatch = idCard.match(/\d{15,18}/)
  if (numberMatch) {
    const extracted = numberMatch[0]
    if (/^\d{17}[\dXx]$/.test(extracted)) {
      return extracted.replace(/(\d{3})\d{11}(\d{4})/, '$1***********$2')
    }
    if (/^\d{15}$/.test(extracted)) {
      return extracted.replace(/(\d{3})\d{8}(\d{4})/, '$1********$2')
    }
    // 超长数字串：显示前3位和后4位
    if (extracted.length >= 7) {
      return extracted.substring(0, 3) + '****' + extracted.substring(extracted.length - 4)
    }
  }
  // 最终兜底：完全无法识别的值，返回"证件号已加密"
  return '******'
}

// 联程方案购票
const handleBuyRoute = async (opt) => {
  const first = opt.legs[0]
  selectedTrain.value = {
    id: first.segmentId,
    trainNo: opt.legs.map(l => l.trainNo).join(' / '),
    startStation: searchForm.startStation,
    endStation: searchForm.endStation,
    trainDate: searchForm.trainDate,
    routeSku: opt.routeSku,
    routeType: opt.routeType,
    legs: opt.legs.map(l => ({
      segmentId: l.segmentId,
      trainNo: l.trainNo,
      fromStation: l.fromStation,
      toStation: l.toStation,
      segmentPrice: l.price
    })),
    isRoute: true
  }
  selectedStock.value = {
    seatType: 3,
    seatTypeName: getSeatTypeName(3),
    price: opt.totalPrice,
    availableSeats: opt.minAvailableSeats
  }
  selectedPassengerIds.value = []
  await loadPassengers()
  orderDialogVisible.value = true
}

// 提交订单（MQ异步削峰模式）
// 改造前：同步等待DB写入完成 → 拿到orderNo → 支付
// 改造后：提交入队(立即返回) → 轮询查询结果 → 成功后支付
const handleSubmitOrder = async () => {
  if (selectedPassengerIds.value.length === 0) {
    ElMessage.warning('请选择至少一位乘客')
    return
  }

  // 防重复提交
  if (isSubmitting) {
    ElMessage.warning('正在处理中，请勿重复点击')
    return
  }
  isSubmitting = true

  // 检查本人是否已选但未完善身份信息
  if (selectedPassengerIds.value.includes('self') && !currentUser.value?.idCard) {
    ElMessage.warning('请先完善个人身份信息')
    isSubmitting = false
    return
  }

  paying.value = true

  try {
    // 构建乘客列表
    const items = []
    for (const id of selectedPassengerIds.value) {
      if (id === 'self') {
        items.push({
          passengerName: currentUser.value.realName,
          idCard: currentUser.value.idCard
        })
      } else {
        const passenger = passengers.value.find(p => p.id === id)
        if (passenger) {
          items.push({
            passengerName: passenger.name,
            idCard: passenger.idCard
          })
        }
      }
    }

    // Step 1: 提交下单请求（快速入队，<10ms返回）
    const orderPayload = {
      trainDate: selectedTrain.value.trainDate,
      startStation: selectedTrain.value.startStation,
      endStation: selectedTrain.value.endStation,
      seatType: selectedStock.value.seatType,
      items: items
    }
    if (selectedTrain.value.isRoute) {
      orderPayload.trainId = selectedTrain.value.legs[0].segmentId
      orderPayload.routeSku = selectedTrain.value.routeSku
      orderPayload.routeType = selectedTrain.value.routeType
      orderPayload.legs = selectedTrain.value.legs
    } else {
      orderPayload.trainId = selectedTrain.value.id
    }

    const submitRes = await request.post('/orders', orderPayload)

    const requestId = submitRes.data.requestId
    const initialStatus = submitRes.data.status
    let orderNo = null

    if (!requestId || initialStatus !== 'PROCESSING') {
      throw new Error(submitRes.data?.message || '下单提交异常，请重试')
    }

    ElMessage.info('订单已提交，正在处理中...')

    const MAX_POLL_COUNT = 30
    const POLL_INTERVAL_MS = 1000

    for (let i = 0; i < MAX_POLL_COUNT; i++) {
      await new Promise(resolve => setTimeout(resolve, POLL_INTERVAL_MS))

      const pollRes = await request.get(`/orders/queue/${requestId}`)
      const status = pollRes.data.status

      if (status === 'SUCCESS') {
        orderNo = pollRes.data.orderNo
        ElMessage.success('下单成功！')
        break
      } else if (status === 'FAILED') {
        throw new Error(pollRes.data.errorMessage || '下单失败')
      } else if (status === 'EXPIRED') {
        throw new Error('查询结果已过期，请重新下单')
      }
    }

    if (!orderNo) {
      throw new Error('订单处理超时，请在"我的订单"页面查看结果')
    }

    // Step 3: 发起支付
    ElMessage.info('正在发起支付...')
    await new Promise(resolve => setTimeout(resolve, 1500))
    await request.post(`/orders/${orderNo}/pay`)

    ElMessage.success({
      message: '支付成功！',
      duration: 2000
    })

    orderDialogVisible.value = false
    setTimeout(() => {
      router.push('/orders')
    }, 1000)

  } catch (error) {
    console.error('购票失败:', error)
    ElMessage.error(error.message || '购票失败，请稍后重试')
  } finally {
    paying.value = false
    isSubmitting = false
  }
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

const getSeatsColor = (seats) => {
  if (seats <= 0) return 'info'
  if (seats < 10) return 'warning'
  return 'success'
}

const handleLogout = () => {
  userStore.logout()
  router.push('/login')
}

const loadStations = async () => {
  try {
    const res = await request.get('/trains/stations')
    stationOptions.value = Array.isArray(res.data) ? res.data : []
    if (stationOptions.value.length === 0) {
      ElMessage.warning({ message: '暂无站点数据，请在数据库 station 表中维护站点后再试', duration: 2500 })
    }
  } catch {
    stationOptions.value = []
  }
}

onMounted(() => {
  loadStations()
})
</script>

<style lang="scss" scoped>
.search {
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

.search-box {
  margin-bottom: 20px;
}

.train-list {
  .train-card {
    margin-bottom: 20px;

    .train-info {
      margin-bottom: 20px;

      .train-header {
        display: flex;
        align-items: center;
        gap: 10px;
        margin-bottom: 15px;

        .train-no {
          font-size: 20px;
          font-weight: bold;
          color: #1890FF;
        }
      }

      .train-route {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 0 20px;

        .station {
          text-align: center;

          .station-name {
            font-size: 18px;
            font-weight: bold;
            margin-bottom: 5px;
          }

          .time {
            font-size: 24px;
            color: #1890FF;
          }
        }

        .arrow {
          font-size: 24px;
          color: #999999;
        }
      }
    }
  }
}

// 订单确认对话框样式
.order-summary {
  .summary-item {
    display: flex;
    align-items: center;
    margin-bottom: 12px;

    .label {
      width: 70px;
      color: #666;
    }

    .value {
      color: #333;
      font-weight: 500;

      &.price {
        color: #ff4d4f;
        font-size: 18px;
      }
    }
  }
}

.passenger-section {
  .section-title {
    font-weight: 500;
    color: #333;
    margin-bottom: 15px;
  }

  .passenger-item {
    padding: 12px 15px;
    border: 1px solid #e8e8e8;
    border-radius: 6px;
    margin-bottom: 10px;
    transition: all 0.3s;

    &:hover {
      border-color: #1890FF;
      background-color: #f0f7ff;
    }

    &.self-item {
      border-color: #ffc069;
      background-color: #fffbe6;

      &:hover {
        border-color: #ffc069;
        background-color: #fffbe6;
      }
    }

    .passenger-content {
      display: flex;
      flex-direction: column;
      gap: 4px;

      .passenger-name {
        display: flex;
        align-items: center;
        gap: 8px;
        font-weight: 500;
        color: #333;
      }

      .passenger-idcard {
        font-size: 12px;
        color: #999;
      }

      .passenger-tip {
        font-size: 12px;
      }
    }
  }

  .empty-passengers {
    padding: 20px 0;
  }
}

.total-section {
  margin-top: 20px;
  padding-top: 15px;
  border-top: 1px solid #eee;

  .total-info {
    display: flex;
    justify-content: space-between;
    color: #666;
    font-size: 14px;
    margin-bottom: 10px;

    .unit-price {
      color: #999;
    }
  }

  .total-price-wrapper {
    display: flex;
    justify-content: flex-end;
    align-items: center;
    font-size: 16px;

    .total-price {
      margin-left: 10px;
      font-size: 28px;
      font-weight: bold;
      color: #ff4d4f;
    }
  }
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.route-sku {
  margin-left: 12px;
  color: #666;
  font-size: 13px;
}

.leg-line {
  margin: 10px 0;
  line-height: 1.6;

  .muted {
    color: #888;
    margin: 0 10px;
    font-size: 13px;
  }
}

.route-summary {
  margin-top: 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  align-items: center;
  font-size: 14px;
}
</style>
