<template>
  <div class="user-list">
    <div class="header">
      <h2>用户管理</h2>
      <div class="filters">
        <el-input v-model="filters.phone" placeholder="手机号" style="width: 200px;" />
        <el-select v-model="filters.status" placeholder="状态" style="width: 100px;">
          <el-option label="全部" value="" />
          <el-option label="正常" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
        <el-button type="primary" @click="fetchData">搜索</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>
    </div>
    <el-card>
      <el-table :data="userList" v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="phone" label="手机号" />
        <el-table-column prop="realName" label="姓名" />
        <el-table-column prop="idCard" label="身份证号" />
        <el-table-column prop="status" label="状态">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="注册时间" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button
                size="small"
                :type="row.status === 1 ? 'warning' : 'success'"
                @click="toggleStatus(row)"
            >
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const loading = ref(false)
const userList = ref([])

const filters = reactive({
  phone: '',
  status: ''
})

const fetchData = async () => {
  loading.value = true
  try {
    // 调用管理端用户接口
    const res = await request.get('/api/admin/users')
    if (res.code === 200) {
      // 前端筛选
      let filtered = res.data
      if (filters.phone) {
        filtered = filtered.filter(user => user.phone.includes(filters.phone))
      }
      if (filters.status !== '') {
        filtered = filtered.filter(user => user.status === parseInt(filters.status))
      }
      userList.value = filtered
    } else {
      ElMessage.error(res.message || '加载失败')
    }
  } catch (error) {
    console.error('获取用户列表失败:', error)
    ElMessage.error('网络错误')
  } finally {
    loading.value = false
  }
}

const resetFilters = () => {
  filters.phone = ''
  filters.status = ''
  fetchData()
}

const toggleStatus = async (row) => {
  const newStatus = row.status === 1 ? 0 : 1
  try {
    // 调用接口更新状态
    await request.put(`/api/admin/users/${row.id}/status`, { status: newStatus })
    ElMessage.success('操作成功')
    row.status = newStatus
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.user-list {
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