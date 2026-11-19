<template>
  <div class="live-page">
    <div class="page-header-bar">
      <h2>在线课堂</h2>
      <div class="bar-actions">
        <el-tag v-if="session && session.status === 1" :type="connected ? 'success' : 'warning'" size="small" effect="plain" round>
          {{ connected ? '实时推送' : '连接中' }}
        </el-tag>
        <el-tag v-if="session" :type="session.status === 1 ? 'success' : 'info'" size="small" effect="plain" round>
          {{ session.status === 1 ? '上课中' : '已下课' }}
        </el-tag>
        <span v-if="session && session.status === 1" class="online-summary">
          在线 {{ state.onlineCount || 0 }} / {{ state.totalCount || 0 }}
        </span>
        <span v-if="!session || session.status === 0" class="late-setting">
          迟到判定
          <el-input-number v-model="lateMinutes" :min="0" :max="120" :step="1" size="small" controls-position="right" />
          分钟
        </span>
        <el-button v-if="!session || session.status === 0" type="primary" :loading="starting" @click="handleStart">
          <el-icon><VideoCamera /></el-icon> 开始上课
        </el-button>
        <el-button v-else type="danger" plain @click="handleEnd">下课</el-button>
      </div>
    </div>

    <el-empty v-if="!session" description="点击「开始上课」自动发布签到并生成课堂二维码" />

    <div v-else class="live-body">
      <div class="live-main">
        <div class="slide-stage" :class="{ empty: !currentSlide }">
          <el-image
            v-if="currentSlide"
            :src="currentSlide.imageUrl"
            fit="contain"
            class="slide-image"
            :preview-src-list="slideUrls"
            :initial-index="currentPage - 1"
            preview-teleported
          />
          <div v-else class="stage-empty">
            <el-icon :size="42"><Picture /></el-icon>
            <p>还没有课件，请在右侧上传 PPT 导出的图片</p>
          </div>
        </div>

        <div class="slide-controls">
          <el-button :disabled="currentPage <= 1" @click="goPage(currentPage - 1)">
            <el-icon><ArrowLeft /></el-icon> 上一页
          </el-button>
          <span class="page-indicator">{{ currentPage }} / {{ session.slideTotal || 0 }}</span>
          <el-button :disabled="currentPage >= (session.slideTotal || 0)" @click="goPage(currentPage + 1)">
            下一页 <el-icon><ArrowRight /></el-icon>
          </el-button>
          <span class="follow-tip">
            <el-icon><Link /></el-icon> 学生端画面实时跟随教师翻页
          </span>
        </div>
      </div>

      <aside class="live-side">
        <div class="side-card qr-card">
          <h3>课堂二维码</h3>
          <img v-if="wxacode" :src="wxacode" class="qr-canvas" alt="课堂小程序码" />
          <canvas v-else ref="qrCanvas" class="qr-canvas"></canvas>
          <p class="qr-tip">学生扫码进入课堂，或输入签到码</p>
          <div class="qr-meta" v-if="session.checkinCode">
            <span>签到码</span>
            <strong class="qr-code-strong">{{ session.checkinCode }}</strong>
          </div>
          <div class="qr-meta">
            <span>签到进度</span>
            <strong>{{ state.checkedCount || 0 }} / {{ state.totalCount || 0 }}</strong>
          </div>
        </div>

        <div v-if="session.status === 1" class="side-card online-card">
          <div class="online-head">
            <h3>课堂在线</h3>
            <span class="online-count">{{ onlineList.length }} / {{ state.totalCount || 0 }}</span>
          </div>
          <div class="online-tabs">
            <span :class="{ active: onlineTab === 'online' }" @click="onlineTab = 'online'">在线 {{ onlineList.length }}</span>
            <span :class="{ active: onlineTab === 'miss' }" @click="onlineTab = 'miss'">未签到 {{ missList.length }}</span>
            <span :class="{ active: onlineTab === 'hand' }" @click="onlineTab = 'hand'">举手 {{ handList.length }}</span>
            <span :class="{ active: onlineTab === 'question' }" @click="onlineTab = 'question'">提问 {{ questionList.length }}</span>
          </div>
          <div class="online-list">
            <template v-if="onlineTab === 'online' || onlineTab === 'miss'">
              <div v-for="student in activeList" :key="student.userId" class="online-item">
                <span class="online-dot" :class="{ on: student.online }"></span>
                <span class="online-name">{{ student.name }}</span>
                <span class="online-no">{{ student.studentNo || '' }}</span>
                <el-tag v-if="student.late" type="warning" size="small" effect="plain">迟到</el-tag>
                <el-tag v-else-if="student.checked" type="success" size="small" effect="plain">已签到</el-tag>
                <el-tag v-else type="info" size="small" effect="plain">未签到</el-tag>
              </div>
              <p v-if="activeList.length === 0" class="online-empty">
                {{ onlineTab === 'online' ? '暂无学生在线' : '全部学生已签到' }}
              </p>
            </template>
            <template v-else>
              <div v-for="item in activeInteractions" :key="item.id" class="interaction-item">
                <div class="interaction-head">
                  <span class="online-name">{{ item.name || '学生' }}</span>
                  <span class="online-no">{{ item.studentNo || '' }}</span>
                  <el-button size="small" type="success" link @click="handleResolve(item)">已处理</el-button>
                </div>
                <p v-if="item.type === 'question'" class="interaction-content">{{ item.content }}</p>
              </div>
              <p v-if="activeInteractions.length === 0" class="online-empty">
                {{ onlineTab === 'hand' ? '暂无学生举手' : '暂无学生提问' }}
              </p>
            </template>
          </div>
        </div>

        <div class="side-card quiz-card">
          <div class="quiz-head">
            <h3>随堂测验</h3>
            <el-tag v-if="currentQuiz" size="small" effect="plain" round :type="currentQuiz.status === 1 ? 'success' : 'info'">
              {{ currentQuiz.statusText }}
            </el-tag>
          </div>

          <div v-if="currentQuiz" class="quiz-current">
            <div class="quiz-title">{{ currentQuiz.title }}</div>
            <div class="quiz-meta">
              <span>{{ currentQuiz.questionCount }} 题</span>
              <span>满分 {{ currentQuiz.fullScore }}</span>
              <span>{{ currentQuiz.durationMinutes ? currentQuiz.durationMinutes + ' 分钟' : '不限时' }}</span>
            </div>
            <div class="quiz-progress">
              <div class="quiz-progress-bar">
                <div class="quiz-progress-inner" :style="{ width: quizPercent + '%' }"></div>
              </div>
              <span class="quiz-progress-text">已提交 {{ currentQuiz.submittedCount || 0 }} / {{ currentQuiz.totalCount || 0 }}</span>
            </div>
            <div class="quiz-actions">
              <el-button size="small" type="success" :disabled="currentQuiz.status !== 1" @click="handleEndQuiz">结束测验</el-button>
              <el-button size="small" link type="primary" @click="goQuizPage">查看成绩</el-button>
            </div>
          </div>
          <p v-else class="quiz-empty">本节课还未发布测验</p>

          <div v-if="session && session.status === 1" class="quiz-publish">
            <el-select v-model="quizForm.quizId" placeholder="从我的题库选择测验" size="small" style="width: 100%">
              <el-option v-for="q in myQuizzes" :key="q.id" :label="q.title" :value="q.id" />
            </el-select>
            <div class="quiz-publish-row">
              <span class="quiz-duration-label">限时</span>
              <el-input-number v-model="quizForm.durationMinutes" :min="0" :max="120" :step="1" size="small" controls-position="right" />
              <span class="quiz-duration-label">分钟</span>
              <el-button size="small" type="primary" :loading="publishing" @click="handlePublishQuiz">
                {{ currentQuiz && currentQuiz.status === 1 ? '换一题' : '发布' }}
              </el-button>
            </div>
            <p class="quiz-tip">发布后学生端课堂页会立即收到答题入口，0 分钟表示不限时</p>
          </div>
        </div>

        <div class="side-card">
          <h3>课件（支持 PPT / Word / PDF / 图片）</h3>
          <el-upload
            :http-request="handleCoursewareUpload"
            :show-file-list="false"
            :before-upload="beforeUpload"
            multiple
            accept=".ppt,.pptx,.doc,.docx,.pdf,.odp,.odt,.rtf,.jpg,.jpeg,.png,.webp,.bmp,.gif"
          >
            <el-button size="small" :loading="uploading">
              <el-icon><Upload /></el-icon> 上传课件
            </el-button>
          </el-upload>
          <p class="upload-tip">
            {{ uploading ? uploadTip : 'PPT / Word 自动转为图片分页，单文件不超过 50MB' }}
          </p>

          <div class="slide-list">
            <div
              v-for="slide in slides"
              :key="slide.id"
              class="slide-item"
              :class="{ active: slide.pageNo === currentPage }"
              @click="goPage(slide.pageNo)"
            >
              <span class="slide-no">P{{ slide.pageNo }}</span>
              <img :src="slide.imageUrl" class="slide-thumb" />
              <el-button type="danger" link size="small" @click.stop="handleDeleteSlide(slide)">删除</el-button>
            </div>
            <p v-if="slides.length === 0" class="slide-empty">暂无课件页</p>
          </div>
        </div>
      </aside>
    </div>

    <div v-if="history.length" class="history-section">
      <h3>历史课堂</h3>
      <div class="history-list">
        <div v-for="item in history" :key="item.id" class="history-item" @click="openReport(item)">
          <div class="history-main">
            <span class="history-title">{{ item.title }}</span>
            <span class="history-time">{{ formatTime(item.createTime) }}</span>
          </div>
          <div class="history-meta">
            <span>{{ formatDuration(item.durationMinutes) }}</span>
            <span>签到 {{ item.checkedCount || 0 }}/{{ item.totalCount || 0 }}</span>
            <span class="history-link">查看报告</span>
          </div>
        </div>
      </div>
    </div>

    <el-dialog v-model="reportVisible" title="课堂课后报告" width="680px">
      <div v-loading="reportLoading">
        <template v-if="report">
          <div class="report-head">
            <h3>{{ report.title }}</h3>
            <p>{{ report.courseName }} · {{ report.teacherName }}</p>
            <p>{{ formatTime(report.startTime) }} ~ {{ formatTime(report.endTime) }}（{{ formatDuration(report.durationMinutes) }}）</p>
          </div>
          <div class="report-stats">
            <div class="report-stat"><span>课件页</span><strong>{{ report.slideTotal || 0 }}</strong></div>
            <div class="report-stat"><span>应到</span><strong>{{ report.totalCount || 0 }}</strong></div>
            <div class="report-stat"><span>已签到</span><strong>{{ report.checkedCount || 0 }}</strong></div>
            <div class="report-stat"><span>迟到</span><strong>{{ report.lateCount || 0 }}</strong></div>
            <div class="report-stat"><span>未签到</span><strong>{{ report.absentCount || 0 }}</strong></div>
            <div class="report-stat"><span>进入课堂</span><strong>{{ report.onlineCount || 0 }}</strong></div>
          </div>
          <el-table :data="report.students || []" size="small" max-height="360">
            <el-table-column prop="name" label="姓名" min-width="100" />
            <el-table-column prop="studentNo" label="学号" min-width="120" />
            <el-table-column label="签到状态" width="100">
              <template #default="{ row }">
                <el-tag v-if="row.late" type="warning" size="small">迟到</el-tag>
                <el-tag v-else-if="row.checked" type="success" size="small">已签到</el-tag>
                <el-tag v-else type="info" size="small">未签到</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="签到时间" min-width="150">
              <template #default="{ row }">{{ formatTime(row.checkinTime) }}</template>
            </el-table-column>
          </el-table>
        </template>
      </div>
      <template #footer>
        <el-button @click="reportVisible = false">关闭</el-button>
        <el-button type="primary" :disabled="!report || reportLoading" @click="exportReport">导出 CSV</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import {
  startLive, endLive, getLiveState, turnLivePage,
  getLiveSlides, addLiveSlides, uploadCourseware, deleteLiveSlide, getCurrentLive,
  getLiveWxacode, getLiveOnline, getLiveHistory, getLiveReport, exportLiveReport, createLiveWsTicket,
  getLiveInteractions, resolveLiveInteraction, getLiveEndPreview, publishLiveQuiz, endLiveQuiz
} from '../api/live'
import { getMyQuizzes } from '../api/quiz'
import { ElMessage, ElMessageBox } from 'element-plus'
import { VideoCamera, Picture, ArrowLeft, ArrowRight, Link, Upload } from '@element-plus/icons-vue'
import QRCode from 'qrcode'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const courseId = route.params.id

