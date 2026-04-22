import { ref, onUnmounted } from 'vue'

/**
 * WebSocket 组合式函数
 * 提供 WebSocket 连接管理功能
 */
export function useWebSocket() {
  const socket = ref(null)
  const isConnected = ref(false)
  const messageHandlers = []
  const reconnectAttempts = ref(0)
  const maxReconnectAttempts = 5
  const reconnectDelay = 3000

  // 建立连接
  const connect = (url, options = {}) => {
    if (socket.value && socket.value.readyState === WebSocket.OPEN) {
      console.log('WebSocket 已连接')
      return
    }

    try {
      // 如果URL已经是完整的WebSocket地址（以ws://或wss://开头），直接使用
      // 否则，添加当前主机前缀
      const wsUrl = url.startsWith('ws://') || url.startsWith('wss://') 
        ? url 
        : `ws://${window.location.host}${url}`
      socket.value = new WebSocket(wsUrl)

      socket.value.onopen = () => {
        console.log('WebSocket 连接成功')
        isConnected.value = true
        reconnectAttempts.value = 0
        if (options.onOpen) options.onOpen()
      }

      socket.value.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data)
          messageHandlers.forEach(handler => handler(data))
          if (options.onMessage) options.onMessage(data)
        } catch (error) {
          console.error('解析 WebSocket 消息失败', error)
        }
      }

      socket.value.onclose = (event) => {
        console.log('WebSocket 连接关闭', event.code, event.reason)
        isConnected.value = false
        if (options.onClose) options.onClose(event)

        // 尝试重连
        if (reconnectAttempts.value < maxReconnectAttempts) {
          reconnectAttempts.value++
          console.log(`尝试重连 (${reconnectAttempts.value}/${maxReconnectAttempts})...`)
          setTimeout(() => {
            connect(url, options)
          }, reconnectDelay)
        }
      }

      socket.value.onerror = (error) => {
        console.error('WebSocket 错误', error)
        isConnected.value = false
        if (options.onError) options.onError(error)
      }
    } catch (error) {
      console.error('创建 WebSocket 连接失败', error)
      if (options.onError) options.onError(error)
    }
  }

  // 断开连接
  const disconnect = () => {
    if (socket.value) {
      socket.value.close()
      socket.value = null
      isConnected.value = false
      messageHandlers.length = 0
    }
  }

  // 发送消息
  const send = (data) => {
    if (socket.value && socket.value.readyState === WebSocket.OPEN) {
      if (typeof data === 'object') {
        socket.value.send(JSON.stringify(data))
      } else {
        socket.value.send(data)
      }
      return true
    } else {
      console.warn('WebSocket 未连接，无法发送消息')
      return false
    }
  }

  // 注册消息处理器
  const onMessage = (handler) => {
    messageHandlers.push(handler)
  }

  // 移除消息处理器
  const offMessage = (handler) => {
    const index = messageHandlers.indexOf(handler)
    if (index > -1) {
      messageHandlers.splice(index, 1)
    }
  }

  // 组件卸载时自动断开连接
  onUnmounted(() => {
    disconnect()
  })

  return {
    socket,
    isConnected,
    connect,
    disconnect,
    send,
    onMessage,
    offMessage
  }
}
