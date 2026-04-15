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
        </div>
        <div class="header-actions">
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
</template>

<script setup>
import { ref, reactive, nextTick, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { useUserStore } from '@/stores/user'

const isMinimized = ref(true)
const messages = ref([])
const inputText = ref('')
const loading = ref(false)
const unreadCount = ref(0)
const messagesRef = ref(null)

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

  // 创建占位符助手消息
  const assistantMessage = {
    role: 'assistant',
    content: '',
    time: new Date()
  }
  messages.value.push(assistantMessage)
  const messageIndex = messages.value.length - 1

  try {
    // 使用同步端点
    const result = await request.post('/chat/ask', { question: text })
    // result.data 是 ChatResponse 对象
    assistantMessage.content = result.data.answer
    // 触发响应式更新
    messages.value = [...messages.value]
    scrollToBottom()
    
    // 接收完成
    assistantMessage.time = new Date()
    // 如果窗口最小化，显示未读数
    if (isMinimized.value) {
      unreadCount.value++
    }
  } catch (error) {
    ElMessage.error('发送失败，请重试')
    // 移除占位符消息
    messages.value.splice(messageIndex, 1)
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
function clearHistory() {
  messages.value = []
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
  }
}
</style>