const session = ref(null)
const state = ref({})
const slides = ref([])
const starting = ref(false)
const lateMinutes = ref(5)
const uploading = ref(false)
const uploadTip = ref('')
const qrCanvas = ref(null)
const wxacode = ref('')
const onlineStudents = ref([])
const onlineTab = ref('online')
const interactions = ref([])
const history = ref([])
const reportVisible = ref(false)
const report = ref(null)
const reportLoading = ref(false)
const connected = ref(false)
const myQuizzes = ref([])
const publishing = ref(false)
const quizForm = ref({ quizId: null, durationMinutes: 5 })

let pollTimer = null
let ws = null
let wsPingTimer = null
let wsReconnectTimer = null
let wsClosedByUser = false
let onlineRefreshAt = 0
let interactionRefreshAt = 0

const currentPage = computed(() => state.value.currentPage || 0)
const currentSlide = computed(() => state.value.currentSlide || null)
const slideUrls = computed(() => slides.value.map(s => s.imageUrl))
const onlineList = computed(() => onlineStudents.value.filter(s => s.online))
const missList = computed(() => onlineStudents.value.filter(s => !s.checked))
const activeList = computed(() => (onlineTab.value === 'online' ? onlineList.value : missList.value))
const handList = computed(() => interactions.value.filter(item => item.type === 'hand'))
const questionList = computed(() => interactions.value.filter(item => item.type === 'question'))
const activeInteractions = computed(() => (onlineTab.value === 'hand' ? handList.value : questionList.value))
const currentQuiz = computed(() => state.value.currentQuiz || null)
const quizPercent = computed(() => {
  const quiz = currentQuiz.value
  if (!quiz || !quiz.totalCount) return 0
  return Math.min(100, Math.round((quiz.submittedCount || 0) * 100 / quiz.totalCount))
})

