import request from '@/utils/request'

/**
 * 客服系统 API
 */

// 获取待接入会话列表
export function getPendingSessions() {
  return request({
    url: '/api/customer-service/pending-sessions',
    method: 'get'
  })
}

// 获取服务中的会话列表
export function getServingSessions() {
  return request({
    url: '/api/customer-service/serving-sessions',
    method: 'get'
  })
}

// 接入会话
export function acceptSession(data) {
  return request({
    url: '/api/customer-service/accept-session',
    method: 'post',
    data
  })
}

// 结束会话
export function endSession(data) {
  return request({
    url: '/api/customer-service/end-session',
    method: 'post',
    data
  })
}

// 获取会话历史消息
export function getHistory(params) {
  return request({
    url: '/api/customer-service/history',
    method: 'get',
    params
  })
}

// 发送客服消息
export function sendMessage(data) {
  return request({
    url: '/api/customer-service/send-message',
    method: 'post',
    data
  })
}

// 用户请求转人工客服
export function requestHumanService(data) {
  return request({
    url: '/api/customer-service/request-human',
    method: 'post',
    data
  })
}

// 获取已结束的会话列表
export function getEndedSessions() {
  return request({
    url: '/api/customer-service/ended-sessions',
    method: 'get'
  })
}
