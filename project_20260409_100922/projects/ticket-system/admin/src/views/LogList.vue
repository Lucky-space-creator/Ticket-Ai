<template>
  <div class="log-list">
    <div class="header">
      <h2>操作日志</h2>
      <div class="header-actions">
        <el-button @click="fetchData" :icon="Refresh">刷新</el-button>
      </div>
    </div>

    <!-- 搜索筛选 -->
    <el-form :inline="true" :model="searchForm" class="search-form">
      <el-form-item label="模块">
        <el-input v-model="searchForm.module" placeholder="模块名称" clearable style="width: 140px" />
      </el-form-item>
      <el-form-item label="操作">
        <el-input v-model="searchForm.operation" placeholder="操作类型" clearable style="width: 140px" />
      </el-form-item>
      <el-form-item label="用户">
        <el-input v-model="searchForm.username" placeholder="用户名/手机号" clearable style="width: 140px" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 100px">
          <el-option label="成功" :value="1" />
          <el-option label="失败" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item label="时间范围">
        <el-date-picker
          v-model="searchForm.timeRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          value-format="YYYY-MM-DD HH:mm:ss"
          style="width: 320px"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 日志表格：仅展示主要信息 -->
    <el-table :data="logList" border stripe style="width: 100%" v-loading="loading">
      <el-table-column prop="createdAt" label="时间" width="170">
        <template #default="{ row }">
          {{ formatDateTime(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column prop="username" label="操作人" width="120" show-overflow-tooltip />
      <el-table-column prop="module" label="模块" width="120" />
      <el-table-column prop="operation" label="操作" width="140" show-overflow-tooltip />
      <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
            {{ row.status === 1 ? '成功' : '失败' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="ipAddress" label="IP地址" width="130" />
      <el-table-column prop="requestUrl" label="请求路径" min-width="180" show-overflow-tooltip />
      <el-table-column prop="executionTime" label="耗时(ms)" width="100" />
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button
            size="small"
            type="danger"
            @click="handleDelete(row)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import request from '@/utils/request'

const logList = ref([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const searchForm = ref({
  module: '',
  operation: '',
  username: '',
  status: null,
  timeRange: null
})

const fetchData = async () => {
  loading.value = true
  try {
    const params = {
      page: currentPage.value,
      size: pageSize.value
    }
    if (searchForm.value.module) params.module = searchForm.value.module
    if (searchForm.value.operation) params.operation = searchForm.value.operation
    if (searchForm.value.username) params.username = searchForm.value.username
    if (searchForm.value.status !== null) params.status = searchForm.value.status

    if (searchForm.value.timeRange && searchForm.value.timeRange.length === 2) {
      params.startTime = searchForm.value.timeRange[0]
      params.endTime = searchForm.value.timeRange[1]
    }

    const res = await request.get('/api/admin/logs', { params })
    logList.value = res.data.records || []
    total.value = res.data.total || 0
  } catch (error) {
    ElMessage.error('加载操作日志失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  currentPage.value = 1
  fetchData()
}

const handleReset = () => {
  searchForm.value = {
    module: '',
    operation: '',
    username: '',
    status: null,
    timeRange: null
  }
  currentPage.value = 1
  fetchData()
}

const handleCurrentChange = (page) => {
  currentPage.value = page
  fetchData()
}

const handleSizeChange = (size) => {
  pageSize.value = size
  currentPage.value = 1
  fetchData()
}

const handleDelete = (row) => {
  ElMessageBox.confirm(
    `确定要删除该条操作日志吗？（ID: ${row.id}）`,
    '确认删除',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(async () => {
    try {
      await request.delete(`/api/admin/logs/${row.id}`)
      ElMessage.success('删除成功')
      fetchData()
    } catch (error) {
      ElMessage.error('删除失败')
    }
  }).catch(() => {
    // 取消删除
  })
}

const formatDateTime = (dateStr) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.log-list {
  padding: 20px;
}
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
}
.header-actions {
  display: flex;
  gap: 10px;
}
.search-form {
  margin-bottom: 15px;
  background: #f8f9fa;
  padding: 15px;
  border-radius: 6px;
}
.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>
