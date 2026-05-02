<template>
  <div class="customer-service-container">
    <el-row :gutter="20" class="full-height">
      <!-- 左侧会话列表 -->
      <el-col :span="6" class="session-panel">
        <div class="panel-header">
          <h3>会话列表</h3>
          <div class="header-actions">
            <el-button type="primary" size="small" @click="refreshSessions">刷新</el-button>
          </div>
        </div>
        <el-tabs v-model="activeTab" @tab-click="handleTabClick">
          <el-tab-pane label="待接入" name="pending">
            <div class="session-list">
              <div v-for="session in pendingSessions" :key="session.sessionId" 
                   class="session-item" 
                   :class="{active: activeSessionId === session.sessionId}"
                   @click="selectSession(session)">
                <div class="session-info">
                  <div class="session-user">
                    <el-icon><User /></el-icon>
                    <span class="user-id">用户 {{ session.userId || '匿名' }}</span>
                  </div>
                  <div class="session-preview">{{ session.lastMessage }}</div>
                  <div class="session-time">{{ formatTime(session.lastMessageTime) }}</div>
                </div>
                <el-badge :value="session.unreadCount" :max="99" v-if="session.unreadCount > 0" />
              </div>
              <div v-if="pendingSessions.length === 0" class="empty-tip">
                <el-empty description="暂无待接入会话" />
              </div>
            </div>
          </el-tab-pane>
          <el-tab-pane label="服务中" name="serving">
            <div class="session-list">
              <div v-for="session in servingSessions" :key="session.sessionId" 
                   class="session-item" 
                   :class="{active: activeSessionId === session.sessionId}"
                   @click="selectSession(session)">
                <div class="session-info">
                  <div class="session-user">
                    <el-icon><User /></el-icon>
                    <span class="user-id">用户 {{ session.userId || '匿名' }}</span>
                  </div>
                  <div class="session-preview">{{ session.lastMessage }}</div>
                  <div class="session-time">{{ formatTime(session.lastMessageTime) }}</div>
                </div>
                <el-badge :value="session.unreadCount" :max="99" v-if="session.unreadCount > 0" />
              </div>
              <div v-if="servingSessions.length === 0" class="empty-tip">
                <el-empty description="暂无服务中会话" />
              </div>
            </div>
          </el-tab-pane>
          <el-tab-pane label="已结束" name="ended">
            <div class="session-list">
              <div v-for="session in endedSessions" :key="session.sessionId" 
                   class="session-item" 
                   :class="{active: activeSessionId === session.sessionId}"
                   @click="selectSession(session)">
                <div class="session-info">
                  <div class="session-user">
                    <el-icon><User /></el-icon>
                    <span class="user-id">用户 {{ session.userId || '匿名' }}</span>
                  </div>
                  <div class="session-preview">{{ session.lastMessage }}</div>
                  <div class="session-time">{{ formatTime(session.lastMessageTime) }}</div>
                </div>
                <el-tag type="info" size="small" effect="plain">已结束</el-tag>
              </div>
              <div v-if="endedSessions.length === 0" class="empty-tip">
                <el-empty description="暂无已结束会话" />
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </el-col>

      <!-- 右侧聊天区域 -->
      <el-col :span="18" class="chat-panel">
        <div class="chat-header" v-if="activeSession">
          <div class="header-left">
            <el-icon><ChatDotRound /></el-icon>
            <span class="session-title">用户 {{ activeSession.userId || '匿名' }}</span>
            <el-tag type="info" size="small">{{ activeSessionId }}</el-tag>
            <el-tag v-if="isSessionEnded" type="info" size="small" effect="plain">已结束</el-tag>
          </div>
          <div class="header-right">
            <el-button type="success" size="small" v-if="activeTab === 'pending'" @click="acceptSession">
              接入会话
            </el-button>
            <el-button type="danger" size="small" v-else-if="activeTab === 'serving' && !isSessionEnded" @click="endSession">
              结束会话
            </el-button>
          </div>
        </div>
        <div class="chat-messages" ref="messagesContainer">
          <div v-for="msg in messages" :key="msg.id" class="message-item" :class="getMessageClass(msg)">
            <div class="message-content">
              <div class="message-text">{{ msg.message }}</div>
              <div class="message-meta">
                <span class="message-sender">{{ getSenderName(msg) }}</span>
                <span class="message-time">{{ formatTime(msg.createdAt) }}</span>
              </div>
            </div>
          </div>
          <div v-if="messages.length === 0" class="empty-chat">
            <el-empty description="暂无消息记录" />
          </div>
        </div>
        <div v-if="activeSession">
          <div class="chat-input" v-if="!isSessionEnded">
            <el-input
                v-model="inputMessage"
                type="textarea"
                :rows="3"
                placeholder="输入消息..."
                resize="none"
                @keydown.enter.prevent="sendMessage"
            />
            <div class="input-actions">
              <el-button type="primary" @click="sendMessage" :loading="sending">发送</el-button>
            </div>
          </div>
          <div v-else class="chat-ended-tip">
            <el-alert type="info" title="会话已结束，无法发送新消息" :closable="false" center />
          </div>
        </div>
        <div v-else class="chat-placeholder">
          <el-empty description="请从左侧选择一个会话" />
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { User, ChatDotRound } from '@element-plus/icons-vue'
import { getPendingSessions, getServingSessions, getEndedSessions, acceptSession as apiAcceptSession, endSession as apiEndSession, getHistory, sendMessage as apiSendMessage } from '@/api/customerService'
import { formatTime } from '@/utils/date'
import { useWebSocket } from '@/composables/useWebSocket'
import { getWsBaseUrl } from '@/utils/wsBase'

