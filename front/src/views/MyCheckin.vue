<template>
  <div class="my-checkin-page">
    <div class="page-header-bar">
      <h2>{{ courseId ? '本课程签到' : '我的签到' }}</h2>
      <el-button @click="loadCheckins">
        <el-icon><Refresh /></el-icon> 刷新
      </el-button>
    </div>

    <el-tabs v-model="activeTab" class="status-tabs">
      <el-tab-pane label="全部" name="all" />
      <el-tab-pane label="待签到" name="pending" />
      <el-tab-pane label="已签到" name="checked" />
    </el-tabs>

    <div v-loading="loading">
      <el-empty v-if="filteredList.length === 0" description="暂无签到活动" />

      <div v-else class="checkin-list">
        <div v-for="item in filteredList" :key="item.id" class="checkin-card">
          <div class="card-top">
            <div class="card-title">
              <h3>{{ item.title }}</h3>
              <el-tag :type="typeTag(item.type)" size="small" effect="plain" round>
                {{ typeLabel(item.type) }}
              </el-tag>
            </div>
            <el-tag :type="stateTag(item)" size="small" effect="dark" round>
              {{ stateLabel(item) }}
            </el-tag>
          </div>
          <p v-if="!courseId" class="card-course">{{ item.courseName }}</p>
          <p class="card-time">发布时间: {{ item.createTime }}</p>

          <div v-if="canInputCode(item)" class="card-action">
            <el-input
              v-model="item.inputCode"
              placeholder="请输入6位签到码"
              maxlength="6"
              class="code-input"
              @keyup.enter="handleSubmit(item)"
            />
            <el-button type="primary" size="small" :loading="item.submitting" @click="handleSubmit(item)">签到</el-button>
            <span class="action-tip">请向老师索取 6 位数字签到码</span>
          </div>
          <div v-else-if="isPending(item)" class="card-action">
            <span class="action-tip">本次为二维码签到，请扫描老师出示的二维码完成签到</span>
          </div>
          <div v-else-if="item.checked" class="checked-tip">
            <el-icon color="#10b981"><CircleCheck /></el-icon>
            <span>{{ item.myStatus === 2 ? '你已签到（迟到）' : '你已完成本次签到' }}</span>
          </div>
          <div v-else-if="item.myStatus === 3" class="absent-tip">
            <span>本次签到已被老师登记为请假，无需签到</span>
          </div>
          <div v-else-if="item.myStatus === 4" class="absent-tip">
            <span>本次签到已被老师登记为缺勤，如需更正请联系老师</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getMyCheckins, submitCheckinByCode } from '../api/checkin'
import { ElMessage } from 'element-plus'
import { Refresh, CircleCheck } from '@element-plus/icons-vue'

const route = useRoute()
const checkins = ref([])
const loading = ref(false)
const activeTab = ref('all')

const courseId = computed(() => (route.params.id ? String(route.params.id) : ''))

const scopedList = computed(() => {
  if (!courseId.value) return checkins.value
  return checkins.value.filter(i => String(i.courseId) === courseId.value)
})

const isAbsentLike = item => item.myStatus === 3 || item.myStatus === 4

const isPending = item => item.status === 1 && !item.checked && !isAbsentLike(item)

const canInputCode = item => isPending(item) && (item.type === 1 || item.type === 3)

function typeLabel(type) {
  if (type === 1) return '数字签到'
  if (type === 3) return '扫码/数字'
  return '二维码签到'
}

function typeTag(type) {
  return type === 2 ? 'success' : 'primary'
}

function stateLabel(item) {
  if (item.myStatus === 3) return '已请假'
  if (item.myStatus === 4) return '缺勤'
  if (item.checked) return item.myStatus === 2 ? '已签到·迟到' : '已签到'
  return item.status === 1 ? '进行中' : '已结束'
}

function stateTag(item) {
  if (item.myStatus === 3) return 'info'
  if (item.myStatus === 4) return 'danger'
  if (item.checked) return 'success'
  return item.status === 1 ? 'warning' : 'info'
}

const filteredList = computed(() => {
  if (activeTab.value === 'pending') return scopedList.value.filter(isPending)
  if (activeTab.value === 'checked') return scopedList.value.filter(i => i.checked)
  return scopedList.value
})

async function loadCheckins() {
  loading.value = true
  try {
    const res = await getMyCheckins()
    checkins.value = (res.data || []).map(item => ({ ...item, inputCode: '', submitting: false }))
  } catch (e) {
  } finally {
    loading.value = false
  }
}

async function handleSubmit(item) {
  if (!item.inputCode) {
    ElMessage.warning('请输入签到码')
    return
  }
  item.submitting = true
  try {
    await submitCheckinByCode({ code: item.inputCode.trim(), source: 'number' })
    ElMessage.success('签到成功')
    loadCheckins()
  } catch (e) {
  } finally {
    item.submitting = false
  }
}

onMounted(() => {
  loadCheckins()
})
</script>

<style scoped>
.my-checkin-page {
  max-width: 760px;
  margin: 0 auto;
  padding: 28px;
}

.status-tabs {
  margin-top: 8px;
}

.checkin-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.checkin-card {
  padding: 20px 24px;
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  transition: all var(--transition-normal);
}

.checkin-card:hover {
  box-shadow: var(--shadow-md);
}

.card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.card-title h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.card-course {
  color: var(--color-text-secondary);
  font-size: 13px;
  margin: 8px 0 4px;
}

.card-time {
  color: var(--color-text-placeholder);
  font-size: 12px;
  margin: 0 0 8px;
}

.card-action {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  flex-wrap: wrap;
}

.code-input {
  width: 200px;
}

.code-input :deep(input) {
  letter-spacing: 4px;
}

.action-tip {
  font-size: 12px;
  color: var(--color-text-placeholder);
}

.checked-tip {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  font-size: 13px;
  color: #10b981;
}

.absent-tip {
  margin-top: 8px;
  font-size: 13px;
  color: var(--color-text-secondary);
}

@media (max-width: 768px) {
  .my-checkin-page {
    padding: 16px;
  }

  .checkin-card {
    padding: 14px 16px;
  }

  .code-input {
    width: 100%;
  }
}
</style>