async function loadCurrent() {
  try {
    const res = await getCurrentLive(courseId)
    session.value = res.data || null
    if (session.value) {
      await refreshAll()
    }
  } catch (e) {}
}

async function handleStart() {
  starting.value = true
  try {
    const res = await startLive({ courseId: Number(courseId), lateMinutes: lateMinutes.value })
    session.value = res.data
    ElMessage.success('已开始上课，签到已自动发布（下课自动结束）')
    await refreshAll()
    connectWs()
    loadHistory()
  } catch (e) {
  } finally {
    starting.value = false
  }
}

async function handleEnd() {
  const sid = session.value.id
  let preview = null
  try {
    const res = await getLiveEndPreview(sid)
    preview = (res && res.data) || null
  } catch (e) {
    preview = null
  }

  let publish = false
  if (preview && preview.hasCourseware && !preview.published) {
    const fileName = preview.publishFileName || '课堂课件'
    try {
      await ElMessageBox.confirm(
        `本节课共 ${preview.slideTotal} 页课件，发布后学生可在课程公告与课程课件中查看《${fileName}》。是否发布？`,
        '下课并发布课件',
        {
          confirmButtonText: '发布课件并下课',
          cancelButtonText: '不发布，直接下课',
          distinguishCancelAndClose: true,
          type: 'warning'
        }
      )
      publish = true
    } catch (action) {
      if (action !== 'cancel') {
        return
      }
      publish = false
    }
  } else {
    const tip = preview && preview.published
      ? '本节课件已发布给学生，确认下课？下课后学生将无法继续进入课堂。'
      : '确认下课？下课后学生将无法继续进入课堂。'
    try {
      await ElMessageBox.confirm(tip, '确认下课', { type: 'warning' })
    } catch (e) {
      return
    }
  }

  try {
    await endLive(sid, publish)
    session.value.status = 0
    ElMessage.success(publish ? '已下课，课件已发布给学生' : '已下课')
    disconnectWs()
    onlineStudents.value = []
    interactions.value = []
    loadHistory()
  } catch (e) {}
}

