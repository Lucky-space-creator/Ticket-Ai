<template>
  <div class="ticket-stock-list">
    <div class="header">
      <h2>余票管理</h2>
      <div class="filters">
        <el-input v-model="filters.trainNo" placeholder="车次号（模糊）" style="width: 160px;" clearable />
        <el-date-picker
          v-model="filters.trainDate"
          type="date"
          placeholder="乘车日期"
          value-format="YYYY-MM-DD"
          style="width: 160px;"
          clearable
        />
        <el-select v-model="filters.saleEnabled" placeholder="售票状态" style="width: 120px;" clearable>
          <el-option label="在售" :value="1" />
          <el-option label="停售" :value="0" />
        </el-select>
        <el-button type="primary" @click="search">搜索</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>
    </div>
    <el-card>
      <el-table :data="rows" v-loading="loading" border stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="90" />
        <el-table-column prop="trainNo" label="车次" width="100" />
        <el-table-column prop="trainDate" label="日期" width="120" />
        <el-table-column prop="startStation" label="出发站" width="100" />
        <el-table-column prop="endStation" label="到达站" width="100" />
        <el-table-column label="席别" width="90">
          <template #default="{ row }">{{ seatLabel(row.seatType) }}</template>
        </el-table-column>
        <el-table-column prop="price" label="票价" width="90">
          <template #default="{ row }">{{ row.price != null ? row.price : '-' }}</template>
        </el-table-column>
        <el-table-column label="总座" width="130">
          <template #default="{ row }">
            <el-input-number v-model="row.totalSeats" :min="0" size="small" controls-position="right" />
          </template>
        </el-table-column>
        <el-table-column label="余票" width="130">
          <template #default="{ row }">
            <el-input-number v-model="row.availableSeats" :min="0" size="small" controls-position="right" />
          </template>
        </el-table-column>
        <el-table-column label="售票" width="100" fixed="right">
          <template #default="{ row }">
            <el-switch
              :model-value="row.saleEnabled === 1"
              @change="(v) => onSaleChange(row, v)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="saveSeats(row)">保存票数</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="fetchData"
          @size-change="fetchData"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const SEAT_LABELS = {
  1: '商务',
  2: '一等',
  3: '二等',
  4: '软卧',
  5: '硬卧',
  6: '硬座'
}

const loading = ref(false)
const rows = ref([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

const filters = reactive({
  trainNo: '',
  trainDate: '',
  saleEnabled: null
})

const seatLabel = (t) => SEAT_LABELS[t] ?? `席别${t ?? '-'}`

const buildParams = () => {
  const params = {
    current: page.value,
    size: pageSize.value
  }
  if (filters.trainNo?.trim()) {
    params.trainNo = filters.trainNo.trim()
  }
  if (filters.trainDate) {
    params.trainDate = filters.trainDate
  }
  if (filters.saleEnabled === 0 || filters.saleEnabled === 1) {
    params.saleEnabled = filters.saleEnabled
  }
  return params
}

const fetchData = async () => {
  loading.value = true
  try {
    const res = await request.get('/api/admin/ticket-stocks', { params: buildParams() })
    if (res.code === 200 && res.data) {
      rows.value = res.data.records || []
      total.value = res.data.total ?? 0
    }
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

const search = () => {
  page.value = 1
  fetchData()
}

const resetFilters = () => {
  filters.trainNo = ''
  filters.trainDate = ''
  filters.saleEnabled = null
  page.value = 1
  fetchData()
}

const onSaleChange = async (row, enabled) => {
  const prev = row.saleEnabled
  const next = enabled ? 1 : 0
  try {
    await request.put(`/api/admin/ticket-stocks/${row.id}/sale-enabled`, { saleEnabled: next })
    row.saleEnabled = next
    ElMessage.success('售票状态已更新')
  } catch {
    row.saleEnabled = prev
  }
}

const saveSeats = async (row) => {
  try {
    await request.put(`/api/admin/ticket-stocks/${row.id}/seats`, {
      totalSeats: row.totalSeats,
      availableSeats: row.availableSeats
    })
    ElMessage.success('票数已保存')
    await fetchData()
  } catch {
    /* 全局拦截已提示 */
  }
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.ticket-stock-list {
  padding: 20px;
}
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 20px;
}
.filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}
.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>
