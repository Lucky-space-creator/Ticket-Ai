<template>
  <div class="train-list">
    <div class="header">
      <h2>车次管理</h2>
      <el-button type="primary" @click="handleAdd">添加车次</el-button>
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
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button
                size="small"
                :type="row.status === 1 ? 'warning' : 'success'"
                @click="toggleStatus(row)"
            >
              {{ row.status === 1 ? '停运' : '启用' }}
            </el-button>
            <el-button size="small" type="primary" @click="handleStock(row)">余票</el-button>
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

    <!-- 添加/编辑弹窗 -->
    <el-dialog
        v-model="dialogVisible"
        :title="dialogTitle"
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

    <!-- 余票设置弹窗 -->
    <el-dialog
        v-model="stockDialogVisible"
        title="设置余票"
        width="800px"
    >
      <el-table :data="stockList">
        <el-table-column prop="trainDate" label="日期" />
        <el-table-column prop="startStation" label="出发站" />
        <el-table-column prop="endStation" label="到达站" />
        <el-table-column prop="seatTypeName" label="席别" />
        <el-table-column prop="price" label="票价" />
        <el-table-column prop="availableSeats" label="余票">
          <template #default="{ row }">
            <el-input-number v-model="row.availableSeats" :min="0" size="small" />
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="stockDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveStock">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

const loading = ref(false)
const trainList = ref([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

const dialogVisible = ref(false)
const stockDialogVisible = ref(false)
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

const stockList = ref([])
const currentTrainId = ref(null)

const dialogTitle = computed(() => form.id ? '编辑车次' : '添加车次')

const fetchData = async () => {
  loading.value = true
  try {
    // 调用管理端接口（待实现）
    // 暂时使用模拟数据
    const res = await request.get('/api/trains/search', {
      params: {
        startStation: '',
        endStation: '',
        trainDate: new Date().toISOString().split('T')[0]
      }
    })
    if (res.code === 200) {
      trainList.value = res.data.map(item => ({
        id: item.id,
        trainNo: item.trainNo,
        trainType: item.trainType,
        trainTypeName: item.trainTypeName,
        startStation: item.startStation,
        endStation: item.endStation,
        startTime: item.startTime,
        endTime: item.endTime,
        status: 1 // 默认正常
      }))
      total.value = trainList.value.length
    }
  } catch (error) {
    console.error('获取车次列表失败:', error)
  } finally {
    loading.value = false
  }
}

const handleAdd = () => {
  Object.keys(form).forEach(key => {
    form[key] = key === 'trainType' ? 1 : key === 'status' ? 1 : ''
  })
  form.id = null
  dialogVisible.value = true
}

const handleEdit = (row) => {
  Object.assign(form, row)
  dialogVisible.value = true
}

const toggleStatus = async (row) => {
  const newStatus = row.status === 1 ? 0 : 1
  try {
    // 调用接口更新状态
    await request.put(`/api/admin/trains/${row.id}/status`, { status: newStatus })
    ElMessage.success('操作成功')
    row.status = newStatus
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

const handleStock = (row) => {
  currentTrainId.value = row.id
  // 获取余票数据（待实现）
  stockList.value = [{
    trainDate: '2024-04-10',
    startStation: row.startStation,
    endStation: row.endStation,
    seatTypeName: '二等座',
    price: 553.0,
    availableSeats: 100
  }]
  stockDialogVisible.value = true
}

const closeDialog = () => {
  formRef.value?.resetFields()
}

const submitForm = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      try {
        if (form.id) {
          // 更新
          await request.put(`/api/admin/trains/${form.id}`, form)
        } else {
          // 新增
          await request.post('/api/admin/trains', form)
        }
        ElMessage.success('操作成功')
        dialogVisible.value = false
        fetchData()
      } catch (error) {
        ElMessage.error('操作失败')
      }
    }
  })
}

const saveStock = async () => {
  try {
    // 调用接口保存余票设置
    await request.post(`/api/admin/trains/${currentTrainId.value}/stock`, stockList.value)
    ElMessage.success('保存成功')
    stockDialogVisible.value = false
  } catch (error) {
    ElMessage.error('保存失败')
  }
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