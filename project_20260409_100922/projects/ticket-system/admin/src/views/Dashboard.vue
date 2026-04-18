<template>
  <div class="dashboard">
    <h2>数据概览</h2>
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background-color: #409EFF;">
              <el-icon><User /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.userCount || 0 }}</div>
              <div class="stat-label">用户总数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background-color: #67C23A;">
              <el-icon><List /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.orderCount || 0 }}</div>
              <div class="stat-label">订单总数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background-color: #E6A23C;">
              <el-icon><Box /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.trainCount || 0 }}</div>
              <div class="stat-label">车次总数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background-color: #F56C6C;">
              <el-icon><ChatDotRound /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.knowledgeCount || 0 }}</div>
              <div class="stat-label">知识库条目</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
    <el-row :gutter="20" class="chart-row">
      <el-col :span="12">
        <el-card header="最近订单">
          <div v-if="recentOrders.length === 0" class="empty">暂无数据</div>
          <el-table v-else :data="recentOrders" size="small">
            <el-table-column prop="orderNo" label="订单号" />
            <el-table-column prop="trainNo" label="车次" />
            <el-table-column prop="totalAmount" label="金额" />
            <el-table-column prop="status" label="状态">
              <template #default="{ row }">
                <el-tag :type="statusTag(row.status)">{{ statusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card header="系统信息">
          <div class="system-info">
            <p>后端版本：v2.0.0</p>
            <p>前端版本：v1.0.0</p>
            <p>数据库：MySQL 8.0</p>
            <p>服务器时间：{{ serverTime }}</p>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { User, List, Box, ChatDotRound } from '@element-plus/icons-vue'
import request from '@/utils/request'

const stats = ref({})
const recentOrders = ref([])
const serverTime = ref('')

const statusTag = (status) => {
  switch (status) {
    case 0: return 'info'
    case 1: return 'success'
    case 2: return 'warning'
    case 3: return 'danger'
    default: return ''
  }
}

const statusText = (status) => {
  switch (status) {
    case 0: return '待支付'
    case 1: return '已支付'
    case 2: return '已退票'
    case 3: return '已取消'
    default: return '未知'
  }
}

const fetchStats = async () => {
  try {
    // 获取统计数据
    const statsRes = await request.get('/api/admin/stats/overview')
    console.log('statsRes:', statsRes)
    if (statsRes.code === 200) {
      stats.value = statsRes.data
      console.log('stats data:', statsRes.data)
    } else {
      // 如果接口无权限或未实现，使用模拟数据
      console.warn('stats API returned non-200 code:', statsRes.code)
      stats.value = {
        userCount: 0,
        orderCount: 0,
        trainCount: 0,
        knowledgeCount: 0,
        todayUserCount: 0,
        todayOrderCount: 0,
        runningTrainCount: 0,
        todayChatCount: 0
      }
    }
    // 获取最近订单
    const ordersRes = await request.get('/api/admin/orders')
    if (ordersRes.code === 200) {
      // 分页响应中 data.records 是订单数组
      const orders = ordersRes.data.records || ordersRes.data
      recentOrders.value = Array.isArray(orders) ? orders.slice(0, 5) : []
    }
    // 获取服务器时间
    serverTime.value = new Date().toLocaleString()
  } catch (error) {
    console.error('获取统计数据失败:', error)
    // 使用模拟数据
    stats.value = {
      userCount: 0,
      orderCount: 0,
      trainCount: 0,
      knowledgeCount: 0,
      todayUserCount: 0,
      todayOrderCount: 0,
      runningTrainCount: 0,
      todayChatCount: 0
    }
  }
}

onMounted(() => {
  fetchStats()
})
</script>

<style scoped>
.dashboard {
  padding: 20px;
}
.stats-row {
  margin-bottom: 20px;
}
.stat-card {
  height: 120px;
}
.stat-content {
  display: flex;
  align-items: center;
}
.stat-icon {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 20px;
}
.stat-icon .el-icon {
  font-size: 30px;
  color: #fff;
}
.stat-value {
  font-size: 28px;
  font-weight: bold;
}
.stat-label {
  color: #999;
  margin-top: 5px;
}
.chart-row {
  margin-top: 20px;
}
.empty {
  text-align: center;
  color: #999;
  padding: 20px;
}
.system-info p {
  margin-bottom: 10px;
  color: #666;
}
</style>