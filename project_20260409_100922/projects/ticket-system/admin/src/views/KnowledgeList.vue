<template>
  <div class="knowledge-list">
    <div class="header">
      <div class="header-text">
        <h2>FAQ问答管理</h2>
        <p class="sub">固定话术短问答（不走大模型）；政策类内容由前台 RAG+LLM。</p>
      </div>
      <el-button type="primary" @click="handleAdd">添加知识</el-button>
    </div>
    <el-card>
      <el-table :data="knowledgeList" v-loading="loading">
        <el-table-column prop="category" label="分类" />
        <el-table-column prop="question" label="问题" />
        <el-table-column prop="answer" label="答案">
          <template #default="{ row }">
            <div class="answer-cell">{{ row.answer }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="keywords" label="关键词" />
        <el-table-column prop="hitCount" label="命中次数" />
        <el-table-column prop="status" label="状态">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
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
        width="700px"
        @close="closeDialog"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="分类" prop="category">
          <el-select v-model="form.category" placeholder="请选择">
            <el-option label="通用" value="general" />
            <el-option label="购票" value="booking" />
            <el-option label="退票" value="refund" />
            <el-option label="查询" value="query" />
          </el-select>
        </el-form-item>
        <el-form-item label="问题" prop="question">
          <el-input v-model="form.question" type="textarea" rows="2" />
        </el-form-item>
        <el-form-item label="答案" prop="answer">
          <el-input v-model="form.answer" type="textarea" rows="4" />
        </el-form-item>
        <el-form-item label="关键词" prop="keywords">
          <el-input v-model="form.keywords" placeholder="多个关键词用逗号分隔" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :label="1">启用</el-radio>
            <el-radio :label="0">禁用</el-radio>
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
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

const loading = ref(false)
const knowledgeList = ref([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

const dialogVisible = ref(false)
const formRef = ref()
const form = reactive({
  id: null,
  category: 'general',
  question: '',
  answer: '',
  keywords: '',
  status: 1
})
const rules = {
  category: [{ required: true, message: '请选择分类', trigger: 'change' }],
  question: [{ required: true, message: '请输入问题', trigger: 'blur' }],
  answer: [{ required: true, message: '请输入答案', trigger: 'blur' }]
}

const dialogTitle = computed(() => form.id ? '编辑知识' : '添加知识')

const fetchData = async () => {
  loading.value = true
  try {
    const res = await request.get('/api/knowledge/list')
    if (res.code === 200) {
      knowledgeList.value = res.data
      total.value = knowledgeList.value.length
    }
  } catch (error) {
    console.error('获取知识库列表失败:', error)
  } finally {
    loading.value = false
  }
}

const handleAdd = () => {
  Object.keys(form).forEach(key => {
    form[key] = key === 'category' ? 'general' : key === 'status' ? 1 : ''
  })
  form.id = null
  dialogVisible.value = true
}

const handleEdit = (row) => {
  Object.assign(form, row)
  dialogVisible.value = true
}

const handleDelete = (row) => {
  ElMessageBox.confirm('确定删除吗？', '提示', {
    type: 'warning'
  }).then(async () => {
    try {
      await request.delete(`/api/knowledge/${row.id}`)
      ElMessage.success('删除成功')
      fetchData()
    } catch (error) {
      ElMessage.error('删除失败')
    }
  }).catch(() => {})
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
          const res = await request.put('/api/knowledge/update', form)
          if (res.code === 200) {
            ElMessage.success('更新成功')
          }
        } else {
          // 新增
          const res = await request.post('/api/knowledge/add', form)
          if (res.code === 200) {
            ElMessage.success('添加成功')
          }
        }
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
.knowledge-list {
  padding: 20px;
}
.header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 20px;
}
.header-text h2 {
  margin: 0 0 6px;
}
.header-text .sub {
  margin: 0;
  font-size: 13px;
  color: #909399;
}
.answer-cell {
  max-height: 100px;
  overflow-y: auto;
}
.pagination {
  margin-top: 20px;
  text-align: center;
}
</style>