// 响应式数据
const activeTab = ref('pending')
const pendingSessions = ref([])
const servingSessions = ref([])
const endedSessions = ref([])
const activeSessionId = ref(null)
const activeSession = ref(null)
const messages = ref([])
const inputMessage = ref('')
const sending = ref(false)
const messagesContainer = ref(null)
let intervalId = null
let messageRefreshInterval = null

// WebSocket 连接
const { connect, disconnect, send, isConnected } = useWebSocket()

// 计算属性
const allSessions = computed(() => {
  if (activeTab.value === 'pending') {
    return pendingSessions.value
  } else if (activeTab.value === 'serving') {
    return servingSessions.value
  } else {
    return endedSessions.value
  }
})

// 会话是否已结束
const isSessionEnded = computed(() => {
  return activeTab.value === 'ended' || 
         (activeSession.value && 
          activeSession.value.sessionId && 
          endedSessions.value.some(s => s.sessionId === activeSession.value.sessionId))
})

// 初始化
onMounted(() => {
  initWebSocket()
  refreshSessions()
  // 添加定期刷新，30秒一次（作为WebSocket的后备）
  intervalId = setInterval(refreshSessions, 30000)
  // 添加消息自动刷新，每2s一次
  messageRefreshInterval = setInterval(refreshCurrentSessionMessages, 2000)
})

onUnmounted(() => {
  disconnect()
  if (intervalId) {
    clearInterval(intervalId)
  }
  if (messageRefreshInterval) {
    clearInterval(messageRefreshInterval)
  }
})

// 初始化 WebSocket
const initWebSocket = () => {
  // 与网关 /ws/** 或 Vite 代理到 customer-service 一致
  const wsUrl = `${getWsBaseUrl()}/ws/chat/global`
  connect(wsUrl, {
    onMessage: handleWebSocketMessage,
    onOpen: () => console.log('WebSocket connected'),
    onClose: () => console.log('WebSocket closed'),
    onError: (err) => console.error('WebSocket error', err)
  })
}

