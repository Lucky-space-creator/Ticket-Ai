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
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
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

    // 提示查询结果
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

const handleBuy = (train, stock) => {
  ElMessage.info('购票功能开发中，敬请期待')
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
</style>
