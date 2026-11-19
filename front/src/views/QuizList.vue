<template>
  <div class="quiz-page">
    <div class="page-header-bar">
      <h2>测验管理</h2>
      <div v-if="isTeacher" class="header-actions">
        <el-button @click="goBank">测验题库</el-button>
        <el-button type="primary" @click="openAssign">
          <el-icon><Plus /></el-icon> 发布测验
        </el-button>
      </div>
    </div>

    <div v-if="!isTeacher" class="page-tip">在线答题请使用小程序端「动态 → 测试」进入，成绩会自动同步到此处</div>

    <el-empty v-if="quizList.length === 0 && !loading" description="暂无测验" />

    <div v-else class="table-card">
      <el-table :data="quizList" stripe v-loading="loading">
        <el-table-column prop="title" label="测验标题" min-width="180" show-overflow-tooltip />
        <el-table-column prop="description" label="说明" min-width="160" show-overflow-tooltip />
        <el-table-column label="题目" width="80">
          <template #default="{ row }">{{ row.questionCount ?? 0 }} 题</template>
        </el-table-column>
        <el-table-column label="满分" width="80">
          <template #default="{ row }">{{ row.fullScore ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="限时" width="90">
          <template #default="{ row }">{{ row.durationMinutes ? row.durationMinutes + ' 分钟' : '不限' }}</template>
        </el-table-column>
        <el-table-column label="截止时间" width="160">
          <template #default="{ row }">{{ fmt(row.endTime) || '不限' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row)" size="small" effect="plain" round>{{ row.statusText }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="!isTeacher" label="我的成绩" width="100">
          <template #default="{ row }">
            <span v-if="row.taken" class="my-score">{{ row.myScore }} 分</span>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column v-if="isTeacher" label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="openDetail(row)">题目</el-button>
            <el-button size="small" link type="warning" :disabled="row.assignmentStatus !== 0" @click="openSettings(row)">设置</el-button>
            <el-button size="small" link type="success" :disabled="row.assignmentStatus !== 0" @click="handlePublish(row)">发布</el-button>
            <el-button size="small" link type="info" :disabled="row.assignmentStatus !== 1" @click="handleEnd(row)">结束</el-button>
            <el-button size="small" link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="showAssign" title="发布测验到本课程" width="520px">
      <el-form :model="assignForm" label-width="90px">
        <el-form-item label="选择测验">
          <el-select v-model="assignForm.quizId" placeholder="从我的题库选择" style="width: 100%" @change="onQuizChange">
            <el-option v-for="q in myQuizzes" :key="q.id" :label="q.title" :value="q.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="myQuizzes.length === 0" label="提示">
          <span class="muted">题库中还没有测验，请先到「测验题库」创建</span>
        </el-form-item>
        <el-form-item label="发布标题">
          <el-input v-model="assignForm.title" placeholder="不填则沿用测验标题" maxlength="60" />
        </el-form-item>
        <el-form-item label="限时">
          <el-input-number v-model="assignForm.durationMinutes" :min="0" :max="600" controls-position="right" />
          <span class="hint">分钟，0 为不限时</span>
        </el-form-item>
        <el-form-item label="开始时间">
          <el-date-picker v-model="assignForm.startTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="不填则沿用测验设置" style="width: 100%" />
        </el-form-item>
        <el-form-item label="截止时间">
          <el-date-picker v-model="assignForm.endTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="不填则不限" style="width: 100%" />
        </el-form-item>
        <el-form-item label="立即发布">
          <el-switch v-model="assignForm.publishNow" />
          <span class="hint">关闭则先保存为待发布，稍后手动发布</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAssign = false">取消</el-button>
        <el-button type="primary" :loading="assigning" @click="handleAssign">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showSettings" title="修改发布设置" width="480px">
      <el-form :model="settingsForm" label-width="90px">
        <el-form-item label="发布标题">
          <el-input v-model="settingsForm.title" maxlength="60" />
        </el-form-item>
        <el-form-item label="限时">
          <el-input-number v-model="settingsForm.durationMinutes" :min="0" :max="600" controls-position="right" />
          <span class="hint">分钟，0 为不限时</span>
        </el-form-item>
        <el-form-item label="开始时间">
          <el-date-picker v-model="settingsForm.startTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="不填则不限" style="width: 100%" />
        </el-form-item>
        <el-form-item label="截止时间">
          <el-date-picker v-model="settingsForm.endTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="不填则不限" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showSettings = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSaveSettings">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showDetail" title="题目详情" width="640px" top="6vh">
      <div class="d-head">
        <span class="d-title">{{ detail.title }}</span>
        <el-tag size="small" effect="plain" round>{{ detail.statusText }}</el-tag>
      </div>
      <div class="d-meta">
        <span>{{ detail.questionCount }} 题</span>
        <span>满分 {{ detail.fullScore }}</span>
        <span>{{ detail.durationMinutes ? detail.durationMinutes + ' 分钟' : '不限时' }}</span>
        <span>截止 {{ fmt(detail.endTime) || '不限' }}</span>
      </div>
      <div v-for="(q, qi) in detail.questions || []" :key="qi" class="d-q">
        <div class="d-q-head">
          <span class="d-q-index">{{ qi + 1 }}.</span>
          <span class="d-q-type">{{ typeText(q.type) }}</span>
          <span class="d-q-score">{{ q.score }} 分</span>
        </div>
        <div class="d-q-content">{{ q.content }}</div>
        <div v-if="q.options && q.options.length" class="d-q-opts">
          <div v-for="(opt, oi) in q.options" :key="oi" class="d-q-opt">
            <span class="d-opt-label">{{ optionLabel(oi) }}</span>
            <span>{{ opt }}</span>
          </div>
        </div>
        <div v-if="q.correctAnswer" class="d-q-ans">正确答案：{{ answerText(q) }}</div>
        <div v-if="q.analysis" class="d-q-analysis">解析：{{ q.analysis }}</div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { useUserStore } from '../stores/user'