async function refreshAll() {
  await Promise.all([refreshState(), loadSlides(), loadWxacode()])
  await renderQrCode()
}

async function loadWxacode() {
  if (!session.value || session.value.status === 0) {
    wxacode.value = ''
    return
  }
  try {
    const res = await getLiveWxacode(session.value.id)
    wxacode.value = res.data || ''
  } catch (e) {
    wxacode.value = ''
  }
}

async function refreshState() {
  if (!session.value) return
  try {
    const res = await getLiveState(session.value.id)
    applyState(res.data)
  } catch (e) {}
}

function applyState(data) {
  state.value = data || {}
  if (!session.value) return
  if (session.value.status !== state.value.status) {
    session.value.status = state.value.status
    if (state.value.status === 0) {
      disconnectWs()
      onlineStudents.value = []
      interactions.value = []
      ElMessage.success('课堂已结束')
      loadHistory()
    }
  }
  session.value.slideTotal = state.value.slideTotal || 0
  refreshOnline(false)
  loadInteractions(false)
}

async function refreshOnline(force) {
  if (!session.value || session.value.status === 0) {
    onlineStudents.value = []
    return
  }
  const now = Date.now()
  if (!force && now - onlineRefreshAt < 5000) return
  onlineRefreshAt = now
  try {
    const res = await getLiveOnline(session.value.id)
    onlineStudents.value = res.data || []
  } catch (e) {}
}

