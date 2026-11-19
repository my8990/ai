<template>
  <div class="quiz-bank-page">
    <div class="page-header-bar">
      <div>
        <h2>测验题库</h2>
        <p class="sub">创建属于你的测验，发布到任意课程，也可以公开给其他老师收藏使用</p>
      </div>
      <el-button type="primary" @click="openCreate">
        <el-icon><Plus /></el-icon> 新建测验
      </el-button>
    </div>

    <el-tabs v-model="activeTab" class="bank-tabs" @tab-change="onTabChange">
      <el-tab-pane label="我的题库" name="mine" />
      <el-tab-pane label="共享广场" name="market" />
      <el-tab-pane label="我的收藏" name="favorites" />
    </el-tabs>

    <div v-if="activeTab === 'market'" class="market-bar">
      <el-input v-model="keyword" placeholder="搜索标题或说明" clearable style="width: 260px" @keyup.enter="loadMarket">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-select v-model="sort" style="width: 140px" @change="loadMarket">
        <el-option label="最新公开" value="new" />
        <el-option label="最受欢迎" value="hot" />
      </el-select>
      <el-button @click="loadMarket">搜索</el-button>
    </div>

    <el-empty v-if="list.length === 0 && !loading" :description="emptyText" />

    <div v-else class="table-card">
      <el-table :data="list" stripe v-loading="loading">
        <el-table-column prop="title" label="测验标题" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="title-cell">{{ row.title }}</span>
            <el-tag v-if="row.mine" size="small" effect="plain" round class="tag-gap">我的</el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="activeTab !== 'mine'" prop="authorName" label="作者" width="120" show-overflow-tooltip />
        <el-table-column label="题目" width="80">
          <template #default="{ row }">{{ row.questionCount ?? 0 }} 题</template>
        </el-table-column>
        <el-table-column label="满分" width="80">
          <template #default="{ row }">{{ row.fullScore ?? 0 }}</template>
        </el-table-column>
        <el-table-column v-if="activeTab === 'mine'" label="可见性" width="100">
          <template #default="{ row }">
            <el-tag :type="row.shared ? 'success' : 'info'" size="small" effect="plain" round>
              {{ row.shared ? '公开' : '私有' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column v-else label="数据" width="120">
          <template #default="{ row }">
            <span class="muted">{{ row.favoriteCount ?? 0 }} 收藏 · {{ row.useCount ?? 0 }} 引用</span>
          </template>
        </el-table-column>
        <el-table-column v-if="activeTab === 'mine'" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'info' : 'success'" size="small" effect="plain" round>
              {{ row.statusText }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="150">
          <template #default="{ row }">{{ fmt(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" :width="activeTab === 'mine' ? 330 : 220" fixed="right">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="openDetail(row)">查看</el-button>
            <template v-if="activeTab === 'mine'">
              <el-button size="small" link type="warning" :disabled="row.status !== 0" @click="openEdit(row)">编辑</el-button>
              <el-button size="small" link :type="row.shared ? 'info' : 'success'" @click="toggleShare(row)">
                {{ row.shared ? '取消公开' : '公开' }}
              </el-button>
              <el-button size="small" link type="success" @click="openAssign(row)">发布到课程</el-button>
              <el-button size="small" link type="danger" @click="handleDelete(row)">删除</el-button>
            </template>
            <template v-else>
              <el-button size="small" link :type="row.favorited ? 'warning' : 'primary'" @click="toggleFavorite(row)">
                {{ row.favorited ? '取消收藏' : '收藏' }}
              </el-button>
              <el-button size="small" link type="success" @click="handleFork(row)">引用</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="showForm" :title="isEdit ? '编辑测验' : '新建测验'" width="760px" top="5vh">
      <el-form :model="form" label-width="90px">
        <el-form-item label="测验标题">
          <el-input v-model="form.title" placeholder="例: 第3章随堂测验" maxlength="60" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="测验说明，可留空" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="默认限时">
              <el-input-number v-model="form.durationMinutes" :min="0" :max="600" controls-position="right" />
              <span class="hint">分钟，0 为不限时</span>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="开始时间">
              <el-date-picker v-model="form.startTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="不填立即开始" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="截止时间">
              <el-date-picker v-model="form.endTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="不填则不限" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="答后展示">
          <el-switch v-model="form.showAnswerAfter" active-text="提交后展示答案与解析" />
        </el-form-item>
      </el-form>

      <div class="q-section">
        <div class="q-section-head">
          <span class="q-section-title">题目列表</span>
          <span class="q-total">共 {{ form.questions.length }} 题 · 总分 {{ totalScore }}</span>
        </div>

        <div v-if="form.questions.length === 0" class="q-empty">还没有题目，点击下方按钮添加</div>

        <div v-for="(q, qi) in form.questions" :key="qi" class="q-card">
          <div class="q-card-head">
            <span class="q-index">第 {{ qi + 1 }} 题</span>
            <el-select v-model="q.type" size="small" style="width: 110px" @change="onTypeChange(q)">
              <el-option label="单选题" :value="1" />
              <el-option label="多选题" :value="2" />
              <el-option label="判断题" :value="3" />
              <el-option label="填空题" :value="4" />
            </el-select>
            <el-input-number v-model="q.score" :min="0.5" :max="100" :step="0.5" size="small" controls-position="right" style="width: 110px" />
            <span class="hint">分</span>
            <el-button size="small" link type="danger" @click="removeQuestion(qi)">删除</el-button>
          </div>

          <el-input v-model="q.content" type="textarea" :rows="2" placeholder="请输入题干" />

          <template v-if="q.type === 1 || q.type === 2">
            <div v-for="(opt, oi) in q.options" :key="oi" class="opt-row">
              <span class="opt-label">{{ optionLabel(oi) }}</span>
              <el-input v-model="q.options[oi]" size="small" :placeholder="'选项 ' + optionLabel(oi)" />
              <el-button size="small" link type="danger" :disabled="q.options.length <= 2" @click="removeOption(q, oi)">移除</el-button>
            </div>
            <el-button size="small" link type="primary" :disabled="q.options.length >= 8" @click="q.options.push('')">添加选项</el-button>
          </template>

          <div class="ans-row">
            <span class="ans-label">正确答案</span>
            <el-radio-group v-if="q.type === 1 || q.type === 3" v-model="q.correctAnswer">
              <el-radio v-for="(opt, oi) in q.options" :key="oi" :label="String(oi)">
                {{ optionLabel(oi) }}
              </el-radio>
            </el-radio-group>
            <el-checkbox-group v-else-if="q.type === 2" v-model="q.multiAnswer">
              <el-checkbox v-for="(opt, oi) in q.options" :key="oi" :label="String(oi)">
                {{ optionLabel(oi) }}
              </el-checkbox>
            </el-checkbox-group>
            <el-input v-else v-model="q.correctAnswer" placeholder="多个答案用 | 分隔，如：北京|Beijing" />
          </div>

          <el-input v-model="q.analysis" type="textarea" :rows="2" placeholder="答案解析（可选）" />
        </div>

        <el-button class="q-add" plain type="primary" @click="addQuestion">
          <el-icon><Plus /></el-icon> 添加题目
        </el-button>
      </div>

      <template #footer>
        <el-button @click="showForm = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">{{ isEdit ? '保存' : '创建' }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showDetail" title="题目详情" width="640px" top="6vh">
      <div class="d-head">
        <span class="d-title">{{ detail.title }}</span>
        <el-tag size="small" effect="plain" round>{{ detail.statusText || '测验' }}</el-tag>
      </div>
      <div class="d-meta">
        <span>{{ detail.questionCount ?? 0 }} 题</span>
        <span>满分 {{ detail.fullScore ?? 0 }}</span>
        <span>{{ detail.durationMinutes ? detail.durationMinutes + ' 分钟' : '不限时' }}</span>
        <span v-if="detail.authorName">作者 {{ detail.authorName }}</span>
      </div>
      <div v-if="!detail.questions || detail.questions.length === 0" class="d-empty">该测验未公开题目，引用后即可查看与编辑</div>
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

    <el-dialog v-model="showAssign" title="发布到课程" width="520px">
      <el-form :model="assignForm" label-width="90px">
        <el-form-item label="测验">
          <span class="assign-quiz">{{ assignForm.quizTitle }}</span>
        </el-form-item>
        <el-form-item label="选择课程">
          <el-select v-model="assignForm.courseId" placeholder="请选择课程" style="width: 100%">
            <el-option v-for="c in courses" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="发布标题">
          <el-input v-model="assignForm.title" placeholder="不填则沿用测验标题" maxlength="60" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="限时">
              <el-input-number v-model="assignForm.durationMinutes" :min="0" :max="600" controls-position="right" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="立即发布">
              <el-switch v-model="assignForm.publishNow" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="开始时间">
          <el-date-picker v-model="assignForm.startTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="不填则沿用测验设置" style="width: 100%" />
        </el-form-item>
        <el-form-item label="截止时间">
          <el-date-picker v-model="assignForm.endTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="不填则不限" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAssign = false">取消</el-button>
        <el-button type="primary" :loading="assigning" @click="handleAssign">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import {
  getMyQuizzes, createQuiz, updateQuiz, deleteQuiz, getQuizDetail,
  shareQuiz, unshareQuiz, getMarketQuizzes, favoriteQuiz, unfavoriteQuiz,
  getMyFavorites, forkQuiz, assignQuiz
} from '../api/quiz'
import { getMyCourses } from '../api/course'

const activeTab = ref('mine')
const list = ref([])
const loading = ref(false)
const keyword = ref('')
const sort = ref('new')

const showForm = ref(false)
const showDetail = ref(false)
const showAssign = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const assigning = ref(false)
const detail = ref({})
const form = ref(emptyForm())
const assignForm = ref(emptyAssignForm())
const courses = ref([])

const emptyText = computed(() => {
  if (activeTab.value === 'mine') return '还没有测验，点击右上角新建'
  if (activeTab.value === 'favorites') return '还没有收藏其他老师的测验'
  return '暂时没有公开的测验'
})

const totalScore = computed(() =>
  form.value.questions.reduce((sum, q) => sum + (Number(q.score) || 0), 0)
)

function emptyForm() {
  return {
    id: null,
    title: '',
    description: '',
    durationMinutes: 0,
    startTime: '',
    endTime: '',
    showAnswerAfter: true,
    questions: []
  }
}

function emptyAssignForm() {
  return {
    quizId: null,
    quizTitle: '',
    courseId: null,
    title: '',
    durationMinutes: 0,
    startTime: '',
    endTime: '',
    publishNow: true
  }
}

function newQuestion() {
  return {
    type: 1,
    content: '',
    options: ['', ''],
    correctAnswer: '',
    multiAnswer: [],
    score: 5,
    analysis: ''
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

async function loadMine() {
  loading.value = true
  try {
    const res = await getMyQuizzes()
    list.value = res.data || []
  } catch (e) {
    list.value = []
  } finally {
    loading.value = false
  }
}

async function loadMarket() {
  loading.value = true
  try {
    const res = await getMarketQuizzes({ keyword: keyword.value || undefined, sort: sort.value })
    list.value = res.data || []
  } catch (e) {
    list.value = []
  } finally {
    loading.value = false
  }
}

async function loadFavorites() {
  loading.value = true
  try {
    const res = await getMyFavorites()
    list.value = res.data || []
  } catch (e) {
    list.value = []
  } finally {
    loading.value = false
  }
}

function onTabChange(name) {
  if (name === 'mine') loadMine()
  else if (name === 'market') loadMarket()
  else loadFavorites()
}

function openCreate() {
  isEdit.value = false
  form.value = emptyForm()
  addQuestion()
  showForm.value = true
}

function addQuestion() {
  form.value.questions.push(newQuestion())
}

function removeQuestion(i) {
  form.value.questions.splice(i, 1)
}

function removeOption(q, i) {
  q.options.splice(i, 1)
  if (q.type === 2) {
    q.multiAnswer = (q.multiAnswer || [])
      .filter(v => Number(v) !== i)
      .map(v => (Number(v) > i ? String(Number(v) - 1) : v))
  } else if (q.correctAnswer !== '' && q.correctAnswer != null) {
    const cur = Number(q.correctAnswer)
    if (cur === i) q.correctAnswer = ''
    else if (cur > i) q.correctAnswer = String(cur - 1)
  }
}

function onTypeChange(q) {
  if (q.type === 3) {
    q.options = ['正确', '错误']
    q.correctAnswer = ''
    q.multiAnswer = []
  } else if (q.type === 4) {
    q.options = []
    q.correctAnswer = ''
    q.multiAnswer = []
  } else {
    if (!q.options || q.options.length < 2) q.options = ['', '']
    q.correctAnswer = ''
    q.multiAnswer = []
  }
}

async function openEdit(row) {
  try {
    const res = await getQuizDetail(row.id)
    const d = res.data || {}
    isEdit.value = true
    form.value = {
      id: d.id,
      title: d.title || '',
      description: d.description || '',
      durationMinutes: d.durationMinutes || 0,
      startTime: fmtFull(d.startTime),
      endTime: fmtFull(d.endTime),
      showAnswerAfter: d.showAnswerAfter !== false,
      questions: (d.questions || []).map(q => ({
        type: q.type,
        content: q.content || '',
        options: q.type === 4 ? [] : (q.options || []),
        correctAnswer: q.type === 2 ? '' : (q.correctAnswer || ''),
        multiAnswer: q.type === 2 ? String(q.correctAnswer || '').split(',').filter(v => v !== '') : [],
        score: Number(q.score) || 1,
        analysis: q.analysis || ''
      }))
    }
    showForm.value = true
  } catch (e) {
    ElMessage.error('加载测验详情失败')
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

function validate() {
  if (!form.value.title.trim()) return '请填写测验标题'
  if (form.value.questions.length === 0) return '请至少添加一道题目'
  for (let i = 0; i < form.value.questions.length; i++) {
    const q = form.value.questions[i]
    const no = `第 ${i + 1} 题`
    if (!q.content.trim()) return `${no}题干不能为空`
    if (q.type !== 4) {
      const opts = q.options.filter(o => o.trim() !== '')
      if (opts.length < 2) return `${no}至少需要两个有效选项`
    }
    if (q.type === 2) {
      if (!q.multiAnswer || q.multiAnswer.length < 1) return `${no}请选择正确答案`
    } else if (!String(q.correctAnswer).trim()) {
      return `${no}请填写正确答案`
    }
  }
  return ''
}

function buildPayload() {
  const questions = form.value.questions.map((q, i) => ({
    type: q.type,
    content: q.content.trim(),
    options: q.type === 4 ? [] : q.options.filter(o => o.trim() !== ''),
    correctAnswer: q.type === 2 ? (q.multiAnswer || []).join(',') : String(q.correctAnswer).trim(),
    score: Number(q.score) || 0,
    analysis: (q.analysis || '').trim(),
    sortOrder: i
  }))
  return {
    id: form.value.id,
    title: form.value.title.trim(),
    description: (form.value.description || '').trim(),
    fullScore: questions.reduce((s, q) => s + q.score, 0),
    durationMinutes: form.value.durationMinutes || 0,
    startTime: form.value.startTime || '',
    endTime: form.value.endTime || '',
    showAnswerAfter: form.value.showAnswerAfter,
    questions
  }
}

async function handleSubmit() {
  const msg = validate()
  if (msg) {
    ElMessage.warning(msg)
    return
  }
  submitting.value = true
  try {
    const payload = buildPayload()
    if (isEdit.value) {
      await updateQuiz(payload)
      ElMessage.success('保存成功')
    } else {
      await createQuiz(payload)
      ElMessage.success('创建成功，可发布到课程或公开给其他老师')
    }
    showForm.value = false
    loadMine()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '提交失败')
  } finally {
    submitting.value = false
  }
}

async function toggleShare(row) {
  try {
    if (row.shared) {
      await unshareQuiz(row.id)
      ElMessage.success('已取消公开')
    } else {
      await shareQuiz(row.id)
      ElMessage.success('已公开，其他老师可在广场收藏与引用')
    }
    loadMine()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '操作失败')
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`删除后该测验及其题目将被清除，确定删除「${row.title}」吗？`, '删除测验', {
      type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消'
    })
  } catch (e) {
    return
  }
  try {
    await deleteQuiz(row.id)
    ElMessage.success('已删除')
    loadMine()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '删除失败')
  }
}

async function toggleFavorite(row) {
  try {
    if (row.favorited) {
      await unfavoriteQuiz(row.id)
      ElMessage.success('已取消收藏')
    } else {
      await favoriteQuiz(row.id)
      ElMessage.success('已收藏')
    }
    onTabChange(activeTab.value)
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '操作失败')
  }
}

async function handleFork(row) {
  try {
    await ElMessageBox.confirm(`将「${row.title}」复制到你的题库，之后可自由修改，确定引用吗？`, '引用测验', {
      type: 'info', confirmButtonText: '引用', cancelButtonText: '取消'
    })
  } catch (e) {
    return
  }
  try {
    await forkQuiz(row.id)
    ElMessage.success('已复制到我的题库')
    onTabChange(activeTab.value)
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '引用失败')
  }
}

async function openAssign(row) {
  assignForm.value = {
    quizId: row.id,
    quizTitle: row.title,
    courseId: null,
    title: '',
    durationMinutes: row.durationMinutes || 0,
    startTime: '',
    endTime: '',
    publishNow: true
  }
  if (courses.value.length === 0) {
    try {
      const res = await getMyCourses()
      courses.value = res.data || []
    } catch (e) {
      courses.value = []
    }
  }
  showAssign.value = true
}

async function handleAssign() {
  if (!assignForm.value.courseId) {
    ElMessage.warning('请选择课程')
    return
  }
  assigning.value = true
  try {
    await assignQuiz({
      quizId: assignForm.value.quizId,
      courseId: assignForm.value.courseId,
      title: assignForm.value.title,
      durationMinutes: assignForm.value.durationMinutes,
      startTime: assignForm.value.startTime,
      endTime: assignForm.value.endTime,
      publishNow: assignForm.value.publishNow
    })
    ElMessage.success(assignForm.value.publishNow ? '已发布到课程' : '已添加到课程，待发布')
    showAssign.value = false
    loadMine()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '发布失败')
  } finally {
    assigning.value = false
  }
}

onMounted(loadMine)
</script>

<style scoped>
.page-header-bar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 8px;
}

