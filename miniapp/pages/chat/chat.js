const { aiChat, aiStatus } = require('../../utils/request')

const STORAGE_KEY = 'ai_chat_messages'
const MAX_PERSIST = 40
const MAX_HISTORY = 20
const ERROR_REPLY = '抱歉，AI助手暂时无法回复，请稍后再试。'

const STYLE = {
  p: 'margin:0 0 8px;line-height:1.75;',
  h1: 'margin:12px 0 6px;font-weight:700;color:#1e293b;font-size:17px;',
  h2: 'margin:12px 0 6px;font-weight:700;color:#1e293b;font-size:15px;',
  h3: 'margin:10px 0 5px;font-weight:700;color:#1e293b;font-size:14px;',
  h4: 'margin:10px 0 5px;font-weight:700;color:#1e293b;font-size:14px;',
  list: 'margin:0 0 8px;padding-left:18px;',
  li: 'margin:2px 0;line-height:1.75;',
  code: 'background:#f1f5f9;color:#6366f1;padding:1px 4px;border-radius:3px;font-size:12px;',
  pre: 'background:#1e293b;color:#e2e8f0;border-radius:6px;padding:9px;margin:5px 0;font-size:12px;line-height:1.6;white-space:pre-wrap;word-break:break-all;',
  quote: 'border-left:3px solid #6366f1;padding:3px 0 3px 9px;margin:5px 0;color:#64748b;',
  hr: 'height:1px;background:#e2e8f0;margin:8px 0;'
}

let seq = 0
function nextId() {
  seq += 1
  return Date.now() * 100 + (seq % 100)
}

