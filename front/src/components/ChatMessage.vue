<template>
  <div :class="['chat-message', `chat-message--${message.role}`]">
    <div class="chat-message__avatar">
      <div v-if="message.role === 'user'" class="avatar-icon avatar-user">
        <el-icon :size="18"><User /></el-icon>
      </div>
      <div v-else class="avatar-icon avatar-ai">
        <span>AI</span>
      </div>
    </div>
    <div class="chat-message__content">
      <div v-if="message.role === 'assistant'" class="chat-message__bubble markdown-body" v-html="renderedContent"></div>
      <div v-else class="chat-message__bubble">{{ message.content }}</div>
      <div class="chat-message__meta">
        <span class="chat-message__time">{{ message.time }}</span>
        <span
          v-if="message.role === 'assistant' && message.content"
          class="copy-btn"
          title="复制回答"
          @click="handleCopy"
        >
          <el-icon :size="13"><CopyDocument /></el-icon>
        </span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { User, CopyDocument } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { marked } from 'marked'
import hljs from 'highlight.js'

const props = defineProps({
  message: {
    type: Object,
    required: true
  }
})

marked.setOptions({
  breaks: true,
  gfm: true,
  highlight(code, lang) {
    if (lang && hljs.getLanguage(lang)) {
      return hljs.highlight(code, { language: lang }).value
    }
    return hljs.highlightAuto(code).value
  }
})

const renderedContent = computed(() => {
  if (props.message.role !== 'assistant') return props.message.content
  if (!props.message.content) return '<span class="typing-cursor">▍</span>'
  try {
    return marked.parse(props.message.content)
  } catch {
    return props.message.content
  }
})

async function handleCopy() {
  const text = props.message.content
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text)
    } else {
      const textarea = document.createElement('textarea')
      textarea.value = text
      textarea.style.position = 'fixed'
      textarea.style.opacity = '0'
      document.body.appendChild(textarea)
      textarea.select()
      document.execCommand('copy')
      document.body.removeChild(textarea)
    }
    ElMessage.success('已复制到剪贴板')
  } catch {
    ElMessage.error('复制失败，请手动选择文本复制')
  }
}
</script>

<style scoped>
.chat-message {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}

.chat-message--assistant {
  flex-direction: row;
}

.chat-message--user {
  flex-direction: row-reverse;
}

.chat-message__avatar {
  flex-shrink: 0;
}

.avatar-icon {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.avatar-user {
  background: linear-gradient(135deg, #6366f1, #818cf8);
  color: #fff;
}

.avatar-ai {
  background: linear-gradient(135deg, #10b981, #34d399);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
}

.chat-message__content {
  max-width: 70%;
}

.chat-message--user .chat-message__content {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.chat-message__bubble {
  padding: 10px 16px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
}

.chat-message--user .chat-message__bubble {
  background: linear-gradient(135deg, #6366f1, #818cf8);
  color: #fff;
  border-bottom-right-radius: 4px;
  box-shadow: 0 8px 20px -12px rgba(99, 102, 241, 0.7);
}

.chat-message--assistant .chat-message__bubble {
  background: #fff;
  color: #1e293b;
  border: 1px solid #e2e8f0;
  border-bottom-left-radius: 4px;
  box-shadow: 0 6px 18px -14px rgba(15, 23, 42, 0.4);
}

.chat-message__meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 5px;
}

.chat-message--user .chat-message__meta {
  justify-content: flex-end;
}

.chat-message__time {
  font-size: 12px;
  color: #94a3b8;
}

.copy-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 6px;
  color: #94a3b8;
  cursor: pointer;
  transition: all 0.2s ease;
}

.copy-btn:hover {
  color: #6366f1;
  background: #eef2ff;
}

.typing-cursor {
  display: inline-block;
  color: #6366f1;
  animation: cursor-blink 1s steps(2, start) infinite;
}

@keyframes cursor-blink {
  to {
    visibility: hidden;
  }
}

.markdown-body :deep(p) {
  margin: 0 0 8px;
}

.markdown-body :deep(p:last-child) {
  margin-bottom: 0;
}

.markdown-body :deep(ul), .markdown-body :deep(ol) {
  padding-left: 20px;
  margin: 8px 0;
}

.markdown-body :deep(li) {
  margin: 4px 0;
}

.markdown-body :deep(pre) {
  background: #1e293b;
  border-radius: 8px;
  padding: 12px 16px;
  margin: 8px 0;
  overflow-x: auto;
}

.markdown-body :deep(pre code) {
  color: #e2e8f0;
  font-size: 13px;
  font-family: 'Fira Code', 'Consolas', monospace;
  background: none;
  padding: 0;
}

.markdown-body :deep(code) {
  background: #f1f5f9;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 13px;
  color: #6366f1;
}

.markdown-body :deep(blockquote) {
  border-left: 3px solid #6366f1;
  padding-left: 12px;
  margin: 8px 0;
  color: #64748b;
}

.markdown-body :deep(h1), .markdown-body :deep(h2), .markdown-body :deep(h3) {
  margin: 12px 0 8px;
  font-weight: 600;
}

.markdown-body :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 8px 0;
}

.markdown-body :deep(th), .markdown-body :deep(td) {
  border: 1px solid #e2e8f0;
  padding: 6px 12px;
  text-align: left;
}

.markdown-body :deep(th) {
  background: #f8fafc;
  font-weight: 600;
}

.typing-cursor {
  display: inline-block;
  animation: blink 1s infinite;
  color: #6366f1;
}

@keyframes blink {
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0; }
}
</style>