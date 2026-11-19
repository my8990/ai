<template>
  <div class="notice-page">
    <div class="page-header-bar">
      <h2>课程公告</h2>
      <div class="bar-actions">
        <el-button v-if="isTeacher" type="primary" :icon="Plus" @click="openDialog">发布公告</el-button>
      </div>
    </div>

    <el-empty v-if="notices.length === 0 && !loading" description="暂无公告" />

    <div v-else v-loading="loading" class="notice-list">
      <div v-for="item in notices" :key="item.id" class="notice-card" :class="noticeClass(item)">
        <div class="nc-left">
          <el-icon :size="22"><component :is="item.type === 1 ? Cpu : item.type === 3 ? ChatLineSquare : item.type === 4 ? Tickets : Bell" /></el-icon>
        </div>
        <div class="nc-body">
          <div class="nc-head">
            <el-tag
              :type="noticeTagType(item)"
              effect="light"
              round
              size="small"
            >{{ noticeLabel(item) }}</el-tag>
            <span class="nc-time">{{ formatTime(item.createTime) }}</span>
          </div>
          <h4 class="nc-title">{{ item.title }}</h4>
          <p v-if="item.content" class="nc-content">{{ item.content }}</p>
        </div>
        <div v-if="isTeacher && item.type === 2" class="nc-actions">
          <el-button type="danger" size="small" link @click="handleDelete(item)">删除</el-button>
        </div>
      </div>
    </div>

    <el-dialog v-model="showDialog" title="发布公告" width="520px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="公告标题" required>
          <el-input v-model="form.title" maxlength="100" show-word-limit placeholder="请输入公告标题" />
        </el-form-item>
        <el-form-item label="公告内容">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="5"
            maxlength="500"
            show-word-limit
            placeholder="请输入公告内容（可选）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">发布</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '../stores/user'
import { getCourseNotices, createNotice, deleteNotice } from '../api/notice'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Bell, Cpu, ChatLineSquare, Tickets } from '@element-plus/icons-vue'

const route = useRoute()
const userStore = useUserStore()
const courseId = route.params.id
const isTeacher = computed(() => userStore.userInfo?.role === 'teacher')

const notices = ref([])
const loading = ref(false)
const showDialog = ref(false)
const submitting = ref(false)
const form = ref({ title: '', content: '' })

function formatTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 16)
}

function noticeLabel(item) {
  if (item.type === 1) return '课堂课件发布'
  if (item.type === 3) return '课堂讨论'
  if (item.type === 4) return '课堂测验'
  return '教师公告'
}

function noticeTagType(item) {
  if (item.type === 1) return 'info'
  if (item.type === 3) return 'warning'
  if (item.type === 4) return 'success'
  return 'primary'
}

function noticeClass(item) {
  if (item.type === 1) return 'auto'
  if (item.type === 3) return 'discussion'
  if (item.type === 4) return 'quiz'
  return 'teacher'
}

async function loadNotices() {
  loading.value = true
  try {
    const res = await getCourseNotices(courseId)
    notices.value = res.data || []
  } catch (e) {
    notices.value = []
  } finally {
    loading.value = false
  }
}

function openDialog() {
  form.value = { title: '', content: '' }
  showDialog.value = true
}

async function handleSubmit() {
  if (!form.value.title?.trim()) {
    ElMessage.warning('请输入公告标题')
    return
  }
  submitting.value = true
  try {
    await createNotice({
      courseId: Number(courseId),
      title: form.value.title.trim(),
      content: form.value.content?.trim() || ''
    })
    ElMessage.success('公告已发布')
    showDialog.value = false
    loadNotices()
  } catch (e) {
  } finally {
    submitting.value = false
  }
}

async function handleDelete(item) {
  try {
    await ElMessageBox.confirm(`确认删除公告「${item.title}」？`, '删除公告', { type: 'warning' })
    await deleteNotice(item.id)
    ElMessage.success('已删除')
    loadNotices()
  } catch (e) {}
}

onMounted(() => loadNotices())
</script>

<style scoped>
.notice-page {
  padding: 0;
}

.bar-actions {
  display: flex;
  gap: 8px;
}

.notice-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.notice-card {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 18px 22px;
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-left: 4px solid var(--el-color-primary);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  transition: transform var(--transition-normal), box-shadow var(--transition-normal);
}

.notice-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-lg);
}

.notice-card.auto {
  border-left-color: var(--el-color-info);
}

.nc-left {
  width: 44px;
  height: 44px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
  background: linear-gradient(135deg, #6366f1, #818cf8);
}

.notice-card.auto .nc-left {
  background: linear-gradient(135deg, #0ea5e9, #38bdf8);
}

.notice-card.discussion {
  border-left-color: #f59e0b;
}

.notice-card.discussion .nc-left {
  background: linear-gradient(135deg, #f59e0b, #fbbf24);
}

.notice-card.quiz {
  border-left-color: #8b5cf6;
}

.notice-card.quiz .nc-left {
  background: linear-gradient(135deg, #7c3aed, #a78bfa);
}

.nc-body {
  flex: 1;
  min-width: 0;
}

.nc-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}

.nc-time {
  font-size: 12px;
  color: var(--color-text-placeholder);
  margin-left: auto;
}

.nc-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-primary);
  line-height: 1.45;
  word-break: break-all;
}

.nc-content {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--color-text-secondary);
  line-height: 1.6;
  word-break: break-all;
  white-space: pre-wrap;
}

.nc-actions {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

@media (max-width: 768px) {
  .notice-card {
    padding: 14px;
    gap: 12px;
  }

  .nc-left {
    width: 38px;
    height: 38px;
  }
}
</style>