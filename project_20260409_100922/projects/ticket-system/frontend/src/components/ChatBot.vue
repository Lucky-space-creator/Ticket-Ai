<template>
  <div class="chat-container">
    <!-- 聊天按钮 -->
    <div class="chat-toggle" @click="toggleChat" v-if="isMinimized">
      <el-badge :value="unreadCount" :hidden="unreadCount === 0" :max="99">
        <el-icon size="28"><ChatDotRound /></el-icon>
      </el-badge>
    </div>

    <!-- 聊天窗口 -->
    <div class="chat-window" v-show="!isMinimized">
      <div class="chat-header">
        <div class="header-info">
          <el-icon size="20"><Service /></el-icon>
          <span>智能客服</span>
          <span class="connection-status" :class="connectionStatusClass">
            {{ connectionStatusText }}
          </span>
        </div>
        <div class="header-actions">
          <el-button
              type="primary"
              link
              size="small"
              @click="loadHistory"
              :loading="loadingHistory"
          >
            刷新
          </el-button>
          <el-button
              type="primary"
              link
              size="small"
              @click="clearHistory"
          >
            清空会话
          </el-button>
          <el-button
              type="primary"
              link
              size="small"
              @click="isMinimized = true"
          >
            <el-icon><Minus /></el-icon>
          </el-button>
        </div>
      </div>

      <div class="chat-messages" ref="messagesRef">
        <!-- 欢迎消息 -->
        <div class="welcome-tip" v-if="messages.length === 0">
          <div class="tip-icon">
            <el-icon size="40" color="#409EFF"><ChatLineRound /></el-icon>
          </div>
          <p>您好，我是12306智能客服</p>
          <p>我可以帮您解答购票、改签、退票等问题</p>
          <div class="quick-questions">
            <span
                v-for="q in quickQuestions"
                :key="q"
                class="quick-btn"
                @click="sendQuickQuestion(q)"
            >
              {{ q }}
            </span>
          </div>
        </div>

        <!-- 消息列表 -->
        <div
            v-for="(msg, index) in messages"
            :key="index"
            class="message-item"
            :class="msg.role"
        >
          <div class="avatar">
            <el-icon v-if="msg.role === 'user'" size="20"><User /></el-icon>
            <el-icon v-else size="20"><Service /></el-icon>
          </div>
          <div class="message-content">
            <div class="message-text" v-html="formatMessage(msg.content)"></div>
            <div class="message-time">{{ formatTime(msg.time) }}</div>
          </div>
        </div>

        <!-- 加载中 -->
        <div class="message-item assistant" v-if="loading">
          <div class="avatar">
            <el-icon size="20"><Service /></el-icon>
          </div>
          <div class="message-content">
            <div class="message-text loading">
              <span class="dot"></span>
              <span class="dot"></span>
              <span class="dot"></span>
            </div>
          </div>
        </div>
      </div>

      <div class="chat-input">
        <el-input
            v-model="inputText"
            type="textarea"
            :rows="2"
            placeholder="请输入您的问题..."
            @keydown.enter.ctrl="sendMessage"
            resize="none"
        />
        <div class="input-footer">
          <span class="hint">按 Ctrl+Enter 发送</span>
          <div class="input-actions">
            <el-button
                type="warning"
                :loading="transferring"
                @click="requestHumanService"
            >
              转人工客服
            </el-button>
            <el-button
                v-if="isHumanService && !isSessionEnded"
                type="danger"
                @click="endSession"
            >
              结束对话
            </el-button>
            <el-button
                type="primary"
                :disabled="!inputText.trim() || loading"
                @click="sendMessage"
            >
              发送
            </el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, nextTick, onMounted, onUnmounted, watch, computed } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { useUserStore } from '@/stores/user'

const isMinimized = ref(true)
const messages = ref([])
const inputText = ref('')
const loading = ref(false)
const unreadCount = ref(0)
const transferring = ref(false)
const messagesRef = ref(null)
const userStore = useUserStore()