async function loadInteractions(force) {
  if (!session.value || session.value.status === 0) {
    interactions.value = []
    return
  }
  const now = Date.now()
  if (!force && now - interactionRefreshAt < 3000) return
  interactionRefreshAt = now
  try {
    const res = await getLiveInteractions(session.value.id)
    interactions.value = res.data || []
  } catch (e) {}
}

async function handleResolve(item) {
  try {
    await resolveLiveInteraction(item.id)
    interactions.value = interactions.value.filter(i => i.id !== item.id)
    ElMessage.success('已处理')
  } catch (e) {}
}

async function loadSlides() {
  if (!session.value) return
  try {
    const res = await getLiveSlides(session.value.id)
    slides.value = res.data || []
  } catch (e) {}
}

async function renderQrCode() {
  if (!session.value) return
  await nextTick()
  if (!qrCanvas.value) return
  await QRCode.toCanvas(qrCanvas.value, session.value.qrContent, { width: 190, margin: 1 })
}

async function goPage(pageNo) {
  if (!session.value || session.value.status === 0) return
  const total = session.value.slideTotal || 0
  if (total <= 0 || pageNo < 1 || pageNo > total) return
  try {
    const res = await turnLivePage({ sessionId: session.value.id, pageNo })
    applyState(res.data)
  } catch (e) {}
}

async function loadHistory() {
  try {
    const res = await getLiveHistory(courseId)
    history.value = res.data || []
  } catch (e) {}
}

async function loadMyQuizzes() {
  try {
    const res = await getMyQuizzes()
    myQuizzes.value = res.data || []
  } catch (e) {
    myQuizzes.value = []
  }
}

async function handlePublishQuiz() {
  if (!session.value || session.value.status === 0) return
  if (!quizForm.value.quizId) {
    ElMessage.warning('请先选择要发布的测验')
    return
  }
  publishing.value = true
  try {
    const res = await publishLiveQuiz(session.value.id, {
      quizId: quizForm.value.quizId,
      durationMinutes: quizForm.value.durationMinutes || 0
    })
    state.value = { ...state.value, currentQuiz: res.data }
    ElMessage.success('随堂测验已发布，学生端已同步')
  } catch (e) {
  } finally {
    publishing.value = false
  }
}

async function handleEndQuiz() {
  if (!session.value) return
  try {
    await ElMessageBox.confirm('结束后学生将无法继续提交，确认结束本次随堂测验？', '结束测验', { type: 'warning' })
  } catch (e) {
    return
  }
  try {
    await endLiveQuiz(session.value.id)
    if (state.value.currentQuiz) {
      state.value = {
        ...state.value,
        currentQuiz: { ...state.value.currentQuiz, status: 2, statusText: '已结束' }
      }
    }
    ElMessage.success('随堂测验已结束')
  } catch (e) {}
}

function goQuizPage() {
  router.push(`/course/${courseId}/quiz`)
}

async function openReport(item) {
  report.value = null
  reportVisible.value = true
  reportLoading.value = true
  try {
    const res = await getLiveReport(item.id)
    report.value = res.data || null
  } catch (e) {
    reportVisible.value = false
  } finally {
    reportLoading.value = false
  }
}

function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

