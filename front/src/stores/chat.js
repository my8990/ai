import { defineStore } from 'pinia'
import { ref } from 'vue'
import { chat, chatStreamUrl, getAiStatus } from '@/api/ai'

const STORAGE_KEY = 'ai_chat_messages'
const MAX_PERSIST = 60
const MAX_HISTORY = 20
const DEFAULT_ERROR = '抱歉，发生了错误，请稍后重试。'

let seq = 0
function nextId() {
  seq += 1
  return Date.now() * 100 + (seq % 100)
}

function currentTime() {
  return new Date().toLocaleTimeString('zh-CN', { hour12: false })
}

function loadMessages() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    const parsed = raw ? JSON.parse(raw) : []
    return Array.isArray(parsed) ? parsed.filter(m => m && m.role && typeof m.content === 'string') : []
  } catch {
    return []
  }
}

function extractPayload(frame) {
  const dataLines = []
  for (const line of frame.split('\n')) {
    const trimmed = line.trim()
    if (trimmed.startsWith('data:')) {
      dataLines.push(trimmed.slice(5).trim())
    }
  }
  return dataLines.join('\n')
}

export const useChatStore = defineStore('chat', () => {
  const messages = ref(loadMessages())
  const loading = ref(false)
  const configured = ref(true)
  const model = ref('')
  let controller = null

  function persist() {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(messages.value.slice(-MAX_PERSIST)))
    } catch {
      // 存储失败时忽略，不影响对话
    }
  }

  function addUserMessage(content) {
    messages.value.push({
      id: nextId(),
      role: 'user',
      content,
      time: currentTime()
    })
  }

  function addAssistantMessage(content) {
    const id = nextId()
    messages.value.push({
      id,
      role: 'assistant',
      content,
      time: currentTime()
    })
    return id
  }

  function updateAssistantMessage(id, content) {
    const msg = messages.value.find(m => m.id === id)
    if (msg) {
      msg.content = content
    }
  }

  function removeMessage(id) {
    messages.value = messages.value.filter(m => m.id !== id)
  }

  function buildHistory(list) {
    const turns = (list || [])
      .filter(m => (m.role === 'user' || m.role === 'assistant') && m.content && m.content.trim())
      .map(m => ({ role: m.role, content: m.content }))
    return turns.slice(-MAX_HISTORY)
  }

  async function sendStreamMessage(content, history) {
    const assistantId = addAssistantMessage('')
    controller = new AbortController()
    const token = localStorage.getItem('token')
    let fullText = ''

    try {
      const response = await fetch(chatStreamUrl(), {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': token ? `Bearer ${token}` : ''
        },
        body: JSON.stringify({ message: content, history }),
        signal: controller.signal
      })

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`)
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      let finished = false

      while (!finished) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const frames = buffer.split('\n\n')
        buffer = frames.pop() || ''

        for (const frame of frames) {
          const payload = extractPayload(frame)
          if (!payload) continue

          let chunk
          try {
            chunk = JSON.parse(payload)
          } catch {
            continue
          }

          if (chunk.type === 'error') {
            updateAssistantMessage(assistantId, chunk.content || 'AI 服务暂时不可用，请稍后再试。')
            reader.cancel().catch(() => {})
            return { ok: true, id: assistantId }
          }

          if (chunk.type === 'done') {
            finished = true
            break
          }

          if (chunk.type === 'content' && chunk.content) {
            fullText += chunk.content
            updateAssistantMessage(assistantId, fullText)
          }
        }
      }

      if (!fullText) {
        updateAssistantMessage(assistantId, 'AI 暂时无法回复，请稍后再试。')
      }
      return { ok: true, id: assistantId }
    } catch (error) {
      if (error.name === 'AbortError') {
        if (fullText) {
          updateAssistantMessage(assistantId, `${fullText}\n\n_(已停止生成)_`)
        } else {
          removeMessage(assistantId)
        }
        return { ok: true, id: assistantId, aborted: true }
      }
      removeMessage(assistantId)
      throw error
    } finally {
      controller = null
    }
  }

  async function askAssistant(text, history) {
    try {
      const result = await sendStreamMessage(text, history)
      if (result && !result.ok) {
        throw new Error('stream failed')
      }
    } catch {
      try {
        const res = await chat(text, history)
        addAssistantMessage(res.data?.reply || 'AI 暂时无法回复，请稍后再试。')
      } catch {
        addAssistantMessage(DEFAULT_ERROR)
      }
    }
  }

  async function sendMessage(content) {
    const text = (content || '').trim()
    if (!text || loading.value) return

    const history = buildHistory(messages.value)
    addUserMessage(text)
    persist()

    loading.value = true
    try {
      await askAssistant(text, history)
    } finally {
      loading.value = false
      persist()
    }
  }

  async function regenerate() {
    if (loading.value) return
    const list = messages.value
    let lastUserIndex = -1
    for (let i = list.length - 1; i >= 0; i -= 1) {
      if (list[i].role === 'user') {
        lastUserIndex = i
        break
      }
    }
    if (lastUserIndex < 0) return

    const text = list[lastUserIndex].content
    const history = buildHistory(list.slice(0, lastUserIndex))
    messages.value = list.slice(0, lastUserIndex + 1)

    loading.value = true
    try {
      await askAssistant(text, history)
    } finally {
      loading.value = false
      persist()
    }
  }

  function stop() {
    if (controller) {
      controller.abort()
    }
  }

  function clearMessages() {
    if (controller) {
      controller.abort()
    }
    messages.value = []
    persist()
  }

  async function fetchStatus() {
    try {
      const res = await getAiStatus()
      configured.value = !!res.data?.configured
      model.value = res.data?.model || ''
      return res.data
    } catch {
      return null
    }
  }

  return {
    messages,
    loading,
    configured,
    model,
    sendMessage,
    regenerate,
    stop,
    clearMessages,
    fetchStatus
  }
})