// WebSocket 相关
const socket = ref(null)
const isConnected = ref(false)
const isHumanService = ref(false) // 是否客服已介入
const isSessionEnded = ref(false) // 会话是否已结束
const sessionId = ref('')
const reconnectAttempts = ref(0)
const maxReconnectAttempts = 5
const reconnectDelay = 3000
const heartbeatInterval = ref(null)
const loadingHistory = ref(false)
const connectionStatus = ref('disconnected') // 'connecting', 'connected', 'disconnected'
const autoRefreshInterval = ref(null)
const isRefreshing = ref(false)

// 连接状态计算属性
const connectionStatusClass = computed(() => {
  switch (connectionStatus.value) {
    case 'connected': return 'status-connected'
    case 'connecting': return 'status-connecting'
    default: return 'status-disconnected'
  }
})
const connectionStatusText = computed(() => {
  switch (connectionStatus.value) {
    case 'connected': return '在线'
    case 'connecting': return '连接中'
    default: return '离线'
  }
})

// 监听用户状态变化
watch(() => userStore.token, (newToken, oldToken) => {
  if (!newToken) {
    // 用户注销，清空消息和未读计数
    messages.value = []
    unreadCount.value = 0
    inputText.value = ''
    disconnectWebSocket()
  } else if (oldToken && newToken !== oldToken) {
    // token发生变化（可能是重新登录或切换账户），清空消息
    messages.value = []
    unreadCount.value = 0
    inputText.value = ''
    disconnectWebSocket()
  }
})

// 初始化 WebSocket 连接
onMounted(() => {
  initWebSocket()
  // 启动自动刷新，每500ms检查新消息
  autoRefreshInterval.value = setInterval(() => {
    if (!isRefreshing.value && !loadingHistory.value && sessionId.value) {
      autoRefresh()
    }
  }, 500)
})

// 初始化 WebSocket
const initWebSocket = () => {
  if (!userStore.token) return
  const userId = userStore.userInfo?.id
  if (!userId) return
  sessionId.value = 'user_' + userId
  connectWebSocket()
}

// 连接 WebSocket（支持重连）
const connectWebSocket = () => {
  if (socket.value && socket.value.readyState === WebSocket.OPEN) {
    console.log('WebSocket 已连接')
    return
  }

  // 更新连接状态
  connectionStatus.value = 'connecting'
  
  const wsUrl = `ws://localhost:8080/ws/chat/${sessionId.value}`
  try {
    socket.value = new WebSocket(wsUrl)
    
    socket.value.onopen = () => {
      console.log('WebSocket 连接成功')
      isConnected.value = true
      connectionStatus.value = 'connected'
      reconnectAttempts.value = 0
      // 启动心跳
      startHeartbeat()
      // 加载历史消息
      loadHistory()
    }
    
    socket.value.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data)
        if (data.type === 'chat') {
          // 判断消息类型
          if (data.msgType === 'user') {
            // 用户消息（可能是自己发送的，已经显示过，忽略）
          } else if (data.msgType === 'robot') {
            // AI 回复
            messages.value.push({
              role: 'assistant',
              content: data.content,
              time: new Date(data.timestamp)
            })
            scrollToBottom()
          } else if (data.msgType === 'ended') {
            // 会话结束消息
            isSessionEnded.value = true
            isHumanService.value = false
            messages.value.push({
              role: 'assistant',
              content: data.content,
              time: new Date(data.timestamp)
            })
            scrollToBottom()
          } else if (data.msgType === 'pending') {
            // 待接入消息，忽略（避免重复显示）
          } else {
            // 客服消息（msgType 为员工号）
            isHumanService.value = true
            messages.value.push({
              role: 'assistant',
              content: data.content,
              time: new Date(data.timestamp)
            })
            scrollToBottom()
          }
          // 如果窗口最小化，增加未读计数
          if (isMinimized.value) {
            unreadCount.value++
          }
        }
      } catch (error) {
        console.error('解析 WebSocket 消息失败', error)
      }
    }
    
    socket.value.onclose = (event) => {
      console.log('WebSocket 连接关闭', event.code, event.reason)
      isConnected.value = false
      connectionStatus.value = 'disconnected'
      stopHeartbeat()
      
      // 尝试重连
      if (reconnectAttempts.value < maxReconnectAttempts) {
        reconnectAttempts.value++
        console.log(`尝试重连 (${reconnectAttempts.value}/${maxReconnectAttempts})...`)
        setTimeout(() => {
          connectWebSocket()
        }, reconnectDelay)
      } else {
        console.log('重连次数已达上限，停止重连')
      }
    }
    
    socket.value.onerror = (error) => {
      console.error('WebSocket 错误', error)
      connectionStatus.value = 'disconnected'
    }
  } catch (error) {
    console.error('创建 WebSocket 连接失败', error)
    connectionStatus.value = 'disconnected'
  }
}