// 处理 WebSocket 消息
const handleWebSocketMessage = (data) => {
  if (data.type === 'chat') {
    // 只处理当前会话的消息
    if (data.sessionId === activeSessionId.value) {
      messages.value.push({
        id: Date.now(),
        message: data.content,
        msgType: data.msgType,
        employeeId: data.employeeId,
        userId: data.userId,
        createdAt: new Date(data.timestamp)
      })
      scrollToBottom()
    }
  } else if (data.type === 'pong') {
    // 心跳响应
    console.log('收到 pong')
  } else if (data.type === 'notification') {
    // 全局通知
    console.log('收到全局通知:', data)
    if (data.notificationType === 'new_pending_session') {
      // 新待接入会话通知
      if (activeTab.value === 'pending') {
        // 如果在待接入标签页，自动刷新列表
        refreshSessions()
      } else {
        // 在其他标签页，显示提示
        ElMessage.info('有新的用户请求人工客服')
      }
    }
  }
}

// 刷新会话列表
const refreshSessions = async () => {
  try {
    const [pendingRes, servingRes, endedRes] = await Promise.all([
      getPendingSessions(),
      getServingSessions(),
      getEndedSessions()
    ])
    if (pendingRes.code === 200) {
      pendingSessions.value = pendingRes.data.sessions || []
    }
    if (servingRes.code === 200) {
      servingSessions.value = servingRes.data.sessions || []
    }
    if (endedRes.code === 200) {
      endedSessions.value = endedRes.data.sessions || []
    }
  } catch (error) {
    ElMessage.error('获取会话列表失败')
    console.error(error)
  }
}

// 刷新当前会话的消息（轮询后备）
const refreshCurrentSessionMessages = async () => {
  if (!activeSessionId.value || !activeSession.value) {
    return
  }
  // 如果会话已结束，不需要刷新
  if (isSessionEnded.value) {
    return
  }
  try {
    const res = await getHistory({ sessionId: activeSessionId.value })
    if (res.code === 200) {
      const newMessages = res.data.messages || []
      // 简单去重：如果消息数量不同，则替换
      if (newMessages.length !== messages.value.length) {
        messages.value = newMessages
        scrollToBottom()
      }
    }
  } catch (error) {
    // 静默失败，避免频繁报错
    console.error('刷新消息失败', error)
  }
}

// 标签页切换
const handleTabClick = () => {
  activeSessionId.value = null
  activeSession.value = null
  messages.value = []
}

// 选择会话
const selectSession = async (session) => {
  activeSessionId.value = session.sessionId
  activeSession.value = session
  messages.value = []

  // 加载历史消息
  try {
    const res = await getHistory({ sessionId: session.sessionId })
    if (res.code === 200) {
      messages.value = res.data.messages || []
      scrollToBottom()
    }
  } catch (error) {
    ElMessage.error('加载历史消息失败')
    console.error(error)
  }
}

// 接入会话
const acceptSession = async () => {
  if (!activeSessionId.value) {
    ElMessage.warning('请先选择会话')
    return
  }

  try {
    const res = await apiAcceptSession({ sessionId: activeSessionId.value })
    if (res.code === 200) {
      ElMessage.success('会话接入成功')
      // 刷新列表，会话会从待接入移到服务中
      refreshSessions()
      activeTab.value = 'serving'
      // 重新加载消息
      selectSession(activeSession.value)
    } else {
      ElMessage.error(res.message || '接入失败')
    }
  } catch (error) {
    ElMessage.error('接入会话失败')
    console.error(error)
  }
}

