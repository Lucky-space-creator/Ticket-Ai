<template>
  <div class="role-list">
    <div class="header">
      <h2>角色管理</h2>
      <el-button type="primary" @click="handleAdd">添加角色</el-button>
    </div>

    <el-table :data="roleList" border stripe style="width: 100%">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="roleName" label="角色标识" />
      <el-table-column prop="roleDisplayName" label="角色名称" />
      <el-table-column prop="description" label="描述" />
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
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button size="small" @click="handleEdit(row)">编辑</el-button>
          <el-button size="small" @click="handlePermission(row)">权限</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 添加/编辑角色对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="角色标识" prop="roleName">
          <el-input v-model="form.roleName" placeholder="请输入角色标识，如admin" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="角色名称" prop="roleDisplayName">
          <el-input v-model="form.roleDisplayName" placeholder="请输入角色显示名称" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" placeholder="请输入角色描述" />
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

    <!-- 权限分配对话框 -->
    <el-dialog v-model="permissionDialogVisible" title="权限分配" width="700px">
      <el-tree
        ref="treeRef"
        :data="permissionTree"
        show-checkbox
        node-key="id"
        :props="treeProps"
        :default-checked-keys="checkedKeys"
      />
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="permissionDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="savePermissions">保存</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

const roleList = ref([])
const permissionTree = ref([])
const checkedKeys = ref([])
const dialogVisible = ref(false)
const permissionDialogVisible = ref(false)
const formRef = ref(null)
const treeRef = ref(null)
const isEdit = ref(false)
const currentRoleId = ref(null)

const form = ref({
  roleName: '',
  roleDisplayName: '',
  description: '',
  status: 1
})

const rules = {
  roleName: [
    { required: true, message: '请输入角色标识', trigger: 'blur' }
  ],
  roleDisplayName: [
    { required: true, message: '请输入角色名称', trigger: 'blur' }
  ]
}

const treeProps = {
  label: 'permissionDisplayName',
  children: 'children'
}

const dialogTitle = computed(() => isEdit.value ? '编辑角色' : '添加角色')

// 加载角色列表
const loadRoles = async () => {
  try {
    const res = await request.get('/api/admin/roles')
    if (res.code === 200) {
      roleList.value = res.data
    } else {
      ElMessage.error(res.message || '加载失败')
    }
  } catch (error) {
    ElMessage.error('网络错误')
  }
}

// 加载权限树
const loadPermissions = async () => {
  try {
    const res = await request.get('/api/admin/permissions/tree')
    if (res.code === 200) {
      permissionTree.value = res.data
    }
  } catch (error) {
    console.error('加载权限树失败', error)
  }
}

// 加载角色权限
const loadRolePermissions = async (roleId) => {
  try {
    const res = await request.get(`/api/admin/roles/${roleId}/permissions`)
    if (res.code === 200) {
      checkedKeys.value = res.data
    }
  } catch (error) {
    console.error('加载角色权限失败', error)
  }
}

const handleAdd = () => {
  isEdit.value = false
  form.value = {
    roleName: '',
    roleDisplayName: '',
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

const handlePermission = async (row) => {
  currentRoleId.value = row.id
  await loadPermissions()
  await loadRolePermissions(row.id)
  permissionDialogVisible.value = true
}

const handleDelete = (row) => {
  ElMessageBox.confirm(`确定删除角色 "${row.roleDisplayName}" 吗？`, '提示', {
    type: 'warning'
  }).then(async () => {
    try {
      const res = await request.delete(`/api/admin/roles/${row.id}`)
      if (res.code === 200) {
        ElMessage.success('删除成功')
        loadRoles()
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
          res = await request.put(`/api/admin/roles/${form.value.id}`, form.value)
        } else {
          res = await request.post('/api/admin/roles', form.value)
        }
        if (res.code === 200) {
          ElMessage.success(isEdit.value ? '更新成功' : '添加成功')
          dialogVisible.value = false
          loadRoles()
        } else {
          ElMessage.error(res.message || '操作失败')
        }
      } catch (error) {
        ElMessage.error('网络错误')
      }
    }
  })
}

const savePermissions = async () => {
  const checked = treeRef.value.getCheckedKeys()
  try {
    const res = await request.post(`/api/admin/roles/${currentRoleId.value}/permissions`, {
      permissionIds: checked
    })
    if (res.code === 200) {
      ElMessage.success('权限分配成功')
      permissionDialogVisible.value = false
    } else {
      ElMessage.error(res.message || '分配失败')
    }
  } catch (error) {
    ElMessage.error('网络错误')
  }
}

const formatDate = (dateStr) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN')
}

onMounted(() => {
  loadRoles()
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