// 启动心跳
const startHeartbeat = () => {
  stopHeartbeat()
  heartbeatInterval.value = setInterval(() => {
    if (socket.value && socket.value.readyState === WebSocket.OPEN) {
      socket.value.send(JSON.stringify({ type: 'heartbeat' }))
    }
  }, 30000) // 每30秒发送一次心跳
}

// 停止心跳
const stopHeartbeat = () => {
  if (heartbeatInterval.value) {
    clearInterval(heartbeatInterval.value)
    heartbeatInterval.value = null
  }
}

// 加载历史消息
async function loadHistory() {
  loadingHistory.value = true
  try {
    const result = await request.get('/customer-service/user-history')
    if (result.code === 200 && result.data.messages) {
      // 将历史消息转换为前端格式并添加到消息列表
      const historyMessages = result.data.messages.map(msg => ({
        role: msg.msgType === 'user' ? 'user' : 'assistant',
        content: msg.message,
        time: new Date(msg.createdAt)
      }))
      // 清空现有消息，添加历史消息
      messages.value = historyMessages
      scrollToBottom()
    }
  } catch (error) {
    console.error('加载历史消息失败', error)
    ElMessage.error('加载历史消息失败，请重试')
  } finally {
    loadingHistory.value = false
  }
}

// 自动刷新消息（轮询后备）
async function autoRefresh() {
  if (isRefreshing.value || loadingHistory.value || !sessionId.value) {
    return
  }
  isRefreshing.value = true
  try {
    // 调用历史消息接口，获取最新消息
    const result = await request.get('/customer-service/user-history')
    if (result.code === 200 && result.data.messages) {
      // 将历史消息转换为前端格式
      const historyMessages = result.data.messages.map(msg => ({
        role: msg.msgType === 'user' ? 'user' : 'assistant',
        content: msg.message,
        time: new Date(msg.createdAt)
      }))
      // 如果消息数量不同，则更新（简单去重）
      if (historyMessages.length !== messages.value.length) {
        messages.value = historyMessages
        scrollToBottom()
      }
    }
  } catch (error) {
    // 静默失败，避免频繁报错
    console.error('自动刷新消息失败', error)
  } finally {
    isRefreshing.value = false
  }
}

// 组件卸载时断开连接
onUnmounted(() => {
  disconnectWebSocket()
  if (autoRefreshInterval.value) {
    clearInterval(autoRefreshInterval.value)
    autoRefreshInterval.value = null
  }
})

// 断开 WebSocket 连接
const disconnectWebSocket = () => {
  stopHeartbeat()
  if (socket.value && socket.value.readyState === WebSocket.OPEN) {
    socket.value.close()
  }
  socket.value = null
  isConnected.value = false
  connectionStatus.value = 'disconnected'
  isHumanService.value = false
  isSessionEnded.value = false
  sessionId.value = ''
}

const quickQuestions = [
  '如何购买火车票',
  '如何改签',
  '如何退票',
  '学生票怎么买'
]

