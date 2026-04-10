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
                <el-input
                    v-model="searchForm.startStation"
                    placeholder="请输入出发站"
                    clearable
                />
              </el-form-item>
              <el-form-item label="到达站">
                <el-input
                    v-model="searchForm.endStation"
                    placeholder="请输入到达站"
                    clearable
                />
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
                  {{ searchForm.startStation || searchForm.endStation ? '查询' : '查询所有车票' }}
                </el-button>
                <el-button @click="handleReset">清空</el-button>
              </el-form-item>
            </el-form>
          </el-card>
        </div>

        <!-- 车次列表 -->
        <div class="train-list" v-if="trains.length > 0">
          <el-card v-for="train in trains" :key="train.id" class="train-card">
            <div class="train-info">
              <div class="train-header">
                <div class="train-no">{{ train.trainNo }}</div>
                <el-tag :type="getTrainTypeColor(train.trainType)">
                  {{ train.trainTypeName }}
                </el-tag>
              </div>
              <div class="train-route">
                <div class="station">
                  <div class="station-name">{{ train.startStation }}</div>
                  <div class="time">{{ train.startTime }}</div>
                </div>
                <div class="arrow">→</div>
                <div class="station">
                  <div class="station-name">{{ train.endStation }}</div>
                  <div class="time">{{ train.endTime }}</div>
                </div>
              </div>
            </div>
            <div class="ticket-info">
              <el-table :data="train.stocks" style="width: 100%">
                <el-table-column prop="seatTypeName" label="席别" />
                <el-table-column prop="price" label="票价" width="100" />
                <el-table-column prop="availableSeats" label="余票" width="100">
                  <template #default="{ row }">
                    <el-tag :type="getSeatsColor(row.availableSeats)">
                      {{ row.availableSeats }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="120">
                  <template #default="{ row }">
                    <el-button
                        type="primary"
                        size="small"
                        :disabled="row.availableSeats <= 0"
                        @click="handleBuy(train, row)"
                    >
                      购票
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </el-card>
        </div>

        <el-empty v-else-if="!loading && trains.length === 0" :description="searchForm.startStation || searchForm.endStation ? '暂无符合条件的车次' : '请点击查询按钮查询车票'" />
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
        <div class="section-header">
          <span>选择乘客</span>
          <el-button type="primary" text size="small" @click="showAddPassenger = true">
            + 添加乘客
          </el-button>
        </div>

        <el-checkbox-group v-model="selectedPassengerIds" :disabled="paying">
          <div v-if="passengers.length === 0" class="no-passenger">
            <el-empty description="暂无常用联系人，请添加" :image-size="60" />
          </div>
          <el-checkbox
              v-for="passenger in passengers"
              :key="passenger.id"
              :label="passenger.id"
              class="passenger-checkbox"
          >
            <div class="passenger-info">
              <span class="name">{{ passenger.name }}</span>
              <span class="idcard">{{ formatIdCard(passenger.idCard) }}</span>
            </div>
          </el-checkbox>
        </el-checkbox-group>
      </div>

      <div class="total-section" v-if="selectedPassengerIds.length > 0">
        <span>合计：</span>
        <span class="total-price">¥{{ totalPrice }}</span>
      </div>

      <!-- 添加乘客表单 -->
      <el-dialog
          v-model="showAddPassenger"
          title="添加乘客"
          width="400px"
          append-to-body
      >
        <el-form :model="passengerForm" :rules="passengerRules" ref="passengerFormRef" label-width="80px">
          <el-form-item label="姓名" prop="name">
            <el-input v-model="passengerForm.name" placeholder="请输入乘客姓名" />
          </el-form-item>
          <el-form-item label="身份证" prop="idCard">
            <el-input v-model="passengerForm.idCard" placeholder="请输入身份证号" />
          </el-form-item>
          <el-form-item label="手机号" prop="phone">
            <el-input v-model="passengerForm.phone" placeholder="请输入手机号" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="showAddPassenger = false">取消</el-button>
          <el-button type="primary" @click="handleAddPassenger" :loading="addingPassenger">添加</el-button>
        </template>
      </el-dialog>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="orderDialogVisible = false" :disabled="paying">取消</el-button>
          <el-button
              type="primary"
              @click="handleSubmitOrder"
              :loading="paying"
              :disabled="selectedPassengerIds.length === 0"
          >
            {{ paying ? '支付中...' : '确认购票' }}
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
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

const trains = ref([])
const loading = ref(false)

// 购票相关
const orderDialogVisible = ref(false)
const selectedTrain = ref(null)
const selectedStock = ref(null)
const passengers = ref([])
const selectedPassengerIds = ref([])
const paying = ref(false)

// 添加乘客相关
const showAddPassenger = ref(false)
const addingPassenger = ref(false)
const passengerFormRef = ref()
const passengerForm = reactive({
  name: '',
  idCard: '',
  phone: ''
})

const passengerRules = {
  name: [
    { required: true, message: '请输入姓名', trigger: 'blur' }
  ],
  idCard: [
    { required: true, message: '请输入身份证号', trigger: 'blur' },
    { pattern: /^[1-9]\d{5}(18|19|20)\d{2}((0[1-9])|(1[0-2]))(([0-2][1-9])|10|20|30|31)\d{3}[0-9Xx]$/, message: '身份证号格式不正确', trigger: 'blur' }
  ],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ]
}