async function exportReport() {
  if (!report.value) return
  try {
    const res = await exportLiveReport(report.value.sessionId)
    const blob = res.data
    let name = `${report.value.title || '课堂'}-课堂报告.csv`
    const disposition = (res.headers && res.headers['content-disposition']) || ''
    const match = disposition.match(/filename=([^;]+)/)
    if (match) {
      try {
        name = decodeURIComponent(match[1].trim().replace(/^"|"$/g, ''))
      } catch (e) {}
    }
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = name
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error('导出失败，请稍后重试')
  }
}

function formatDuration(minutes) {
  const total = Number(minutes || 0)
  if (total < 60) return `${total} 分钟`
  return `${Math.floor(total / 60)} 小时 ${total % 60} 分钟`
}

async function wsUrl() {
  const protocol = location.protocol === 'https:' ? 'wss' : 'ws'
  const res = await createLiveWsTicket(session.value.id)
  const ticket = (res && res.data && res.data.ticket) || ''
  if (!ticket) return ''
  return `${protocol}://${location.host}/api/ws/live?ticket=${encodeURIComponent(ticket)}`
}

async function connectWs() {
  if (!session.value || session.value.status === 0) return
  disconnectWs()
  wsClosedByUser = false
  let url
  try {
    url = await wsUrl()
  } catch (e) {
    url = ''
  }
  if (wsClosedByUser || !session.value || session.value.status === 0) return
  if (!url) {
    startPolling()
    wsReconnectTimer = setTimeout(connectWs, 3000)
    return
  }
  let socket
  try {
    socket = new WebSocket(url)
  } catch (e) {
    startPolling()
    return
  }
  ws = socket
  socket.onopen = () => {
    if (ws !== socket) return
    connected.value = true
    stopPolling()
    sendWsPing()
    refreshOnline(true)
  }
  socket.onmessage = (event) => {
    try {
      const msg = JSON.parse(event.data)
      if (msg && msg.type === 'state' && msg.data) {
        applyState(msg.data)
      }
    } catch (e) {}
  }
  socket.onerror = () => {
    if (ws !== socket) return
    connected.value = false
  }
  socket.onclose = () => {
    if (ws !== socket) return
    ws = null
    connected.value = false
    stopWsPing()
    if (wsClosedByUser || !session.value || session.value.status === 0) return
    startPolling()
    wsReconnectTimer = setTimeout(connectWs, 3000)
  }
  startWsPing()
}

function startWsPing() {
  stopWsPing()
  wsPingTimer = setInterval(sendWsPing, 15000)
}

function stopWsPing() {
  if (wsPingTimer) {
    clearInterval(wsPingTimer)
    wsPingTimer = null
  }
}

function sendWsPing() {
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send('ping')
  }
}

function disconnectWs() {
  wsClosedByUser = true
  stopWsPing()
  if (wsReconnectTimer) {
    clearTimeout(wsReconnectTimer)
    wsReconnectTimer = null
  }
  const socket = ws
  ws = null
  if (socket) {
    try {
      socket.close()
    } catch (e) {}
  }
  connected.value = false
  stopPolling()
}

function beforeUpload(file) {
  if (file.size > 50 * 1024 * 1024) {
    ElMessage.warning('单个课件不能超过 50MB')
    return false
  }
  return true
}

async function handleCoursewareUpload(options) {
  const file = options.file
  uploading.value = true
  uploadTip.value = '正在上传…'
  try {
    const res = await uploadCourseware(file, (e) => {
      if (e.total) {
        const percent = Math.round((e.loaded / e.total) * 100)
        uploadTip.value = percent < 100 ? `正在上传 ${percent}%` : '正在转换课件，请稍候…'
      }
    })
    const data = (res && res.data) || {}
    const pageUrls = data.pageUrls || []
    if (!pageUrls.length) {
      ElMessage.error('课件没有可展示的页面')
      return
    }
    await addLiveSlides({
      sessionId: session.value.id,
      pageUrls,
      fileName: data.fileName || file.name,
      documentUrl: data.documentUrl || ''
    })
    if (data.truncated) {
      ElMessage.warning(`课件页数超过上限，仅导入前 ${pageUrls.length} 页`)
    } else {
      ElMessage.success(`已添加 ${pageUrls.length} 页课件`)
    }
    await refreshAll()
  } catch (e) {
  } finally {
    uploading.value = false
    uploadTip.value = ''
  }
}

