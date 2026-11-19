<template>
  <div class="checkin-detail-page">
    <div class="page-header-bar">
      <h2>签到详情</h2>
      <el-button @click="router.back()">
        <el-icon><ArrowLeft /></el-icon> 返回
      </el-button>
    </div>

    <div v-if="checkin.id" v-loading="loading" class="detail-content">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="签到标题">{{ checkin.title }}</el-descriptions-item>
        <el-descriptions-item label="课程">{{ checkin.courseName }}</el-descriptions-item>
        <el-descriptions-item label="类型">
          <el-tag :type="checkin.type === 1 ? 'primary' : 'success'" size="small" effect="plain" round>
            {{ typeLabel(checkin.type) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="签到码">
          <span class="checkin-code">{{ checkin.code }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="checkin.status === 1 ? 'success' : 'info'" size="small" effect="plain" round>
            {{ checkin.status === 1 ? '进行中' : '已结束' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="签到人数">
          {{ checkin.checkedCount }} / {{ checkin.totalCount }}
        </el-descriptions-item>
        <el-descriptions-item label="发布时间">{{ checkin.createTime }}</el-descriptions-item>
        <el-descriptions-item label="过期时间">{{ checkin.expireTime || '手动结束' }}</el-descriptions-item>
        <el-descriptions-item label="迟到判定">
          {{ checkin.lateMinutes > 0 ? `${checkin.lateMinutes} 分钟后记为迟到` : '不判定迟到' }}
        </el-descriptions-item>
        <el-descriptions-item label="迟到人数">{{ checkin.lateCount || 0 }}</el-descriptions-item>
      </el-descriptions>

      <div class="progress-bar">
        <el-progress
          :percentage="checkin.totalCount ? Math.round((checkin.checkedCount / checkin.totalCount) * 100) : 0"
          :stroke-width="10"
          :color="checkin.status === 1 ? '#10b981' : '#a3aed0'"
        />
      </div>

      <div class="action-bar" v-if="isTeacher && checkin.status === 1">
        <el-button type="warning" @click="handleEnd">结束签到</el-button>
        <el-button v-if="checkin.type === 2 || checkin.type === 3" type="success" @click="showQrDialog = true">显示二维码</el-button>
      </div>

      <div v-if="!isTeacher" class="my-status">
        <el-alert
          :type="myAlert.type"
          :title="myAlert.title"
          :description="myRecord && myRecord.checkinTime ? `签到时间：${myRecord.checkinTime}` : ''"
          show-icon
          :closable="false"
        />
        <el-button
          v-if="checkin.status === 1 && !(myRecord && myRecord.checked) && !(myRecord && (myRecord.status === 3 || myRecord.status === 4))"
          type="primary"
          class="go-checkin-btn"
          @click="router.push('/my-checkin')"
        >
          去签到
        </el-button>
      </div>

      <div v-if="isTeacher" class="record-section">
        <h3>学生签到情况 ({{ checkin.checkedCount }} / {{ checkin.totalCount }})</h3>
        <el-table :data="checkin.records || []" stripe>
          <el-table-column prop="nickname" label="姓名" />
          <el-table-column prop="username" label="用户名" />
          <el-table-column prop="studentNo" label="学号" />
          <el-table-column label="签到状态" width="150">
            <template #default="{ row }">
              <el-select
                :model-value="row.status || 0"
                size="small"
                placeholder="未签到"
                @change="(val) => handleStatusChange(row, val)"
              >
                <el-option label="出勤" :value="1" />
                <el-option label="迟到" :value="2" />
                <el-option label="请假" :value="3" />
                <el-option label="缺勤" :value="4" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="签到时间" width="180">
            <template #default="{ row }">
              <span v-if="row.checkinTime">{{ row.checkinTime }}</span>
              <span v-else class="no-time">—</span>
            </template>
          </el-table-column>
          <el-table-column label="签到地点" min-width="160">
            <template #default="{ row }">
              <span v-if="row.address">{{ row.address }}</span>
              <span v-else class="no-time">—</span>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <el-dialog v-model="showQrDialog" title="扫码签到" width="350px" align-center>
      <div class="qr-container">
        <canvas ref="qrCanvas" />
        <p class="qr-tip">请学生使用微信扫描此二维码签到</p>
        <p class="qr-tip">二维码每5分钟自动刷新，请保持此页面打开</p>
        <p class="qr-code">签到码: {{ checkin.code }}</p>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { getCheckinDetail, endCheckin, updateRecordStatus } from '../api/checkin'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import QRCode from 'qrcode'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const checkinId = route.params.id

const isTeacher = computed(() => userStore.userInfo?.role === 'teacher')

function typeLabel(type) {
  if (type === 1) return '数字签到'
  if (type === 3) return '扫码/数字'
  return '二维码签到'
}
const myRecord = computed(() => {
  const uid = userStore.userInfo?.id
  if (!uid) return null
  return (checkin.value.records || []).find(r => r.studentId === uid) || null
})

const myAlert = computed(() => {
  const mine = myRecord.value
  if (mine && mine.status === 3) {
    return { type: 'info', title: '你已请假' }
  }
  if (mine && mine.status === 4) {
    return { type: 'error', title: '本次签到你被记为缺勤' }
  }
  if (mine && mine.checked) {
    return { type: 'success', title: mine.status === 2 ? '你已完成签到（迟到）' : '你已完成签到' }
  }
  if (checkin.value.status === 1) {
    return { type: 'warning', title: '你尚未签到，请返回「我的签到」完成签到' }
  }
  return { type: 'info', title: '签到已结束，你未参与本次签到' }
})

const checkin = ref({})
const loading = ref(false)
const showQrDialog = ref(false)
const qrCanvas = ref(null)

async function loadDetail() {
  loading.value = true
  try {
    const res = await getCheckinDetail(checkinId)
    checkin.value = res.data
    if (isTeacher.value && (res.data.type === 2 || res.data.type === 3) && res.data.status === 1) {
      showQrDialog.value = true
      await nextTick()
      drawQr()
    }
  } catch (e) {
  } finally {
    loading.value = false
  }
}

async function handleStatusChange(row, status) {
  if (!status) return
  try {
    const res = await updateRecordStatus({
      checkinId: Number(checkinId),
      studentId: row.studentId,
      status
    })
    checkin.value = res.data
    ElMessage.success('签到结果已更新')
  } catch (e) {
  }
}

async function handleEnd() {
  try {
    await ElMessageBox.confirm('确认结束该签到？', '结束签到', { type: 'warning' })
    await endCheckin(checkinId)
    ElMessage.success('签到已结束')
    showQrDialog.value = false
    loadDetail()
  } catch (e) {}
}

function drawQr() {
  const canvas = qrCanvas.value
  if (!canvas || !checkin.value.id) return
  const token = checkin.value.qrToken ? `&token=${checkin.value.qrToken}` : ''
  const qrData = `checkin://${checkin.value.id}?code=${checkin.value.code}${token}`
  QRCode.toCanvas(canvas, qrData, { width: 240, margin: 2, color: { dark: '#000000', light: '#ffffff' } })
}

let qrRefreshTimer = null

function stopQrRefresh() {
  if (qrRefreshTimer) {
    clearInterval(qrRefreshTimer)
    qrRefreshTimer = null
  }
}

watch(showQrDialog, async (val) => {
  stopQrRefresh()
  if (!val) return
  await nextTick()
  drawQr()
  qrRefreshTimer = setInterval(() => {
    if (checkin.value.status !== 1) {
      showQrDialog.value = false
      return
    }
    loadDetail()
  }, 60000)
})

onMounted(() => {
  loadDetail()
})

onUnmounted(stopQrRefresh)
</script>

<style scoped>
.checkin-detail-page {
  max-width: 940px;
  margin: 0 auto;
  padding: 28px;
}

@media (max-width: 768px) {
  .checkin-detail-page {
    padding: 16px;
  }
}

.detail-content {
  margin-top: 4px;
}

.checkin-code {
  font-family: monospace;
  font-weight: 700;
  font-size: 18px;
  color: var(--color-primary);
  letter-spacing: 4px;
}

.action-bar {
  margin: 20px 0;
  display: flex;
  gap: 8px;
}

.progress-bar {
  margin-top: 20px;
}

.my-status {
  margin-top: 20px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}

.go-checkin-btn {
  margin-top: 12px;
}

.record-section {
  margin-top: 24px;
}

.record-section h3 {
  margin-bottom: 12px;
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.qr-container {
  text-align: center;
  padding: 16px 0;
}

.qr-container canvas {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
}

.qr-tip {
  margin-top: 12px;
  font-size: 13px;
  color: var(--color-text-secondary);
}

.qr-code {
  font-family: monospace;
  font-weight: 700;
  font-size: 18px;
  color: var(--color-primary);
  letter-spacing: 4px;
  margin-top: 8px;
}

.no-time {
  color: var(--color-text-placeholder);
}
</style>