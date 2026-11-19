<template>
  <div class="appeal-page">
    <div class="page-header-bar">
      <h2>账号申诉</h2>
    </div>

    <el-alert
      v-if="userInfo?.status === 0"
      title="您的账号已被禁用"
      :description="'禁用原因：' + (userInfo?.banReason || '未说明')"
      type="error"
      show-icon
      :closable="false"
      style="margin-bottom: 20px;"
    />

    <div v-if="!hasPending" class="surface-card appeal-form">
      <div class="section-bar">
        <h3>提交申诉</h3>
      </div>
      <el-form :model="form" label-width="80px">
        <el-form-item label="申诉理由">
          <el-input v-model="form.reason" type="textarea" :rows="4" placeholder="请详细说明申诉理由" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSubmit" :loading="submitting">提交申诉</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-alert
      v-else
      title="您已有待处理的申诉，请耐心等待管理员审核"
      type="warning"
      show-icon
      :closable="false"
      style="margin-bottom: 20px;"
    />

    <div class="section-bar appeal-list-bar">
      <h3>申诉记录</h3>
    </div>

    <div class="surface-card appeal-table">
      <el-table :data="appealList" stripe>
        <el-table-column prop="reason" label="申诉理由" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small" effect="plain" round>{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reply" label="管理员回复" />
        <el-table-column label="提交时间" width="170">
          <template #default="{ row }">
            {{ formatTime(row.createTime) }}
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="appealList.length === 0" description="暂无申诉记录" />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { submitAppeal, getMyAppeals } from '../api/admin'
import { ElMessage } from 'element-plus'

const userInfo = ref(JSON.parse(localStorage.getItem('userInfo') || '{}'))
const form = ref({ reason: '' })
const submitting = ref(false)
const appealList = ref([])

const hasPending = computed(() => appealList.value.some(a => a.status === 0))

function statusLabel(status) {
  return { 0: '待处理', 1: '已通过', 2: '已驳回' }[status] || '未知'
}
function statusTagType(status) {
  return { 0: 'warning', 1: 'success', 2: 'danger' }[status] || ''
}
function formatTime(t) {
  if (!t) return ''
  return t.replace('T', ' ').substring(0, 19)
}

async function loadAppeals() {
  try {
    const res = await getMyAppeals()
    appealList.value = res.data
  } catch (e) {}
}

async function handleSubmit() {
  if (!form.value.reason.trim()) {
    ElMessage.warning('请输入申诉理由')
    return
  }
  submitting.value = true
  try {
    await submitAppeal(form.value.reason)
    ElMessage.success('申诉已提交，请等待管理员审核')
    form.value.reason = ''
    loadAppeals()
  } catch (e) {}
  submitting.value = false
}

onMounted(loadAppeals)
</script>

<style scoped>
.appeal-page {
  max-width: 760px;
  margin: 0 auto;
  padding: 28px;
}

.appeal-form {
  margin-bottom: 20px;
}

.appeal-list-bar {
  margin-top: 4px;
}

.appeal-table {
  padding: 8px 22px 22px;
}

@media (max-width: 768px) {
  .appeal-page {
    padding: 16px;
  }
}
</style>