import {
  getCourseQuizzes, getQuizDetail, getMyQuizzes,
  assignQuiz, updateAssignment, publishAssignment, endAssignment, deleteAssignment
} from '../api/quiz'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const isTeacher = computed(() => userStore.userInfo?.role === 'teacher')
const courseId = computed(() => Number(route.params.id))

const quizList = ref([])
const myQuizzes = ref([])
const loading = ref(false)
const assigning = ref(false)
const saving = ref(false)
const showAssign = ref(false)
const showSettings = ref(false)
const showDetail = ref(false)
const detail = ref({})
const assignForm = ref(emptyAssign())
const settingsForm = ref(emptySettings())

function emptyAssign() {
  return {
    quizId: null,
    title: '',
    durationMinutes: 0,
    startTime: '',
    endTime: '',
    publishNow: true
  }
}

function emptySettings() {
  return {
    id: null,
    title: '',
    durationMinutes: 0,
    startTime: '',
    endTime: ''
  }
}

function optionLabel(i) {
  return String.fromCharCode(65 + i)
}

function typeText(type) {
  return { 1: '单选题', 2: '多选题', 3: '判断题', 4: '填空题' }[type] || '题目'
}

function answerText(q) {
  if (q.type === 4) return q.correctAnswer
  const opts = q.options || []
  const idx = String(q.correctAnswer || '').split(',').filter(v => v !== '')
  if (q.type === 3) return idx.map(i => opts[Number(i)] || '').join('、')
  return idx.map(i => optionLabel(Number(i))).join('、')
}

function fmt(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 16)
}

function fmtFull(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 19)
}

function statusType(row) {
  if (row.statusText === '待发布') return 'info'
  if (row.statusText === '已结束') return 'danger'
  if (row.statusText === '已提交') return 'info'
  return 'success'
}

async function loadList() {
  loading.value = true
  try {
    const res = await getCourseQuizzes(courseId.value)
    quizList.value = res.data || []
  } catch (e) {
    quizList.value = []
  } finally {
    loading.value = false
  }
}

async function loadMyQuizzes() {
  try {
    const res = await getMyQuizzes()
    myQuizzes.value = res.data || []
  } catch (e) {
    myQuizzes.value = []
  }
}

function goBank() {
  router.push('/quiz-bank')
}

async function openAssign() {
  assignForm.value = emptyAssign()
  showAssign.value = true
  await loadMyQuizzes()
}

function onQuizChange(id) {
  const quiz = myQuizzes.value.find(q => q.id === id)
  if (quiz) {
    assignForm.value.durationMinutes = quiz.durationMinutes || 0
    assignForm.value.startTime = fmtFull(quiz.startTime)
    assignForm.value.endTime = fmtFull(quiz.endTime)
  }
}