// 切换聊天窗口
function toggleChat() {
  isMinimized.value = !isMinimized.value
  if (!isMinimized.value) {
    unreadCount.value = 0
    nextTick(() => {
      scrollToBottom()
    })
  }
}

// 结束对话（用户主动结束）
async function endSession() {
  if (!sessionId.value) return
  try {
    const result = await request.post('/customer-service/user-end-session', {
      sessionId: sessionId.value
    })
    if (result.code === 200) {
      ElMessage.success('会话已结束')
      isSessionEnded.value = true
      isHumanService.value = false
    } else {
      ElMessage.error(result.message || '结束会话失败')
    }
  } catch (error) {
    ElMessage.error('结束会话失败')
    console.error(error)
  }
}

// 发送消息
async function sendMessage() {
  if (!inputText.value.trim() || loading.value) return

  const text = inputText.value.trim()
  inputText.value = ''

  // 添加用户消息
  messages.value.push({
    role: 'user',
    content: text,
    time: new Date()
  })
  scrollToBottom()

  loading.value = true

  try {
    if (isHumanService.value && !isSessionEnded.value) {
      // 客服已介入，使用用户发送消息API
      const result = await request.post('/customer-service/user-send-message', { 
        sessionId: sessionId.value,
        content: text
      })
      if (result.code === 200) {
        // 消息已发送给客服，客服的回复将通过WebSocket接收
        // 不需要添加助手消息
      } else {
        ElMessage.error(result.message || '发送失败')
      }
    } else {
      // 使用AI聊天端点
      const result = await request.post('/chat/ask', { question: text })
      // result.data 是 ChatResponse 对象
      // 添加助手消息
      if (result.data.answer && result.data.answer.trim() !== '') {
        messages.value.push({
          role: 'assistant',
          content: result.data.answer,
          time: new Date()
        })
        scrollToBottom()
      }
    }
    
    // 如果窗口最小化，显示未读数
    if (isMinimized.value) {
      unreadCount.value++
    }
  } catch (error) {
    ElMessage.error('发送失败，请重试')
  } finally {
    loading.value = false
    scrollToBottom()
  }
}

// 发送快捷问题
function sendQuickQuestion(question) {
  inputText.value = question
  sendMessage()
}

// 清空历史
async function clearHistory() {
  try {
    await request.post('/chat/clear')
    ElMessage.success('会话已清空')
  } catch (error) {
    ElMessage.error('清空会话失败')
  }
  messages.value = []
  unreadCount.value = 0
  inputText.value = ''
}

// 转接人工客服
async function requestHumanService() {
  if (transferring.value) return;
  transferring.value = true;
  try {
    const result = await request.post('/customer-service/request-human', {
      reason: '用户主动点击转接按钮'
    });
    ElMessage.success(result.data || '转人工请求已提交，请稍候');
    // 可以添加一条系统消息到聊天窗口
    messages.value.push({
      role: 'assistant',
      content: '已为您转接人工客服，请稍候，客服人员将很快为您服务。',
      time: new Date()
    });
    scrollToBottom();
  } catch (error) {
    ElMessage.error('转人工请求失败，请重试');
  } finally {
    transferring.value = false;
  }
}

// 滚动到底部
function scrollToBottom() {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}

// 格式化消息（支持换行）
function formatMessage(text) {
  if (!text) return ''
  return text
      .replace(/\n/g, '<br>')
      .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
}

// 格式化时间
function formatTime(date) {
  const d = new Date(date)
  return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}
</script>

<style lang="scss" scoped>
.chat-container {
  position: fixed;
  bottom: 20px;
  right: 20px;
  z-index: 1000;
}