.page-header-bar h2 {
  font-size: 20px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.sub {
  margin-top: 4px;
  font-size: 13px;
  color: var(--color-text-secondary);
}

.bank-tabs {
  margin-bottom: 6px;
}

.market-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
}

.table-card {
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  padding: 6px 8px;
  overflow: hidden;
}

.title-cell {
  font-weight: 500;
}

.tag-gap {
  margin-left: 6px;
}

.muted {
  color: var(--color-text-secondary);
  font-size: 12px;
}

.hint {
  margin-left: 6px;
  font-size: 12px;
  color: var(--color-text-secondary);
}

.q-section {
  border-top: 1px solid var(--color-border);
  padding-top: 14px;
  margin-top: 6px;
}

.q-section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.q-section-title {
  font-size: 14px;
  font-weight: 600;
}

.q-total {
  font-size: 12px;
  color: var(--color-text-secondary);
}

.q-empty {
  padding: 18px 0;
  text-align: center;
  font-size: 13px;
  color: var(--color-text-secondary);
  background: #f8fafc;
  border-radius: 8px;
}

.q-card {
  border: 1px solid var(--color-border);
  border-radius: 10px;
  padding: 12px;
  margin-bottom: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  background: #fff;
}

.q-card-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.q-index {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.opt-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.opt-label,
.d-opt-label {
  width: 18px;
  font-size: 13px;
  color: var(--color-text-secondary);
}

.ans-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.ans-label {
  flex-shrink: 0;
  font-size: 13px;
  color: var(--color-text-secondary);
  line-height: 32px;
}

.q-add {
  width: 100%;
  border-style: dashed;
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

.d-empty {
  padding: 24px 0;
  text-align: center;
  font-size: 13px;
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

.assign-quiz {
  font-size: 14px;
  font-weight: 500;
}
</style>