async function handleAssign() {
  if (!assignForm.value.quizId) {
    ElMessage.warning('请选择要发布的测验')
    return
  }
  assigning.value = true
  try {
    await assignQuiz({
      quizId: assignForm.value.quizId,
      courseId: courseId.value,
      title: assignForm.value.title,
      durationMinutes: assignForm.value.durationMinutes,
      startTime: assignForm.value.startTime,
      endTime: assignForm.value.endTime,
      publishNow: assignForm.value.publishNow
    })
    ElMessage.success(assignForm.value.publishNow ? '已发布' : '已添加到本课程，待发布')
    showAssign.value = false
    loadList()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '发布失败')
  } finally {
    assigning.value = false
  }
}

function openSettings(row) {
  settingsForm.value = {
    id: row.assignmentId,
    title: row.title || '',
    durationMinutes: row.durationMinutes || 0,
    startTime: fmtFull(row.startTime),
    endTime: fmtFull(row.endTime)
  }
  showSettings.value = true
}

async function handleSaveSettings() {
  saving.value = true
  try {
    await updateAssignment({
      id: settingsForm.value.id,
      title: settingsForm.value.title,
      durationMinutes: settingsForm.value.durationMinutes,
      startTime: settingsForm.value.startTime,
      endTime: settingsForm.value.endTime
    })
    ElMessage.success('已保存')
    showSettings.value = false
    loadList()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function openDetail(row) {
  try {
    const res = await getQuizDetail(row.id)
    detail.value = res.data || {}
    showDetail.value = true
  } catch (e) {
    ElMessage.error('加载测验详情失败')
  }
}

async function handlePublish(row) {
  try {
    await ElMessageBox.confirm(`发布后学生即可作答，确定发布「${row.title}」吗？`, '发布测验', {
      type: 'warning', confirmButtonText: '发布', cancelButtonText: '取消'
    })
  } catch (e) {
    return
  }
  try {
    await publishAssignment(row.assignmentId)
    ElMessage.success('已发布')
    loadList()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '发布失败')
  }
}

async function handleEnd(row) {
  try {
    await ElMessageBox.confirm(`结束后学生将无法继续作答，确定结束「${row.title}」吗？`, '结束测验', {
      type: 'warning', confirmButtonText: '结束', cancelButtonText: '取消'
    })
  } catch (e) {
    return
  }
  try {
    await endAssignment(row.assignmentId)
    ElMessage.success('已结束')
    loadList()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '操作失败')
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`删除后本课程内的答卷记录将一并清除，确定删除「${row.title}」吗？`, '删除测验', {
      type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消'
    })
  } catch (e) {
    return
  }
  try {
    await deleteAssignment(row.assignmentId)
    ElMessage.success('已删除')
    loadList()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '删除失败')
  }
}

onMounted(loadList)
</script>

<style scoped>
.page-header-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.page-header-bar h2 {
  font-size: 20px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.header-actions {
  display: flex;
  gap: 10px;
}

.table-card {
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  padding: 6px 8px;
  overflow: hidden;
}

.my-score {
  color: #15803d;
  font-weight: 600;
}

.page-tip {
  margin-bottom: 12px;
  padding: 10px 14px;
  font-size: 13px;
  color: #6d28d9;
  background: #ede9fe;
  border-radius: 8px;
}

.muted {
  color: var(--color-text-secondary);
}

.hint {
  margin-left: 6px;
  font-size: 12px;
  color: var(--color-text-secondary);
}

.d-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.d-title {
  font-size: 16px;
  font-weight: 600;
}

.d-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  margin: 8px 0 14px;
  font-size: 12px;
  color: var(--color-text-secondary);
}

.d-q {
  border-top: 1px solid var(--color-border);
  padding: 10px 0;
}

.d-q-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--color-text-secondary);
}

.d-q-content {
  margin: 6px 0;
  font-size: 14px;
  color: var(--color-text-primary);
}

.d-q-opts {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.d-q-opt {
  display: flex;
  gap: 8px;
  font-size: 13px;
}

.d-opt-label {
  width: 18px;
  font-size: 13px;
  color: var(--color-text-secondary);
}

.d-q-ans {
  margin-top: 6px;
  font-size: 13px;
  color: #15803d;
}

.d-q-analysis {
  margin-top: 4px;
  font-size: 13px;
  color: var(--color-text-secondary);
}
</style>