.chat-toggle {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: linear-gradient(135deg, #409EFF, #66b1ff);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.4);
  transition: transform 0.3s, box-shadow 0.3s;

  &:hover {
    transform: scale(1.1);
    box-shadow: 0 6px 16px rgba(64, 158, 255, 0.5);
  }
}

.chat-window {
  width: 380px;
  height: 520px;
  background: white;
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.chat-header {
  padding: 16px;
  background: linear-gradient(135deg, #409EFF, #66b1ff);
  color: white;
  display: flex;
  justify-content: space-between;
  align-items: center;

  .header-info {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 16px;
    font-weight: 500;
    
    .connection-status {
      font-size: 12px;
      padding: 2px 6px;
      border-radius: 10px;
      margin-left: 8px;
      font-weight: normal;
      
      &.status-connected {
        background-color: rgba(255, 255, 255, 0.2);
      }
      
      &.status-connecting {
        background-color: rgba(255, 193, 7, 0.2);
        animation: pulse 1.5s infinite;
      }
      
      &.status-disconnected {
        background-color: rgba(220, 53, 69, 0.2);
      }
    }
  }

  .header-actions {
    display: flex;
    gap: 8px;

    .el-button {
      color: white;

      &:hover {
        background: rgba(255, 255, 255, 0.2);
      }
    }
  }
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  background: #f5f7fa;

  &::-webkit-scrollbar {
    width: 6px;
  }

  &::-webkit-scrollbar-thumb {
    background: #dcdfe6;
    border-radius: 3px;
  }
}

.welcome-tip {
  text-align: center;
  padding: 24px 16px;
  color: #606266;

  .tip-icon {
    margin-bottom: 12px;
  }

  p {
    margin: 8px 0;
    font-size: 14px;
  }

  .quick-questions {
    margin-top: 16px;
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    justify-content: center;
  }

  .quick-btn {
    padding: 6px 12px;
    background: white;
    border: 1px solid #dcdfe6;
    border-radius: 16px;
    font-size: 12px;
    cursor: pointer;
    transition: all 0.3s;

    &:hover {
      background: #409EFF;
      color: white;
      border-color: #409EFF;
    }
  }
}

.message-item {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;

  &.user {
    flex-direction: row-reverse;

    .message-content {
      align-items: flex-end;
    }

    .message-text {
      background: #409EFF;
      color: white;
    }
  }

  &.assistant {
    .message-text {
      background: white;
      color: #303133;
    }
  }

  .avatar {
    width: 36px;
    height: 36px;
    border-radius: 50%;
    background: #ecf5ff;
    color: #409EFF;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
  }

  .message-content {
    max-width: 75%;
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  .message-text {
    padding: 10px 14px;
    border-radius: 12px;
    font-size: 14px;
    line-height: 1.6;
    word-break: break-word;

    &.loading {
      display: flex;
      gap: 4px;
      padding: 12px 16px;

      .dot {
        width: 6px;
        height: 6px;
        background: #909399;
        border-radius: 50%;
        animation: bounce 1.4s infinite ease-in-out both;

        &:nth-child(1) { animation-delay: -0.32s; }
        &:nth-child(2) { animation-delay: -0.16s; }
      }
    }
  }

  .message-time {
    font-size: 11px;
    color: #909399;
  }

  .reference {
    margin-top: 8px;
    padding: 8px;
    background: rgba(64, 158, 255, 0.1);
    border-radius: 8px;
    font-size: 12px;

    .reference-title {
      color: #409EFF;
      margin-bottom: 4px;
    }

    .reference-item {
      color: #606266;
      padding: 2px 0;
    }
  }
}

@keyframes bounce {
  0%, 80%, 100% {
    transform: scale(0);
  }
  40% {
    transform: scale(1);
  }
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.chat-input {
  padding: 12px;
  background: white;
  border-top: 1px solid #ebeef5;

  .el-textarea {
    :deep(.el-textarea__inner) {
      border-radius: 8px;
      resize: none;
    }
  }

  .input-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-top: 8px;

    .hint {
      font-size: 12px;
      color: #909399;
    }

    .input-actions {
      display: flex;
      gap: 8px;
    }
  }
}
</style>