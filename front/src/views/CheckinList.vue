<template>
  <div class="checkin-list-page">
    <div class="page-header-bar">
      <h2>签到管理</h2>
      <el-button v-if="isTeacher" type="primary" @click="showCreateDialog = true">
        <el-icon><Plus /></el-icon> 发布签到
      </el-button>
    </div>

    <el-empty
      v-if="!isTeacher"
      description="仅教师可管理签到，请前往「我的签到」参与课堂签到"
    />

    <div v-else v-loading="loading">
      <el-empty v-if="checkins.length === 0" description="暂无签到记录，点击右上角发布签到" />

      <el-table v-else :data="checkins" stripe>
        <el-table-column prop="title" label="签到标题" min-width="160" />
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <el-tag :type="row.type === 1 ? 'primary' : 'success'" size="small" effect="plain" round>
              {{ typeLabel(row.type) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="签到码" width="100">
          <template #default="{ row }">
            <span class="checkin-code">{{ row.code }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small" effect="plain" round>
              {{ row.status === 1 ? '进行中' : '已结束' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="签到进度" width="170">
          <template #default="{ row }">
            <div class="progress-cell">
              <el-progress
                :percentage="row.totalCount ? Math.round((row.checkedCount / row.totalCount) * 100) : 0"
                :stroke-width="8"
                :show-text="false"
                :color="row.status === 1 ? '#10b981' : '#a3aed0'"
              />
              <span class="progress-text">{{ row.checkedCount }} / {{ row.totalCount }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="发布时间" width="170" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="router.push(`/checkin-detail/${row.id}`)">详情</el-button>
            <el-button v-if="row.status === 1" type="warning" size="small" link @click="handleEnd(row)">结束</el-button>
            <el-button v-if="(row.type === 2 || row.type === 3) && row.status === 1" type="success" size="small" link @click="showQrCode(row)">二维码</el-button>
            <el-button v-if="row.status === 0" type="danger" size="small" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="showCreateDialog" title="发布签到" width="500px">
      <el-form :model="createForm" label-width="80px">
        <el-form-item label="签到标题">
          <el-input v-model="createForm.title" placeholder="例: 第3周课堂签到" />
        </el-form-item>
        <el-form-item label="签到类型">
          <el-radio-group v-model="createForm.type">
            <el-radio :value="1">数字签到</el-radio>
            <el-radio :value="2">二维码签到</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="过期时间">
          <el-input-number v-model="createForm.expireMinutes" :min="0" :step="5" />
          <span style="margin-left: 8px; color: var(--color-text-secondary); font-size: 12px">分钟，0表示手动结束</span>
        </el-form-item>
        <el-form-item label="签到方式">
          <span style="color: var(--color-text-secondary); font-size: 12px">
            数字签到只能输入签到码，二维码签到只能扫码；迟到判定仅课堂自动签到生效
          </span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="handleCreate">发布</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showQrDialog" title="扫码签到" width="350px" align-center>
      <div class="qr-container">
        <canvas ref="qrCanvas" />
        <p class="qr-tip">请学生使用微信扫描此二维码签到</p>
        <p class="qr-tip">二维码每5分钟自动刷新，请保持此页面打开</p>
        <p class="qr-code">签到码: {{ qrCheckinCode }}</p>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick, watch, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { getCourseCheckins, createCheckin, endCheckin, deleteCheckin } from '../api/checkin'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import QRCode from 'qrcode'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const courseId = route.params.id

const isTeacher = computed(() => userStore.userInfo?.role === 'teacher')

function typeLabel(type) {
  if (type === 1) return '数字签到'
  if (type === 3) return '扫码/数字'
  return '二维码签到'
}
const checkins = ref([])
const loading = ref(false)
const showCreateDialog = ref(false)
const createForm = ref({ title: '', type: 1, expireMinutes: 0 })
const showQrDialog = ref(false)
const qrCheckinId = ref(null)
const qrCheckinCode = ref('')
const qrToken = ref('')
const qrCanvas = ref(null)

async function loadCheckins() {
  if (!isTeacher.value) return
  loading.value = true
  try {
    const res = await getCourseCheckins(courseId)
    checkins.value = res.data || []
  } catch (e) {
  } finally {
    loading.value = false
  }
}

async function handleCreate() {
  if (!createForm.value.title) {
    ElMessage.warning('请输入签到标题')
    return
  }
  if (![1, 2, 3].includes(Number(createForm.value.type))) {
    ElMessage.warning('签到类型只能是数字签到、二维码签到或扫码/数字签到')
    return
  }
  const running = checkins.value.find(i => i.status === 1)
  if (running) {
    ElMessage.warning(`已有进行中的签到「${running.title}」，请先结束后再发布新的签到`)
    return
  }
  try {
    const res = await createCheckin({
      courseId: Number(courseId),
      title: createForm.value.title,
      type: createForm.value.type,
      expireMinutes: createForm.value.expireMinutes || null
    })
    ElMessage.success('签到已发布')
    showCreateDialog.value = false
    createForm.value = { title: '', type: 1, expireMinutes: 0 }
    router.push(`/checkin-detail/${res.data.id}`)
  } catch (e) {}
}

async function handleEnd(row) {
  try {
    await ElMessageBox.confirm('确认结束该签到？结束后学生将无法继续签到。', '结束签到', { type: 'warning' })
    await endCheckin(row.id)
    ElMessage.success('签到已结束')
    loadCheckins()
  } catch (e) {}
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm('删除后该签到的所有签到记录也会被清除，确认删除？', '删除签到', { type: 'warning' })
    await deleteCheckin(row.id)
    ElMessage.success('签到已删除')
    loadCheckins()
  } catch (e) {}
}

function drawQr() {
  const canvas = qrCanvas.value
  if (!canvas || !qrCheckinId.value) return
  const token = qrToken.value ? `&token=${qrToken.value}` : ''
  const qrData = `checkin://${qrCheckinId.value}?code=${qrCheckinCode.value}${token}`
  QRCode.toCanvas(canvas, qrData, { width: 240, margin: 2, color: { dark: '#000000', light: '#ffffff' } })
}

async function showQrCode(row) {
  qrCheckinId.value = row.id
  qrCheckinCode.value = row.code
  qrToken.value = row.qrToken || ''
  showQrDialog.value = true
  await nextTick()
  drawQr()
}

let qrRefreshTimer = null

function stopQrRefresh() {
  if (qrRefreshTimer) {
    clearInterval(qrRefreshTimer)
    qrRefreshTimer = null
  }
}

function startQrRefresh() {
  stopQrRefresh()
  qrRefreshTimer = setInterval(async () => {
    await loadCheckins()
    const row = checkins.value.find((item) => item.id === qrCheckinId.value)
    if (!row || row.status !== 1) {
      showQrDialog.value = false
      stopQrRefresh()
      return
    }
    await showQrCode(row)
  }, 60000)
}

watch(showQrDialog, (visible) => {
  if (visible) startQrRefresh()
  else stopQrRefresh()
})

onMounted(() => {
  loadCheckins()
})

onUnmounted(stopQrRefresh)
</script>

<style scoped>
.checkin-list-page {
  padding: 0;
}

@media (max-width: 768px) {
  .checkin-list-page {
    padding: 0;
  }
}

.checkin-code {
  font-family: monospace;
  font-weight: 700;
  font-size: 15px;
  color: var(--color-primary);
  letter-spacing: 2px;
}

.progress-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.progress-cell :deep(.el-progress) {
  flex: 1;
}

.progress-text {
  font-size: 12px;
  color: var(--color-text-secondary);
  white-space: nowrap;
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
</style>