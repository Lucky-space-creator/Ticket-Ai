/**
 * WebSocket 基础地址（不含路径）。
 * 开发环境默认与页面同源，由 Vite 将 /ws 代理到 customer-service。
 * 若 WebSocket 统一走网关，可在 .env 中设置 VITE_WS_BASE=ws://localhost:8080
 */
export function getWsBaseUrl() {
  const explicit = import.meta.env.VITE_WS_BASE
  if (explicit && String(explicit).trim()) {
    return String(explicit).trim().replace(/\/$/, '')
  }
  if (typeof window === 'undefined') {
    return 'ws://localhost:8080'
  }
  const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${proto}//${window.location.host}`
}