// 计算总价
const totalPrice = computed(() => {
  if (!selectedStock.value) return 0
  return (selectedStock.value.price * selectedPassengerIds.value.length).toFixed(2)
})

const disabledDate = (time) => {
  return time.getTime() < Date.now() - 8.64e7
}

const handleSearch = async () => {
  loading.value = true
  try {
    const res = await request.get('/trains/search', {
      params: {
        startStation: searchForm.startStation,
        endStation: searchForm.endStation,
        trainDate: searchForm.trainDate
      }
    })

    // 处理余票信息，添加席别名称
    trains.value = res.data.map(train => {
      const trainData = { ...train }
      trainData.stocks = train.stocks.map(stock => ({
        ...stock,
        seatTypeName: getSeatTypeName(stock.seatType)
      }))
      return trainData
    })

    if (res.data.length === 0) {
      ElMessage.info({
        message: '未找到符合条件的车次',
        duration: 1000
      })
    } else {
      const conditionText = searchForm.startStation && searchForm.endStation
          ? `${searchForm.startStation} 至 ${searchForm.endStation}`
          : '所有车次'
      ElMessage.success({
        message: `查询成功，共找到 ${res.data.length} 个${conditionText}`,
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
  trains.value = []
}

// 获取乘客列表
const loadPassengers = async () => {
  try {
    const res = await request.get('/passengers')
    passengers.value = res.data || []
  } catch (error) {
    console.error('获取乘客列表失败:', error)
    passengers.value = []
  }
}

// 格式化身份证号（隐藏中间部分）
const formatIdCard = (idCard) => {
  if (!idCard) return ''
  return idCard.replace(/(\d{4})\d+(\d{4})/, '$1**********$2')
}

// 点击购票
const handleBuy = async (train, stock) => {
  selectedTrain.value = { ...train, trainDate: searchForm.trainDate }
  selectedStock.value = stock
  selectedPassengerIds.value = []

  // 加载乘客列表
  await loadPassengers()

  orderDialogVisible.value = true
}

// 添加乘客
const handleAddPassenger = async () => {
  await passengerFormRef.value.validate(async (valid) => {
    if (!valid) return

    addingPassenger.value = true
    try {
      await request.post('/passengers', {
        name: passengerForm.name,
        idCard: passengerForm.idCard,
        phone: passengerForm.phone
      })
      ElMessage.success('添加成功')
      showAddPassenger.value = false
      passengerFormRef.value.resetFields()

      // 刷新乘客列表
      await loadPassengers()
    } catch (error) {
      console.error('添加乘客失败:', error)
      ElMessage.error('添加失败，请稍后重试')
    } finally {
      addingPassenger.value = false
    }
  })
}

// 提交订单
const handleSubmitOrder = async () => {
  if (selectedPassengerIds.value.length === 0) {
    ElMessage.warning('请选择至少一位乘客')
    return
  }

  paying.value = true

  try {
    // 构建订单明细
    const items = selectedPassengerIds.value.map(id => {
      const passenger = passengers.value.find(p => p.id === id)
      return {
        passengerName: passenger.name,
        idCard: passenger.idCard
      }
    })

    // 创建订单
    const res = await request.post('/orders', {
      trainId: selectedTrain.value.id,
      trainDate: selectedTrain.value.trainDate,
      startStation: selectedTrain.value.startStation,
      endStation: selectedTrain.value.endStation,
      seatType: selectedStock.value.seatType,
      items: items
    })

    const orderNo = res.data.orderNo

    // 模拟支付
    ElMessage.info('正在发起支付...')
    await new Promise(resolve => setTimeout(resolve, 1500))

    // 调用支付接口
    await request.post(`/orders/${orderNo}/pay`)

    ElMessage.success({
      message: '支付成功！',
      duration: 2000
    })

    // 关闭对话框
    orderDialogVisible.value = false

    // 跳转到订单页面
    setTimeout(() => {
      router.push('/orders')
    }, 1000)

  } catch (error) {
    console.error('购票失败:', error)
    ElMessage.error(error.message || '购票失败，请稍后重试')
  } finally {
    paying.value = false
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

const getTrainTypeColor = (trainType) => {
  const colors = {
    1: 'danger',
    2: 'warning',
    3: 'info'
  }
  return colors[trainType] || 'info'
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

onMounted(() => {
  handleSearch()
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
  .section-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 15px;
    font-weight: 500;
  }

  .no-passenger {
    padding: 20px 0;
  }

  .passenger-checkbox {
    display: block;
    margin-bottom: 10px;

    .passenger-info {
      display: inline-flex;
      flex-direction: column;

      .name {
        font-weight: 500;
      }

      .idcard {
        font-size: 12px;
        color: #999;
      }
    }
  }
}

.total-section {
  margin-top: 20px;
  padding-top: 15px;
  border-top: 1px solid #eee;
  display: flex;
  justify-content: flex-end;
  align-items: center;
  font-size: 16px;

  .total-price {
    margin-left: 10px;
    font-size: 24px;
    font-weight: bold;
    color: #ff4d4f;
  }
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>