async function handleDeleteSlide(slide) {
  try {
    await ElMessageBox.confirm(`确认删除第 ${slide.pageNo} 页课件？`, '删除课件页', { type: 'warning' })
    await deleteLiveSlide(slide.id)
    ElMessage.success('已删除')
    await refreshAll()
  } catch (e) {}
}

function startPolling() {
  stopPolling()
  schedulePoll()
}

function schedulePoll() {
  const base = 5000
  const jitter = Math.round(base * 0.3 * (Math.random() * 2 - 1))
  pollTimer = setTimeout(async () => {
    pollTimer = null
    await refreshState()
    if (connected.value || !session.value || session.value.status === 0) return
    schedulePoll()
  }, base + jitter)
}

function stopPolling() {
  if (pollTimer) {
    clearTimeout(pollTimer)
    pollTimer = null
  }
}

onMounted(async () => {
  if (userStore.userInfo?.role !== 'teacher') {
    ElMessage.warning('仅教师可进入课堂管理')
    router.push(`/course/${courseId}`)
    return
  }
  loadHistory()
  loadMyQuizzes()
  await loadCurrent()
  if (session.value && session.value.status === 1) {
    connectWs()
  }
})

onUnmounted(disconnectWs)
</script>

<style scoped>
.live-page {
  padding: 20px 24px 24px;
}

.page-header-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.page-header-bar h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.bar-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.late-setting {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--color-text-secondary);
}

.late-setting :deep(.el-input-number) {
  width: 90px;
}

.live-body {
  display: flex;
  gap: 20px;
  align-items: flex-start;
}

.live-main {
  flex: 1;
  min-width: 0;
}

.slide-stage {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 420px;
  padding: 12px;
  background: #0f172a;
  border-radius: var(--radius-lg, 12px);
  overflow: hidden;
}

.slide-stage.empty {
  background: var(--color-bg-secondary, #f5f7fb);
  border: 1px dashed var(--color-border, #dcdfe6);
}

.slide-image {
  width: 100%;
  height: 460px;
}

.stage-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  color: var(--color-text-secondary);
}

.slide-controls {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 14px;
}

.page-indicator {
  font-family: monospace;
  font-weight: 700;
  color: var(--color-text-primary);
}

.follow-tip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-left: auto;
  font-size: 12px;
  color: var(--color-text-secondary);
}

.live-side {
  width: 300px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  flex-shrink: 0;
}

.side-card {
  padding: 16px;
  background: #fff;
  border-radius: var(--radius-lg, 12px);
  box-shadow: var(--shadow-sm, 0 2px 10px rgba(15, 23, 42, 0.06));
}

.side-card h3 {
  margin: 0 0 12px;
  font-size: 14px;
  font-weight: 600;
}

