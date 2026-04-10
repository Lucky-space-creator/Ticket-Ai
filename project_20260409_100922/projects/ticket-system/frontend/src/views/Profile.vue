<template>
  <div class="profile-page">
    <div class="page-header">
      <h2>个人信息管理</h2>
    </div>

    <div class="profile-content">
      <!-- 基本信息卡片 -->
      <el-card class="info-card">
        <template #header>
          <div class="card-header">
            <span>基本信息</span>
            <el-button
                v-if="!isEditing"
                type="primary"
                link
                @click="startEdit"
            >
              编辑
            </el-button>
            <template v-else>
              <el-button type="primary" size="small" @click="saveProfile">保存</el-button>
              <el-button size="small" @click="cancelEdit">取消</el-button>
            </template>
          </div>
        </template>

        <el-form
            :model="profileForm"
            label-width="100px"
            class="profile-form"
        >
          <el-form-item label="手机号">
            <el-input v-model="profileForm.phone" disabled />
          </el-form-item>
          <el-form-item label="真实姓名">
            <el-input
                v-model="profileForm.realName"
                :disabled="!isEditing"
                placeholder="请输入真实姓名"
            />
          </el-form-item>
          <el-form-item label="身份证号">
            <el-input
                v-model="profileForm.idCard"
                :disabled="!isEditing"
                placeholder="请输入身份证号"
                maxlength="18"
                show-word-limit
            />
            <div class="id-card-tip" v-if="!isEditing && profileForm.idCard">
              {{ formatIdCard(profileForm.idCard) }}
            </div>
          </el-form-item>
          <el-form-item label="账号状态">
            <el-tag :type="profileForm.status === 1 ? 'success' : 'danger'">
              {{ profileForm.status === 1 ? '正常' : '异常' }}
            </el-tag>
          </el-form-item>
        </el-form>
      </el-card>

      <!-- 常用联系人卡片 -->
      <el-card class="contacts-card">
        <template #header>
          <div class="card-header">
            <span>常用联系人</span>
            <el-button type="primary" size="small" @click="showAddDialog = true">
              添加联系人
            </el-button>
          </div>
        </template>

        <el-table :data="passengers" v-loading="loadingPassengers">
          <el-table-column prop="name" label="姓名" />
          <el-table-column label="身份证号">
            <template #default="{ row }">
              {{ formatIdCard(row.idCard) }}
            </template>
          </el-table-column>
          <el-table-column prop="phone" label="手机号" />
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button
                  type="danger"
                  link
                  size="small"
                  @click="handleDeletePassenger(row)"
              >
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-empty
            v-if="!loadingPassengers && passengers.length === 0"
            description="暂无常用联系人"
        />
      </el-card>
    </div>

    <!-- 添加联系人对话框 -->
    <el-dialog
        v-model="showAddDialog"
        title="添加常用联系人"
        width="400px"
    >
      <el-form
          :model="passengerForm"
          :rules="passengerRules"
          ref="passengerFormRef"
          label-width="80px"
      >
        <el-form-item label="姓名" prop="name">
          <el-input v-model="passengerForm.name" placeholder="请输入联系人姓名" />
        </el-form-item>
        <el-form-item label="身份证" prop="idCard">
          <el-input
              v-model="passengerForm.idCard"
              placeholder="请输入身份证号"
              maxlength="18"
              show-word-limit
          />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="passengerForm.phone" placeholder="请输入手机号" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" @click="handleAddPassenger">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

const router = useRouter()

// 基本信息
const profileForm = reactive({
  phone: '',
  realName: '',
  idCard: '',
  status: 1
})
const originalProfile = ref({})
const isEditing = ref(false)

// 常用联系人
const passengers = ref([])
const loadingPassengers = ref(false)

// 添加联系人
const showAddDialog = ref(false)
const passengerForm = reactive({
  name: '',
  idCard: '',
  phone: ''
})
const passengerFormRef = ref(null)
const passengerRules = {
  name: [{ required: true, message: '请输入联系人姓名', trigger: 'blur' }],
  idCard: [
    { required: true, message: '请输入身份证号', trigger: 'blur' },
    { pattern: /(^\d{15}$)|(^\d{18}$)|(^\d{17}(\d|X|x)$)/, message: '身份证号格式不正确', trigger: 'blur' }
  ],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ]
}

// 获取个人信息
const fetchProfile = async () => {
  try {
    const res = await request.get('/user/profile')
    const data = res.data
    profileForm.phone = data.phone
    profileForm.realName = data.realName
    profileForm.idCard = data.idCard
    profileForm.status = data.status
    originalProfile.value = { ...profileForm }
  } catch (error) {
    ElMessage.error('获取个人信息失败')
  }
}

// 编辑个人信息
const startEdit = () => {
  originalProfile.value = { ...profileForm }
  isEditing.value = true
}

const cancelEdit = () => {
  Object.assign(profileForm, originalProfile.value)
  isEditing.value = false
}

const saveProfile = async () => {
  try {
    await request.put('/user/profile', {
      realName: profileForm.realName,
      idCard: profileForm.idCard
    })
    ElMessage.success('保存成功')
    isEditing.value = false
    fetchProfile()
  } catch (error) {
    ElMessage.error('保存失败')
  }
}

// 获取常用联系人
const fetchPassengers = async () => {
  loadingPassengers.value = true
  try {
    const res = await request.get('/passengers')
    passengers.value = res.data || []
  } catch (error) {
    ElMessage.error('获取联系人失败')
  } finally {
    loadingPassengers.value = false
  }
}

// 添加联系人
const handleAddPassenger = async () => {
  try {
    await passengerFormRef.value.validate()
    await request.post('/passengers', {
      name: passengerForm.name,
      idCard: passengerForm.idCard,
      phone: passengerForm.phone
    })
    ElMessage.success('添加成功')
    showAddDialog.value = false
    passengerFormRef.value.resetFields()
    fetchPassengers()
  } catch (error) {
    // 验证失败
  }
}

// 删除联系人
const handleDeletePassenger = (row) => {
  ElMessageBox.confirm('确定要删除该联系人吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await request.delete(`/passengers/${row.id}`)
      ElMessage.success('删除成功')
      fetchPassengers()
    } catch (error) {
      ElMessage.error('删除失败')
    }
  }).catch(() => {
  })
}

// 格式化身份证显示
const formatIdCard = (idCard) => {
  if (!idCard || idCard.length < 18) return idCard
  return idCard.substring(0, 3) + '***********' + idCard.substring(14)
}

onMounted(() => {
  fetchProfile()
  fetchPassengers()
})
</script>

<style lang="scss" scoped>
.profile-page {
  min-height: 100vh;
  background-color: #f5f5f5;
  padding: 20px;

  .page-header {
    max-width: 900px;
    margin: 0 auto 20px;

    h2 {
      margin: 0;
      font-size: 20px;
      color: #333;
    }
  }

  .profile-content {
    max-width: 900px;
    margin: 0 auto;
    display: flex;
    flex-direction: column;
    gap: 20px;
  }

  .info-card, .contacts-card {
    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
  }

  .profile-form {
    max-width: 500px;

    .id-card-tip {
      font-size: 12px;
      color: #999;
      margin-top: 4px;
    }
  }
}
</style>
