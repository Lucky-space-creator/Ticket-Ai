<template>
  <div class="permission-list">
    <div class="header">
      <h2>权限管理</h2>
      <el-button type="primary" @click="handleAdd">添加权限</el-button>
    </div>

    <el-table :data="permissionList" border stripe style="width: 100%">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="permissionName" label="权限标识" />
      <el-table-column prop="permissionDisplayName" label="权限名称" />
      <el-table-column prop="permissionType" label="类型" width="100">
        <template #default="{ row }">
          <el-tag :type="getTypeTag(row.permissionType)">
            {{ getTypeName(row.permissionType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="parentId" label="父权限ID" width="100" />
      <el-table-column prop="path" label="路由路径" />
      <el-table-column prop="apiMethod" label="API方法" width="100" />
      <el-table-column prop="apiPath" label="API路径" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'">
            {{ row.status === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="180">
        <template #default="{ row }">
          {{ formatDate(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button size="small" @click="handleEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 添加/编辑权限对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="权限标识" prop="permissionName">
          <el-input v-model="form.permissionName" placeholder="请输入权限标识，如system:user:list" />
        </el-form-item>
        <el-form-item label="权限名称" prop="permissionDisplayName">
          <el-input v-model="form.permissionDisplayName" placeholder="请输入权限显示名称" />
        </el-form-item>
        <el-form-item label="权限类型" prop="permissionType">
          <el-select v-model="form.permissionType" placeholder="请选择" @change="handleTypeChange">
            <el-option label="菜单" :value="1" />
            <el-option label="按钮" :value="2" />
            <el-option label="API" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="父权限" prop="parentId">
          <el-select v-model="form.parentId" placeholder="请选择父权限" filterable>
            <el-option label="无（根权限）" :value="0" />
            <el-option
              v-for="item in parentOptions"
              :key="item.id"
              :label="item.permissionDisplayName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.permissionType === 1" label="路由路径" prop="path">
          <el-input v-model="form.path" placeholder="如/system/users" />
        </el-form-item>
        <el-form-item v-if="form.permissionType === 1" label="组件路径" prop="component">
          <el-input v-model="form.component" placeholder="如system/user/index" />
        </el-form-item>
        <el-form-item v-if="form.permissionType === 1" label="图标" prop="icon">
          <el-input v-model="form.icon" placeholder="如setting" />
        </el-form-item>
        <el-form-item v-if="form.permissionType === 1" label="排序" prop="sort">
          <el-input-number v-model="form.sort" :min="0" />
        </el-form-item>
        <el-form-item v-if="form.permissionType === 3" label="API方法" prop="apiMethod">
          <el-select v-model="form.apiMethod" placeholder="请选择">
            <el-option label="GET" value="GET" />
            <el-option label="POST" value="POST" />
            <el-option label="PUT" value="PUT" />
            <el-option label="DELETE" value="DELETE" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.permissionType === 3" label="API路径" prop="apiPath">
          <el-input v-model="form.apiPath" placeholder="如/api/admin/users" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" placeholder="请输入权限描述" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :label="1">启用</el-radio>
            <el-radio :label="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitForm">确定</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

const permissionList = ref([])
const parentOptions = ref([])
const dialogVisible = ref(false)
const formRef = ref(null)
const isEdit = ref(false)

const form = ref({
  permissionName: '',
  permissionDisplayName: '',
  permissionType: 1,
  parentId: 0,
  path: '',
  component: '',
  icon: '',
  sort: 0,
  apiMethod: '',
  apiPath: '',
  description: '',
  status: 1
})

const rules = {
  permissionName: [
    { required: true, message: '请输入权限标识', trigger: 'blur' }
  ],
  permissionDisplayName: [
    { required: true, message: '请输入权限名称', trigger: 'blur' }
  ],
  permissionType: [
    { required: true, message: '请选择权限类型', trigger: 'change' }
  ]
}

const dialogTitle = computed(() => isEdit.value ? '编辑权限' : '添加权限')

// 加载权限列表
const loadPermissions = async () => {
  try {
    const res = await request.get('/api/admin/permissions')
    if (res.code === 200) {
      permissionList.value = res.data
      // 父权限选项（只包含菜单类型）
      parentOptions.value = res.data.filter(p => p.permissionType === 1)
    } else {
      ElMessage.error(res.message || '加载失败')
    }
  } catch (error) {
    ElMessage.error('网络错误')
  }
}

const getTypeTag = (type) => {
  switch (type) {
    case 1: return 'success'
    case 2: return 'warning'
    case 3: return 'danger'
    default: return 'info'
  }
}

const getTypeName = (type) => {
  switch (type) {
    case 1: return '菜单'
    case 2: return '按钮'
    case 3: return 'API'
    default: return '未知'
  }
}

const handleTypeChange = (type) => {
  // 切换类型时清空相关字段
  if (type !== 1) {
    form.value.path = ''
    form.value.component = ''
    form.value.icon = ''
    form.value.sort = 0
  }
  if (type !== 3) {
    form.value.apiMethod = ''
    form.value.apiPath = ''
  }
}

const handleAdd = () => {
  isEdit.value = false
  form.value = {
    permissionName: '',
    permissionDisplayName: '',
    permissionType: 1,
    parentId: 0,
    path: '',
    component: '',
    icon: '',
    sort: 0,
    apiMethod: '',
    apiPath: '',
    description: '',
    status: 1
  }
  dialogVisible.value = true
}

const handleEdit = (row) => {
  isEdit.value = true
  form.value = { ...row }
  dialogVisible.value = true
}

const handleDelete = (row) => {
  ElMessageBox.confirm(`确定删除权限 "${row.permissionDisplayName}" 吗？`, '提示', {
    type: 'warning'
  }).then(async () => {
    try {
      const res = await request.delete(`/api/admin/permissions/${row.id}`)
      if (res.code === 200) {
        ElMessage.success('删除成功')
        loadPermissions()
      } else {
        ElMessage.error(res.message || '删除失败')
      }
    } catch (error) {
      ElMessage.error('网络错误')
    }
  }).catch(() => {})
}

const submitForm = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      try {
        let res
        if (isEdit.value) {
          res = await request.put(`/api/admin/permissions/${form.value.id}`, form.value)
        } else {
          res = await request.post('/api/admin/permissions', form.value)
        }
        if (res.code === 200) {
          ElMessage.success(isEdit.value ? '更新成功' : '添加成功')
          dialogVisible.value = false
          loadPermissions()
        } else {
          ElMessage.error(res.message || '操作失败')
        }
      } catch (error) {
        ElMessage.error('网络错误')
      }
    }
  })
}

const formatDate = (dateStr) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN')
}

onMounted(() => {
  loadPermissions()
})
</script>

<style scoped>
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
</style>