.qr-card {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.qr-card h3 {
  align-self: flex-start;
}

.qr-canvas {
  display: block;
  width: 190px;
  height: 190px;
  margin: 0 auto;
  border-radius: 8px;
  object-fit: contain;
}

.qr-tip {
  margin: 10px 0 12px;
  font-size: 12px;
  color: var(--color-text-secondary);
  text-align: center;
}

.qr-meta {
  display: flex;
  justify-content: space-between;
  width: 100%;
  font-size: 13px;
  color: var(--color-text-secondary);
  padding: 4px 0;
}

.qr-meta strong {
  color: var(--color-primary-dark, #4338ca);
  font-family: monospace;
}

.qr-code-strong {
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 2px;
}

.upload-tip {
  margin: 8px 0;
  font-size: 12px;
  color: var(--color-text-secondary);
}

.slide-list {
  max-height: 300px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.slide-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px;
  border-radius: 8px;
  cursor: pointer;
  border: 1px solid transparent;
  transition: all 0.2s;
}

.slide-item.active {
  border-color: var(--color-primary, #6366f1);
  background: var(--color-primary-bg, #eef2ff);
}

.slide-no {
  font-size: 12px;
  font-family: monospace;
  color: var(--color-text-secondary);
  width: 28px;
  flex-shrink: 0;
}

.slide-thumb {
  width: 120px;
  height: 68px;
  object-fit: cover;
  border-radius: 4px;
  flex: 1;
}

.slide-empty {
  font-size: 12px;
  color: var(--color-text-secondary);
  text-align: center;
  margin: 8px 0;
}

.online-summary {
  font-size: 13px;
  color: var(--color-text-secondary);
}

.online-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.online-head h3 {
  margin: 0;
}

.online-count {
  font-size: 12px;
  color: var(--color-text-secondary);
  font-family: monospace;
}

.online-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 16px;
  padding-bottom: 8px;
  margin-bottom: 8px;
  border-bottom: 1px solid var(--color-border, #ebeef5);
  font-size: 13px;
}

.online-tabs span {
  cursor: pointer;
  color: var(--color-text-secondary);
}

.online-tabs span.active {
  color: var(--color-primary, #6366f1);
  font-weight: 600;
}

.online-list {
  max-height: 240px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.online-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  padding: 4px 0;
}

.interaction-item {
  padding: 6px 0;
  border-bottom: 1px dashed #e5e7eb;
}

.interaction-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.interaction-head .online-name {
  font-weight: 500;
}

.interaction-head .el-button {
  margin-left: auto;
}

.interaction-content {
  margin: 4px 0 0;
  font-size: 13px;
  color: #475569;
  word-break: break-word;
}

.online-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #cbd5e1;
  flex-shrink: 0;
}

.online-dot.on {
  background: #22c55e;
}

.online-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.online-no {
  font-size: 12px;
  color: var(--color-text-secondary);
  font-family: monospace;
}

.online-empty {
  margin: 10px 0;
  font-size: 12px;
  color: var(--color-text-secondary);
  text-align: center;
}

.quiz-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.quiz-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.quiz-head h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
}

.quiz-current {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  border-radius: 10px;
  background: #f8fafc;
}

.quiz-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-primary, #0f172a);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.quiz-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  font-size: 12px;
  color: var(--color-text-secondary);
}

.quiz-progress {
  display: flex;
  align-items: center;
  gap: 10px;
}

.quiz-progress-bar {
  flex: 1;
  height: 6px;
  border-radius: 999px;
  background: #e2e8f0;
  overflow: hidden;
}

.quiz-progress-inner {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, #6366f1, #22c55e);
  transition: width 0.3s ease;
}

.quiz-progress-text {
  font-size: 12px;
  color: var(--color-text-secondary);
  white-space: nowrap;
}

.quiz-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.quiz-empty {
  margin: 0;
  font-size: 12px;
  color: var(--color-text-secondary);
  text-align: center;
}

.quiz-publish {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-top: 12px;
  border-top: 1px dashed #e2e8f0;
}

.quiz-publish-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.quiz-duration-label {
  font-size: 12px;
  color: var(--color-text-secondary);
  white-space: nowrap;
}

.quiz-tip {
  margin: 0;
  font-size: 12px;
  color: var(--color-text-secondary);
  line-height: 1.5;
}

.history-section {
  margin-top: 20px;
}

.history-section h3 {
  margin: 0 0 12px;
  font-size: 15px;
  font-weight: 600;
}

.history-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 12px;
}

.history-item {
  padding: 12px 14px;
  background: #fff;
  border-radius: var(--radius-lg, 12px);
  box-shadow: var(--shadow-sm, 0 2px 10px rgba(15, 23, 42, 0.06));
  cursor: pointer;
  transition: all 0.2s;
}

.history-item:hover {
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.12);
}

.history-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 6px;
}

.history-title {
  font-size: 14px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.history-time {
  font-size: 12px;
  color: var(--color-text-secondary);
  flex-shrink: 0;
}

.history-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12px;
  color: var(--color-text-secondary);
}

.history-link {
  margin-left: auto;
  color: var(--color-primary, #6366f1);
}

.report-head {
  margin-bottom: 14px;
}

.report-head h3 {
  margin: 0 0 6px;
  font-size: 16px;
}

.report-head p {
  margin: 2px 0;
  font-size: 13px;
  color: var(--color-text-secondary);
}

.report-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  margin-bottom: 16px;
}

.report-stat {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  background: var(--color-bg-secondary, #f5f7fb);
  border-radius: 8px;
  font-size: 13px;
  color: var(--color-text-secondary);
}

.report-stat strong {
  font-size: 18px;
  color: var(--color-text-primary);
  font-family: monospace;
}

@media (max-width: 1200px) {
  .live-body {
    flex-direction: column;
  }
  .live-side {
    width: 100%;
  }
}
</style>