// 结束会话
const endSession = async () => {
  if (!activeSessionId.value) {
    return
  }

  try {
    await ElMessageBox.confirm('确定结束本次会话吗？', '提示', {
      type: 'warning'
    })
    const res = await apiEndSession({ sessionId: activeSessionId.value })
    if (res.code === 200) {
      ElMessage.success('会话已结束')
      // 刷新列表
      refreshSessions()
      // 清空当前会话
      activeSessionId.value = null
      activeSession.value = null
      messages.value = []
    } else {
      ElMessage.error(res.message || '结束失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('结束会话失败')
      console.error(error)
    }
  }
}

// 发送消息
const sendMessage = async () => {
  if (!inputMessage.value.trim()) {
    ElMessage.warning('消息内容不能为空')
    return
  }
  if (!activeSessionId.value || !activeSession.value) {
    ElMessage.warning('请先选择会话')
    return
  }
  
  // 检查会话是否已结束
  if (isSessionEnded.value) {
    ElMessage.warning('会话已结束，无法发送消息')
    return
  }

  const content = inputMessage.value.trim()
  sending.value = true
  try {
    const res = await apiSendMessage({
      sessionId: activeSessionId.value,
      content: content,
      userId: activeSession.value.userId
    })
    if (res.code === 200) {
      // 在本地添加消息，以便立即显示
      messages.value.push({
        id: Date.now(),
        message: content,
        msgType: 'employee', // 占位符，实际员工ID由后端确定
        employeeId: null,
        userId: activeSession.value.userId,
        createdAt: new Date()
      })
      scrollToBottom()
      // 清空输入框
      inputMessage.value = ''
    } else {
      ElMessage.error(res.message || '发送失败')
    }
  } catch (error) {
    ElMessage.error('发送消息失败')
    console.error(error)
  } finally {
    sending.value = false
  }
}

// 消息样式类
const getMessageClass = (msg) => {
  if (msg.msgType === 'user') {
    return 'message-user'
  } else if (msg.msgType === 'robot') {
    return 'message-robot'
  } else {
    return 'message-employee'
  }
}

// 发送者名称
const getSenderName = (msg) => {
  if (msg.msgType === 'user') {
    return '用户'
  } else if (msg.msgType === 'robot') {
    return 'AI客服'
  } else {
    return '客服'
  }
}

// 滚动到底部
const scrollToBottom = () => {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  })
}
</script>

<style scoped>
.customer-service-container {
  height: calc(100vh - 120px);
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
}

.full-height {
  height: 100%;
}

.session-panel {
  border-right: 1px solid #e6e6e6;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.panel-header {
  padding: 16px;
  border-bottom: 1px solid #e6e6e6;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.panel-header h3 {
  margin: 0;
  font-size: 16px;
}

.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.session-item {
  padding: 12px;
  border-radius: 6px;
  margin-bottom: 8px;
  cursor: pointer;
  border: 1px solid #f0f0f0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  transition: all 0.3s;
}

.session-item:hover {
  background-color: #f5f7fa;
  border-color: #409eff;
}

.session-item.active {
  background-color: #e8f4ff;
  border-color: #409eff;
}

.session-user {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
}

.user-id {
  font-weight: 500;
  font-size: 14px;
}

.session-preview {
  font-size: 13px;
  color: #666;
  margin-bottom: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.session-time {
  font-size: 12px;
  color: #999;
}

.empty-tip {
  padding: 40px 20px;
  text-align: center;
  color: #999;
}

.chat-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.chat-header {
  padding: 16px;
  border-bottom: 1px solid #e6e6e6;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.session-title {
  font-weight: 500;
  font-size: 16px;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  background-color: #f9f9f9;
}

.message-item {
  margin-bottom: 16px;
  display: flex;
}

.message-content {
  max-width: 70%;
  padding: 10px 14px;
  border-radius: 8px;
  position: relative;
}

.message-user .message-content {
  background-color: #fff;
  border: 1px solid #e6e6e6;
  margin-left: auto;
}

.message-robot .message-content {
  background-color: #f0f9ff;
  border: 1px solid #c6e2ff;
}

.message-employee .message-content {
  background-color: #e8f5e9;
  border: 1px solid #c8e6c9;
}

.message-text {
  font-size: 14px;
  line-height: 1.5;
  word-break: break-word;
}

.message-meta {
  margin-top: 4px;
  font-size: 12px;
  color: #999;
  display: flex;
  justify-content: space-between;
}

.empty-chat {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.chat-input {
  border-top: 1px solid #e6e6e6;
  padding: 16px;
}

.input-actions {
  margin-top: 12px;
  text-align: right;
}

.chat-placeholder {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.chat-ended-tip {
  border-top: 1px solid #e6e6e6;
  padding: 16px;
  text-align: center;
}
</style>
