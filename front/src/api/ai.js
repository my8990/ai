import request from '@/utils/request'

export function ping() {
  return request({
    url: '/ai/ping',
    method: 'get'
  })
}

export function getAiStatus() {
  return request({
    url: '/ai/status',
    method: 'get'
  })
}

export function chat(message, history) {
  return request({
    url: '/ai/chat',
    method: 'post',
    data: { message, history }
  })
}

export function chatStreamUrl() {
  const baseURL = import.meta.env.VITE_API_BASE_URL || '/api'
  return `${baseURL}/ai/chat/stream`
}