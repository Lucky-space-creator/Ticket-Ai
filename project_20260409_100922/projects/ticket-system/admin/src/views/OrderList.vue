<template>
  <div class="order-list">
    <div class="header">
      <h2>订单管理</h2>
      <div class="filters">
        <el-input v-model="filters.orderNo" placeholder="订单号" style="width: 200px;" />
        <el-input v-model="filters.phone" placeholder="用户手机号" style="width: 200px;" />
        <el-select v-model="filters.status" placeholder="订单状态" style="width: 120px;">
          <el-option label="全部" value="" />
          <el-option label="待支付" :value="0" />
          <el-option label="已支付" :value="1" />
          <el-option label="已退票" :value="2" />
          <el-option label="已取消" :value="3" />
        </el-select>
        <el-button type="primary" @click="fetchData">搜索</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>
    </div>
    <el-card>
      <el-table :data="orderList" v-loading="loading">
        <el-table-column prop="orderNo" label="订单号" width="200" />
        <el-table-column prop="trainNo" label="车次" />
        <el-table-column prop="trainDate" label="乘车日期" />
        <el-table-column prop="startStation" label="出发站" />
        <el-table-column prop="endStation" label="到达站" />
        <el-table-column prop="seatTypeName" label="席别" />
        <el-table-column prop="totalAmount" label="金额" />
        <el-table-column prop="status" label="状态">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button size="small" @click="viewDetail(row)">详情</el-button>
            <el-button
                size="small"
                type="danger"
                v-if="row.status === 1"
                @click="refundOrder(row)"
            >退票</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination">
        <el-pagination
            v-model:current-page="page"
            v-model:page-size="pageSize"
            :total="total"
            @current-change="fetchData"
            layout="prev, pager, next"
        />
      </div>
    </el-card>

    <!-- 订单详情弹窗 -->
    <el-dialog
        v-model="detailVisible"
        title="订单详情"
        width="800px"
    >
      <div v-if="currentOrder">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="订单号">{{ currentOrder.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="用户ID">{{ currentOrder.userId }}</el-descriptions-item>
          <el-descriptions-item label="车次">{{ currentOrder.trainNo }}</el-descriptions-item>
          <el-descriptions-item label="乘车日期">{{ currentOrder.trainDate }}</el-descriptions-item>
          <el-descriptions-item label="出发站">{{ currentOrder.startStation }}</el-descriptions-item>
          <el-descriptions-item label="到达站">{{ currentOrder.endStation }}</el-descriptions-item>
          <el-descriptions-item label="席别">{{ currentOrder.seatTypeName }}</el-descriptions-item>
          <el-descriptions-item label="发车时间">{{ currentOrder.departTime }}</el-descriptions-item>
          <el-descriptions-item label="订单金额">{{ currentOrder.totalAmount }}</el-descriptions-item>
          <el-descriptions-item label="订单状态">
            <el-tag :type="statusTag(currentOrder.status)">{{ statusText(currentOrder.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ currentOrder.createdAt }}</el-descriptions-item>
          <el-descriptions-item label="支付时间">{{ currentOrder.payTime || '未支付' }}</el-descriptions-item>
        </el-descriptions>
        <h3 style="margin-top: 20px;">乘客信息</h3>
        <el-table :data="currentOrder.items" size="small">
          <el-table-column prop="passengerName" label="姓名" />
          <el-table-column prop="idCard" label="身份证号" />
          <el-table-column prop="price" label="票价" />
        </el-table>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

const loading = ref(false)
const orderList = ref([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

const filters = reactive({
  orderNo: '',
  phone: '',
  status: ''
})

const detailVisible = ref(false)
const currentOrder = ref(null)

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

const fetchData = async () => {
  loading.value = true
  try {
    const params = {
      orderNo: filters.orderNo,
      phone: filters.phone,
      status: filters.status,
      page: page.value,
      size: pageSize.value
    }
    const res = await request.get('/api/admin/orders', { params })
    if (res.code === 200) {
      orderList.value = res.data.records.map(order => ({
        ...order,
        seatTypeName: getSeatTypeName(order.seatType)
      }))
      total.value = res.data.total
    }
  } catch (error) {
    console.error('获取订单列表失败:', error)
  } finally {
    loading.value = false
  }
}

const getSeatTypeName = (type) => {
  const map = {
    1: '商务座',
    2: '一等座',
    3: '二等座',
    4: '软卧',
    5: '硬卧',
    6: '硬座'
  }
  return map[type] || type
}

const resetFilters = () => {
  filters.orderNo = ''
  filters.phone = ''
  filters.status = ''
  page.value = 1
  fetchData()
}

const viewDetail = async (row) => {
  try {
    const res = await request.get(`/api/admin/orders/${row.orderNo}`)
    if (res.code === 200) {
      currentOrder.value = res.data
      detailVisible.value = true
    }
  } catch (error) {
    ElMessage.error('获取订单详情失败')
  }
}

const refundOrder = (row) => {
  ElMessageBox.confirm('确认退票吗？', '提示', {
    type: 'warning'
  }).then(async () => {
    try {
      await request.post(`/api/admin/orders/${row.orderNo}/refund`)
      ElMessage.success('退票成功')
      fetchData()
    } catch (error) {
      ElMessage.error('退票失败')
    }
  }).catch(() => {})
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.order-list {
  padding: 20px;
}
.header {
  margin-bottom: 20px;
}
.filters {
  display: flex;
  gap: 10px;
  margin-top: 10px;
}
.pagination {
  margin-top: 20px;
  text-align: center;
}
</style>