function escapeHtml(text) {
  return String(text)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

function inlineFormat(text, codeBlocks) {
  let result = text
    .replace(/`([^`]+)`/g, `<code style="${STYLE.code}">$1</code>`)
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/\*([^*\n]+)\*/g, '<em>$1</em>')
  if (codeBlocks) {
    result = result.replace(/\u0000CODE(\d+)\u0000/g, (match, index) => codeBlocks[Number(index)] || '')
  }
  return result
}

function renderMarkdown(source) {
  if (!source) return ''
  const codeBlocks = []
  let text = String(source).replace(/```([\w+-]*)\n?([\s\S]*?)```/g, (match, lang, code) => {
    codeBlocks.push(`<pre style="${STYLE.pre}">${escapeHtml(code.replace(/\n$/, ''))}</pre>`)
    return `\u0000CODE${codeBlocks.length - 1}\u0000`
  })

  const lines = escapeHtml(text).split('\n')
  const output = []
  let listTag = ''

  const closeList = () => {
    if (listTag) {
      output.push(`</${listTag}>`)
      listTag = ''
    }
  }

  for (let i = 0; i < lines.length; i += 1) {
    const line = lines[i]

    if (!line.trim()) {
      closeList()
      continue
    }

    const codeMatch = line.match(/^\u0000CODE(\d+)\u0000$/)
    if (codeMatch) {
      closeList()
      output.push(codeBlocks[Number(codeMatch[1])] || '')
      continue
    }

    const heading = line.match(/^(#{1,6})\s+(.*)$/)
    if (heading) {
      closeList()
      const level = Math.min(heading[1].length, 4)
      output.push(`<h${level} style="${STYLE['h' + level]}">${inlineFormat(heading[2], codeBlocks)}</h${level}>`)
      continue
    }

    const quote = line.match(/^&gt;\s?(.*)$/)
    if (quote) {
      closeList()
      output.push(`<p style="${STYLE.quote}">${inlineFormat(quote[1], codeBlocks)}</p>`)
      continue
    }

    if (/^\s*(-{3,}|\*{3,})\s*$/.test(line)) {
      closeList()
      output.push(`<div style="${STYLE.hr}"></div>`)
      continue
    }

    const ordered = line.match(/^\s*\d+\.\s+(.*)$/)
    if (ordered) {
      if (listTag !== 'ol') {
        closeList()
        output.push(`<ol style="${STYLE.list}">`)
        listTag = 'ol'
      }
      output.push(`<li style="${STYLE.li}">${inlineFormat(ordered[1], codeBlocks)}</li>`)
      continue
    }

    const unordered = line.match(/^\s*[-*+]\s+(.*)$/)
    if (unordered) {
      if (listTag !== 'ul') {
        closeList()
        output.push(`<ul style="${STYLE.list}">`)
        listTag = 'ul'
      }
      output.push(`<li style="${STYLE.li}">${inlineFormat(unordered[1], codeBlocks)}</li>`)
      continue
    }

    closeList()
    output.push(`<p style="${STYLE.p}">${inlineFormat(line, codeBlocks)}</p>`)
  }

  closeList()
  return output.join('')
}

Page({
  data: {
    messages: [],
    inputText: '',
    loading: false,
    canStop: false,
    scrollTarget: '',
    configured: true,
    canRegenerate: false
  },

  onLoad() {
    this.setData({ messages: this.loadCache() })
    this.refreshRegenerateState()
    aiStatus().then(res => {
      this.setData({ configured: !!(res.data && res.data.configured) })
    }).catch(() => {})
  },

  onUnload() {
    this.stopped = true
    if (this.flushTimer) {
      clearTimeout(this.flushTimer)
      this.flushTimer = null
    }
    if (this.streamTask && typeof this.streamTask.abort === 'function') {
      try {
        this.streamTask.abort()
      } catch (e) {
        // 忽略中断异常
      }
    }
    this.streamTask = null
  },

  loadCache() {
    try {
      const raw = wx.getStorageSync(STORAGE_KEY)
      if (!Array.isArray(raw)) return []
      return raw
        .filter(item => item && item.role && typeof item.content === 'string')
        .map(item => ({
          id: item.id || nextId(),
          role: item.role,
          content: item.content,
          time: item.time || '',
          html: item.role === 'assistant' ? renderMarkdown(item.content) : ''
        }))
    } catch (e) {
      return []
    }
  },

  saveCache(messages) {
    try {
      const cache = (messages || []).slice(-MAX_PERSIST).map(item => ({
        id: item.id,
        role: item.role,
        content: item.content,
        time: item.time
      }))
      wx.setStorageSync(STORAGE_KEY, cache)
    } catch (e) {
      // 本地缓存失败不影响对话
    }
  },

  buildHistory(list) {
    const source = list || this.data.messages
    return source
      .filter(item => (item.role === 'user' || item.role === 'assistant') && item.content && item.content.trim())
      .map(item => ({ role: item.role, content: item.content }))
      .slice(-MAX_HISTORY)
  },

  onInput(e) {
    this.setData({ inputText: e.detail.value })
  },

  handleSend() {
    const text = this.data.inputText.trim()
    if (!text || this.data.loading) return

    const history = this.buildHistory()
    const userMsg = {
      id: nextId(),
      role: 'user',
      content: text,
      time: this.formatTime(),
      html: ''
    }

    const messages = [...this.data.messages, userMsg]
    this.setData({
      messages,
      inputText: '',
      scrollTarget: `msg-${userMsg.id}`,
      canRegenerate: false
    })
    this.saveCache(messages)

    this.startReply(text, history)
  },

  startReply(text, history) {
    const aiMsg = {
      id: nextId(),
      role: 'assistant',
      content: '',
      html: '',
      time: '',
      typing: true
    }

    this.currentId = aiMsg.id
    this.chunkBuffer = ''
    this.flushTimer = null
    this.stopped = false

    const messages = [...this.data.messages, aiMsg]
    this.setData({
      messages,
      loading: true,
      canStop: true,
      canRegenerate: false,
      scrollTarget: `msg-${aiMsg.id}`
    })

    const task = aiChatStream({
      message: text,
      history,
      onContent: piece => this.appendChunk(piece),
      onError: tip => this.handleStreamError(tip, text, history),
      onDone: () => {
        if (!this.stopped) this.finishReply()
      }
    })

    if (task) {
      this.streamTask = task
      return
    }
    this.requestFallback(text, history)
  },

  appendChunk(piece) {
    if (!piece || !this.currentId) return
    this.chunkBuffer = (this.chunkBuffer || '') + piece
    if (this.flushTimer) return
    this.flushTimer = setTimeout(() => {
      this.flushTimer = null
      this.flushChunks()
    }, 160)
  },

  flushChunks() {
    const add = this.chunkBuffer || ''
    if (!add || !this.currentId) return
    this.chunkBuffer = ''
    const id = this.currentId
    const messages = this.data.messages.map(item => {
      if (item.id !== id) return item
      const content = item.content + add
      return { ...item, content, html: renderMarkdown(content) }
    })
    this.setData({ messages })
  },

  replaceContent(content) {
    const id = this.currentId
    if (!id) return
    this.chunkBuffer = ''
    if (this.flushTimer) {
      clearTimeout(this.flushTimer)
      this.flushTimer = null
    }
    const messages = this.data.messages.map(item => item.id === id
      ? { ...item, content, html: renderMarkdown(content) }
      : item)
    this.setData({ messages })
  },

  requestFallback(text, history, tip) {
    this.streamTask = null
    aiChat(text, history).then(res => {
      if (this.stopped) return
      const reply = (res.data && res.data.reply) || tip || ERROR_REPLY
      this.replaceContent(reply)
      this.finishReply()
    }).catch(() => {
      if (this.stopped) return
      this.replaceContent(tip || ERROR_REPLY)
      this.finishReply()
    })
  },

  handleStreamError(tip, text, history) {
    if (this.stopped) return
    const current = this.data.messages.find(item => item.id === this.currentId)
    if (!current || !current.content.trim()) {
      this.requestFallback(text, history, tip)
      return
    }
    this.finishReply(tip)
  },

  finishReply(extraTip) {
    if (this.flushTimer) {
      clearTimeout(this.flushTimer)
      this.flushTimer = null
    }
    this.flushChunks()

    const id = this.currentId
    let messages = this.data.messages.map(item => {
      if (item.id !== id) return item
      let content = item.content
      if (extraTip) content = content ? `${content}\n\n${extraTip}` : extraTip
      if (!content.trim()) content = ERROR_REPLY
      return { ...item, content, html: renderMarkdown(content), time: this.formatTime(), typing: false }
    })

    this.streamTask = null
    this.currentId = null
    this.setData({
      messages,
      loading: false,
      canStop: false,
      canRegenerate: true,
      scrollTarget: `msg-${id}`
    })
    this.saveCache(messages)
  },

  handleStop() {
    if (!this.data.loading) return
    this.stopped = true
    if (this.flushTimer) {
      clearTimeout(this.flushTimer)
      this.flushTimer = null
    }
    if (this.streamTask && typeof this.streamTask.abort === 'function') {
      try {
        this.streamTask.abort()
      } catch (e) {
        // 忽略中断异常
      }
    }
    this.streamTask = null

    const id = this.currentId
    this.flushChunks()
    let messages = this.data.messages
    if (id) {
      messages = messages.map(item => {
        if (item.id !== id) return item
        const content = item.content && item.content.trim() ? item.content : '（已停止生成）'
        return { ...item, content, html: renderMarkdown(content), time: this.formatTime(), typing: false }
      })
    }
    this.currentId = null
    this.setData({ messages, loading: false, canStop: false, canRegenerate: !!id })
    this.saveCache(messages)
  },

  handleRegenerate() {
    if (this.data.loading) return
    const list = this.data.messages
    let lastUserIndex = -1
    for (let i = list.length - 1; i >= 0; i -= 1) {
      if (list[i].role === 'user') {
        lastUserIndex = i
        break
      }
    }
    if (lastUserIndex < 0) return

    const text = list[lastUserIndex].content
    const history = this.buildHistory(list.slice(0, lastUserIndex))
    const messages = list.slice(0, lastUserIndex + 1)

    this.setData({ messages, canRegenerate: false })
    this.saveCache(messages)

    this.startReply(text, history)
  },

  refreshRegenerateState() {
    const list = this.data.messages
    const last = list[list.length - 1]
    this.setData({ canRegenerate: !!last && last.role === 'assistant' && !!last.content })
  },

  handleClear() {
    if (this.data.loading) return
    wx.showModal({
      title: '清空对话',
      content: '确定清空当前对话记录吗？',
      confirmColor: '#6366f1',
      success: res => {
        if (!res.confirm) return
        this.stopped = true
        this.currentId = null
        this.streamTask = null
        this.chunkBuffer = ''
        this.setData({ messages: [], canRegenerate: false, canStop: false, scrollTarget: '' })
        this.saveCache([])
        wx.showToast({ title: '已清空', icon: 'none' })
      }
    })
  },

  copyReply(e) {
    const content = e.currentTarget.dataset.content
    if (!content) return
    wx.setClipboardData({ data: content })
  },

  useSuggestion(e) {
    const text = e.currentTarget.dataset.text
    this.setData({ inputText: text }, () => {
      this.handleSend()
    })
  },

  formatTime() {
    const now = new Date()
    return `${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}`
  }
})