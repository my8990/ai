<template>
  <div class="chat-container">
    <div v-if="!chatStore.configured" class="config-tip">
      <el-icon><WarningFilled /></el-icon>
      <span>AI 助手尚未配置 API Key，请在 application.yml 中设置 ai.api-key 后使用。</span>
    </div>

    <div class="chat-messages" ref="messagesRef">
      <div v-if="chatStore.messages.length === 0" class="chat-empty">
        <div class="empty-icon-wrap">
          <span class="empty-icon">🤖</span>
        </div>
        <h3>AI 智慧助手</h3>
        <p>有什么问题，随时问我吧！</p>
        <div class="suggestion-chips">
          <span class="chip" @click="useSuggestion('帮我解释一个概念')">💡 解释概念</span>
          <span class="chip" @click="useSuggestion('帮我梳理知识点')">📝 梳理知识点</span>
          <span class="chip" @click="useSuggestion('推荐一些学习方法')">📚 学习方法</span>
          <span class="chip" @click="useSuggestion('帮我制定复习计划')">🗓️ 复习计划</span>
          <span class="chip" @click="useSuggestion('这道题怎么做')">✏️ 解题辅导</span>
        </div>
      </div>
      <ChatMessage
        v-for="msg in chatStore.messages"
        :key="msg.id"
        :message="msg"
      />
      <div v-if="chatStore.loading && isLastMsgEmpty" class="chat-loading">
        <div class="loading-dots">
          <span></span><span></span><span></span>
        </div>
        <span>AI 正在思考</span>
      </div>
      <div v-if="showRegenerate" class="chat-regenerate">
        <el-button text type="primary" :icon="RefreshRight" @click="handleRegenerate">
          重新生成
        </el-button>
      </div>
    </div>

    <div class="chat-input">
      <el-input
        v-model="inputText"
        type="textarea"
        :rows="2"
        placeholder="输入消息，按 Enter 发送，Shift+Enter 换行"
        resize="none"
        @keydown.enter.exact.prevent="handleSend"
        :disabled="chatStore.loading"
      />
      <div class="input-actions">
        <el-button
          type="danger"
          text
          @click="handleClear"
          :disabled="chatStore.messages.length === 0"
          title="清空对话"
        >
          <el-icon><Delete /></el-icon>
        </el-button>
        <el-button
          v-if="chatStore.loading"
          type="warning"
          text
          :icon="VideoPause"
          @click="chatStore.stop()"
          title="停止生成"
        >
          停止
        </el-button>
        <el-button
          type="primary"
          :icon="Promotion"
          @click="handleSend"
          :loading="chatStore.loading"
          :disabled="!inputText.trim() || chatStore.loading"
          class="send-btn"
        >
          发送
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, watch, onMounted, computed } from 'vue'
import { Promotion, Delete, RefreshRight, VideoPause, WarningFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useChatStore } from '@/stores/chat'
import { useUserStore } from '@/stores/user'
import ChatMessage from '@/components/ChatMessage.vue'

const chatStore = useChatStore()
const userStore = useUserStore()
const inputText = ref('')
const messagesRef = ref(null)

const isLastMsgEmpty = computed(() => {
  const msgs = chatStore.messages
  return msgs.length === 0 || msgs[msgs.length - 1]?.role === 'user'
})

const showRegenerate = computed(() => {
  if (chatStore.loading) return false
  const msgs = chatStore.messages
  if (msgs.length === 0) return false
  return msgs[msgs.length - 1]?.role === 'assistant' && !!msgs[msgs.length - 1]?.content
})

onMounted(() => {
  chatStore.fetchStatus()
  if (!userStore.userInfo) {
    userStore.fetchUserInfo().catch(() => {})
  }
})

function scrollToBottom() {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}

function handleSend() {
  if (!inputText.value.trim() || chatStore.loading) return
  chatStore.sendMessage(inputText.value)
  inputText.value = ''
}

function handleRegenerate() {
  chatStore.regenerate()
}

async function handleClear() {
  try {
    await ElMessageBox.confirm('确定清空当前对话记录吗？', '清空对话', {
      confirmButtonText: '清空',
      cancelButtonText: '取消',
      type: 'warning'
    })
    chatStore.clearMessages()
    ElMessage.success('对话已清空')
  } catch {
    // 用户取消
  }
}

function useSuggestion(text) {
  inputText.value = text
  handleSend()
}

watch(() => chatStore.messages.length, scrollToBottom)
watch(() => chatStore.loading, scrollToBottom)
watch(() => {
  const msgs = chatStore.messages
  if (msgs.length > 0) return msgs[msgs.length - 1].content
  return ''
}, scrollToBottom)
</script>

<style scoped>
.chat-container {
  display: flex;
  flex-direction: column;
  height: calc(100vh - var(--header-height, 60px));
  max-width: 900px;
  margin: 0 auto;
  background: var(--color-bg-page, #f1f5f9);
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
}

@media (max-width: 768px) {
  .chat-messages {
    padding: 16px;
  }
}

.chat-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--color-text-secondary, #64748b);
}

.empty-icon-wrap {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: linear-gradient(135deg, #eef2ff, #e0e7ff);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 20px;
}

.empty-icon {
  font-size: 40px;
}

.chat-empty h3 {
  font-size: 22px;
  color: var(--color-text-primary, #1e293b);
  margin-bottom: 8px;
  font-weight: 700;
}

.chat-empty p {
  font-size: 14px;
  margin-bottom: 28px;
}

.suggestion-chips {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: center;
  max-width: 400px;
}

.chip {
  padding: 8px 16px;
  background: var(--color-bg-card, #fff);
  border: 1px solid var(--color-border, #e2e8f0);
  border-radius: 20px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s ease;
  color: var(--color-text-regular, #475569);
}

.chip:hover {
  border-color: var(--color-primary, #6366f1);
  color: var(--color-primary, #6366f1);
  background: var(--color-primary-bg, #eef2ff);
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(99, 102, 241, 0.15);
}

.chat-loading {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  color: var(--color-text-secondary, #64748b);
  font-size: 14px;
}

.loading-dots {
  display: flex;
  gap: 4px;
}

.loading-dots span {
  width: 6px;
  height: 6px;
  background: var(--color-primary, #6366f1);
  border-radius: 50%;
  animation: dot-bounce 1.4s infinite ease-in-out both;
}

.loading-dots span:nth-child(1) { animation-delay: -0.32s; }
.loading-dots span:nth-child(2) { animation-delay: -0.16s; }

@keyframes dot-bounce {
  0%, 80%, 100% { transform: scale(0); }
  40% { transform: scale(1); }
}

.config-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 16px 24px 0;
  padding: 10px 16px;
  background: #fffbeb;
  border: 1px solid #fde68a;
  border-radius: 10px;
  color: #b45309;
  font-size: 13px;
}

.chat-regenerate {
  display: flex;
  justify-content: center;
  padding: 4px 0 12px;
}

.chat-input {
  display: flex;
  gap: 12px;
  padding: 16px 24px;
  background: var(--color-bg-card, #fff);
  border-top: 1px solid var(--color-border, #e2e8f0);
  align-items: flex-end;
}

.chat-input .el-textarea {
  flex: 1;
}

.input-actions {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 4px;
}

.send-btn {
  height: 40px;
}
</style>