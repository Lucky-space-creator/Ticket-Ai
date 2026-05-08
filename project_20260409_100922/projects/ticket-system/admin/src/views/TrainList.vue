<template>
  <div class="train-list">
    <div class="header">
      <h2>车次管理</h2>
    </div>
    <el-card>
      <el-table :data="trainList" v-loading="loading">
        <el-table-column prop="trainNo" label="车次号" />
        <el-table-column prop="trainTypeName" label="类型" />
        <el-table-column prop="startStation" label="始发站" />
        <el-table-column prop="endStation" label="终到站" />
        <el-table-column prop="startTime" label="发车时间" />
        <el-table-column prop="endTime" label="到达时间" />
        <el-table-column prop="status" label="状态">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '正常' : '停运' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button
                size="small"
                :type="row.status === 1 ? 'warning' : 'success'"
                @click="toggleStatus(row)"
            >
              {{ row.status === 1 ? '停运' : '启用' }}
            </el-button>
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

    <el-dialog
        v-model="dialogVisible"
        title="编辑车次"
        width="600px"
        @close="closeDialog"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="车次号" prop="trainNo">
          <el-input v-model="form.trainNo" placeholder="如G1234" />
        </el-form-item>
        <el-form-item label="车次类型" prop="trainType">
          <el-select v-model="form.trainType" placeholder="请选择">
            <el-option label="高铁" :value="1" />
            <el-option label="动车" :value="2" />
            <el-option label="普快" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="始发站" prop="startStation">
          <el-input v-model="form.startStation" />
        </el-form-item>
        <el-form-item label="终到站" prop="endStation">
          <el-input v-model="form.endStation" />
        </el-form-item>
        <el-form-item label="发车时间" prop="startTime">
          <el-time-picker
              v-model="form.startTime"
              placeholder="选择时间"
              format="HH:mm:ss"
              value-format="HH:mm:ss"
          />
        </el-form-item>
        <el-form-item label="到达时间" prop="endTime">
          <el-time-picker
              v-model="form.endTime"
              placeholder="选择时间"
              format="HH:mm:ss"
              value-format="HH:mm:ss"
          />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :label="1">正常</el-radio>
            <el-radio :label="0">停运</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const TRAIN_TYPE_NAMES = { 1: '高铁', 2: '动车', 3: '普快' }

const loading = ref(false)
/** 全量车次（客户端分页） */
const allTrains = ref([])
const trainList = ref([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

function applyPage() {
  const start = (page.value - 1) * pageSize.value
  trainList.value = allTrains.value.slice(start, start + pageSize.value)
}

watch([page, pageSize], applyPage)

const dialogVisible = ref(false)
const formRef = ref()
const form = reactive({
  id: null,
  trainNo: '',
  trainType: 1,
  startStation: '',
  endStation: '',
  startTime: '',
  endTime: '',
  status: 1
})
const rules = {
  trainNo: [{ required: true, message: '请输入车次号', trigger: 'blur' }],
  trainType: [{ required: true, message: '请选择车次类型', trigger: 'change' }],
  startStation: [{ required: true, message: '请输入始发站', trigger: 'blur' }],
  endStation: [{ required: true, message: '请输入终到站', trigger: 'blur' }],
  startTime: [{ required: true, message: '请选择发车时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择到达时间', trigger: 'change' }]
}

function formatTimeForForm(v) {
  if (v == null || v === '') return ''
  if (typeof v === 'string') {
    const s = v.trim()
    if (/^\d{2}:\d{2}:\d{2}$/.test(s)) return s
    if (/^\d{2}:\d{2}$/.test(s)) return `${s}:00`
    return s
  }
  if (typeof v === 'object' && v.hour != null) {
    const z = (n) => String(n).padStart(2, '0')
    return `${z(v.hour)}:${z(v.minute ?? 0)}:${z(v.second ?? 0)}`
  }
  return String(v)
}

const fetchData = async () => {
  loading.value = true
  try {
    const res = await request.get('/api/admin/trains')
    if (res.code === 200 && Array.isArray(res.data)) {
      allTrains.value = res.data.map((item) => ({
        id: item.id,
        trainNo: item.trainNo,
        trainType: item.trainType,
        trainTypeName: TRAIN_TYPE_NAMES[item.trainType] ?? '',
        startStation: item.startStation,
        endStation: item.endStation,
        startTime: formatTimeForForm(item.startTime),
        endTime: formatTimeForForm(item.endTime),
        status: item.status != null ? item.status : 1
      }))
      total.value = allTrains.value.length
      applyPage()
    }
  } catch (error) {
    console.error('获取车次列表失败:', error)
  } finally {
    loading.value = false
  }
}

const handleEdit = (row) => {
  Object.assign(form, {
    id: row.id,
    trainNo: row.trainNo,
    trainType: row.trainType,
    startStation: row.startStation,
    endStation: row.endStation,
    startTime: row.startTime,
    endTime: row.endTime,
    status: row.status
  })
  dialogVisible.value = true
}

const toggleStatus = async (row) => {
  const newStatus = row.status === 1 ? 0 : 1
  try {
    await request.put(`/api/admin/trains/${row.id}/status`, { status: newStatus })
    ElMessage.success('操作成功')
    row.status = newStatus
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

const closeDialog = () => {
  formRef.value?.resetFields()
}

const submitForm = async () => {
  if (!formRef.value || !form.id) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      try {
        await request.put(`/api/admin/trains/${form.id}`, form)
        ElMessage.success('操作成功')
        dialogVisible.value = false
        fetchData()
      } catch (error) {
        ElMessage.error('操作失败')
      }
    }
  })
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.train-list {
  padding: 20px;
}
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.pagination {
  margin-top: 20px;
  text-align: center;
}
</style>
