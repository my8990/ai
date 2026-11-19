<template>
  <div class="score-page">
    <div class="page-header-bar">
      <h2>成绩管理</h2>
    </div>

    <div class="content">
      <div class="rule-section">
        <div class="section-bar">
          <h3>成绩占比设置</h3>
          <span class="rule-tip">签到、作业、测验占比之和需为 100%</span>
        </div>
        <el-form :inline="true" :model="ruleForm" label-width="80px" class="rule-form">
          <el-form-item label="签到占比">
            <el-slider v-model="ruleForm.checkinWeight" :min="0" :max="100" :step="5" style="width: 200px" @change="(val) => onWeightChange('checkinWeight', val)" />
            <span class="weight-val">{{ ruleForm.checkinWeight }}%</span>
          </el-form-item>
          <el-form-item label="作业占比">
            <el-slider v-model="ruleForm.homeworkWeight" :min="0" :max="100" :step="5" style="width: 200px" @change="(val) => onWeightChange('homeworkWeight', val)" />
            <span class="weight-val">{{ ruleForm.homeworkWeight }}%</span>
          </el-form-item>
          <el-form-item label="测验占比">
            <el-slider v-model="ruleForm.quizWeight" :min="0" :max="100" :step="5" style="width: 200px" @change="(val) => onWeightChange('quizWeight', val)" />
            <span class="weight-val">{{ ruleForm.quizWeight }}%</span>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="savingRule" @click="handleSetRule">保存规则</el-button>
          </el-form-item>
        </el-form>
      </div>

      <div class="score-section">
        <div class="section-bar">
          <h3>学生成绩</h3>
          <div class="bar-actions">
            <el-button size="small" @click="loadScores">
              <el-icon><Refresh /></el-icon> 刷新
            </el-button>
            <el-select v-model="exportSort" size="small" style="width: 112px">
              <el-option label="按学号" value="studentNo" />
              <el-option label="按总评" value="score" />
            </el-select>
            <el-button type="success" size="small" :disabled="scoreList.length === 0" @click="handleExport">
              <el-icon><Download /></el-icon> 导出Excel
            </el-button>
          </div>
        </div>

        <el-empty v-if="scoreList.length === 0 && !loading" description="暂无学生成绩数据" />

        <div v-else class="table-wrap">
          <el-table :data="scoreList" stripe v-loading="loading">
          <el-table-column label="排名" width="70">
            <template #default="{ row }">
              <span class="rank-badge" :class="rankClass(row.rank)">{{ row.rank }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="studentNo" label="学号" width="120" />
          <el-table-column prop="studentName" label="姓名" width="110" />
          <el-table-column label="签到" width="110">
            <template #default="{ row }">{{ row.checkinCount }} / {{ row.totalCheckin }}</template>
          </el-table-column>
          <el-table-column label="签到得分" width="110">
            <template #default="{ row }">
              <el-tag type="primary" effect="plain" round>{{ row.checkinScore }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="作业得分" width="110">
            <template #default="{ row }">
              <el-tag type="warning" effect="plain" round>{{ row.homeworkScore }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="测验" width="110">
            <template #default="{ row }">{{ row.quizCount }} / {{ row.totalQuiz }}</template>
          </el-table-column>
          <el-table-column label="测验得分" width="110">
            <template #default="{ row }">
              <el-tag type="success" effect="plain" round>{{ row.quizScore }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="总评" width="110">
            <template #default="{ row }">
              <span class="total-score" :style="{ color: scoreColor(row.totalScore) }">{{ row.totalScore }}</span>
            </template>
          </el-table-column>
          <el-table-column label="等级" width="100">
            <template #default="{ row }">
              <el-tag :type="gradeTagType(row.totalScore)" effect="dark" round>{{ gradeText(row.totalScore) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button size="small" link type="primary" @click="openDetail(row)">明细</el-button>
            </template>
          </el-table-column>
          </el-table>
        </div>
      </div>
    </div>
    <el-drawer v-model="showDetail" :title="`${detail.studentName || ''} 成绩明细`" size="520px">
      <div v-loading="detailLoading">
        <div class="detail-summary">
          <div class="summary-item">
            <span class="summary-label">签到得分</span>
            <span class="summary-value">{{ detail.checkinScore ?? 0 }}</span>
            <span class="summary-sub">{{ detail.checkinCount ?? 0 }} / {{ detail.totalCheckin ?? 0 }} 次</span>
          </div>
          <div class="summary-item">
            <span class="summary-label">作业平均分</span>
            <span class="summary-value">{{ detail.homeworkScore ?? 0 }}</span>
            <span class="summary-sub">按满分归一化</span>
          </div>
          <div class="summary-item">
            <span class="summary-label">测验得分</span>
            <span class="summary-value">{{ detail.quizScore ?? 0 }}</span>
            <span class="summary-sub">{{ detail.quizCount ?? 0 }} / {{ detail.totalQuiz ?? 0 }} 次</span>
          </div>
          <div class="summary-item">
            <span class="summary-label">综合平时成绩</span>
            <span class="summary-value total">{{ detail.totalScore ?? 0 }}</span>
            <span class="summary-sub">签到 {{ detail.checkinWeight }}% + 作业 {{ detail.homeworkWeight }}% + 测验 {{ detail.quizWeight }}%</span>
          </div>
        </div>

        <div class="detail-section-title">签到记录</div>
        <el-table :data="detail.checkins || []" size="small" stripe>
          <el-table-column prop="title" label="签到" show-overflow-tooltip />
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag v-if="row.status === 1" type="success" size="small" effect="plain" round>正常</el-tag>
              <el-tag v-else-if="row.status === 2" type="warning" size="small" effect="plain" round>迟到</el-tag>
              <el-tag v-else type="danger" size="small" effect="plain" round>缺勤</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="签到时间" width="150">
            <template #default="{ row }">{{ row.submitTime || '-' }}</template>
          </el-table-column>
        </el-table>

        <div class="detail-section-title">作业记录</div>
        <el-table :data="detail.homeworks || []" size="small" stripe>
          <el-table-column prop="title" label="作业" show-overflow-tooltip />
          <el-table-column label="状态" width="70">
            <template #default="{ row }">
              <el-tag v-if="row.status === 2" type="success" size="small" effect="plain" round>已批</el-tag>
              <el-tag v-else-if="row.status === 1" type="primary" size="small" effect="plain" round>待批</el-tag>
              <el-tag v-else type="info" size="small" effect="plain" round>未交</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="得分" width="100">
            <template #default="{ row }">{{ row.score != null ? `${row.score} / ${row.fullScore}` : '-' }}</template>
          </el-table-column>
          <el-table-column label="归一化" width="80">
            <template #default="{ row }">{{ row.normalizedScore != null ? row.normalizedScore : '-' }}</template>
          </el-table-column>
          <el-table-column label="迟交" width="60">
            <template #default="{ row }">
              <span v-if="row.isLate === 1" class="late-tag">迟交</span>
              <span v-else>-</span>
            </template>
          </el-table-column>
        </el-table>

        <div class="detail-section-title">测验记录</div>
        <el-table :data="detail.quizzes || []" size="small" stripe>
          <el-table-column prop="title" label="测验" show-overflow-tooltip />
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag v-if="row.status === 1" type="success" size="small" effect="plain" round>已提交</el-tag>
              <el-tag v-else type="info" size="small" effect="plain" round>未作答</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="得分" width="100">
            <template #default="{ row }">{{ row.score != null ? `${row.score} / ${row.fullScore}` : `- / ${row.fullScore}` }}</template>
          </el-table-column>
        </el-table>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getScoreRule, setScoreRule, getCourseScores, exportScores, getScoreDetail } from '../api/homework'
import { ElMessage } from 'element-plus'
import { Download, Refresh } from '@element-plus/icons-vue'

const route = useRoute()
const courseId = route.params.id

const ruleForm = ref({ checkinWeight: 40, homeworkWeight: 30, quizWeight: 30 })
const exportSort = ref('studentNo')
const scoreList = ref([])
const loading = ref(false)
const savingRule = ref(false)
const showDetail = ref(false)
const detailLoading = ref(false)
const detail = ref({})

function rankClass(rank) {
  if (rank === 1) return 'gold'
  if (rank === 2) return 'silver'
  if (rank === 3) return 'bronze'
  return ''
}

async function openDetail(row) {
  showDetail.value = true
  detailLoading.value = true
  detail.value = { studentName: row.studentName, studentNo: row.studentNo }
  try {
    const res = await getScoreDetail(courseId, row.studentId)
    detail.value = res.data || {}
  } catch (e) {
  } finally {
    detailLoading.value = false
  }
}

function onWeightChange(key, val) {
  const others = ['checkinWeight', 'homeworkWeight', 'quizWeight'].filter((k) => k !== key)
  const remain = 100 - val
  const [first, second] = others
  const curFirst = ruleForm.value[first] || 0
  const curSecond = ruleForm.value[second] || 0
  const curSum = curFirst + curSecond
  let nextFirst = curSum > 0
    ? Math.round((remain * curFirst) / curSum / 5) * 5
    : Math.round(remain / 2 / 5) * 5
  if (nextFirst > remain) nextFirst = remain
  if (nextFirst < 0) nextFirst = 0
  ruleForm.value[key] = val
  ruleForm.value[first] = nextFirst
  ruleForm.value[second] = remain - nextFirst
}

function scoreColor(val) {
  const v = Number(val) || 0
  if (v >= 85) return '#10b981'
  if (v >= 70) return '#6366f1'
  if (v >= 60) return '#f59e0b'
  return '#ef4444'
}

function gradeText(val) {
  const v = Number(val) || 0
  if (v >= 85) return '优秀'
  if (v >= 70) return '良好'
  if (v >= 60) return '及格'
  return '不及格'
}

function gradeTagType(val) {
  const v = Number(val) || 0
  if (v >= 85) return 'success'
  if (v >= 70) return 'primary'
  if (v >= 60) return 'warning'
  return 'danger'
}

async function loadRule() {
  try {
    const res = await getScoreRule(courseId)
    if (res.data) {
      ruleForm.value.checkinWeight = res.data.checkinWeight ?? 40
      ruleForm.value.homeworkWeight = res.data.homeworkWeight ?? 30
      ruleForm.value.quizWeight = res.data.quizWeight ?? 30
    }
  } catch (e) {}
}

async function loadScores() {
  loading.value = true
  try {
    const res = await getCourseScores(courseId)
    scoreList.value = res.data || []
  } catch (e) {
  } finally {
    loading.value = false
  }
}

async function handleSetRule() {
  savingRule.value = true
  try {
    await setScoreRule({
      courseId: Number(courseId),
      checkinWeight: ruleForm.value.checkinWeight,
      homeworkWeight: ruleForm.value.homeworkWeight,
      quizWeight: ruleForm.value.quizWeight
    })
    ElMessage.success('规则已保存')
    loadScores()
  } catch (e) {
  } finally {
    savingRule.value = false
  }
}

async function handleExport() {
  try {
    const res = await exportScores(courseId, exportSort.value)
    const blob = new Blob([res.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = resolveFileName(res.headers)
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error('导出失败')
  }
}

function resolveFileName(headers) {
  const disposition = headers && (headers['content-disposition'] || headers['Content-Disposition'])
  if (!disposition) return '成绩表.xlsx'
  const matched = /filename\*=UTF-8''([^;]+)/i.exec(disposition) || /filename="?([^";]+)"?/i.exec(disposition)
  if (!matched) return '成绩表.xlsx'
  return decodeURIComponent(matched[1].replace(/"/g, ''))
}

onMounted(() => {
  loadRule()
  loadScores()
})
</script>

<style scoped>
.score-page {
  padding: 0;
}

.content {
  margin-top: 4px;
}

.rule-section {
  padding: 22px;
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  margin-bottom: 20px;
}

.rule-form {
  margin-top: 8px;
}

.rule-tip {
  font-size: 12px;
  color: var(--color-text-secondary);
}

.weight-val {
  margin-left: 12px;
  font-weight: 700;
  color: var(--color-primary);
  width: 44px;
  display: inline-block;
}

.section-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.section-bar h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-primary);
  position: relative;
  padding-left: 12px;
}

.section-bar h3::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 4px;
  height: 16px;
  border-radius: 999px;
  background: var(--gradient-primary);
}

.bar-actions {
  display: flex;
  gap: 8px;
}

.score-section {
  padding: 22px;
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.table-wrap {
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.total-score {
  font-weight: 700;
  font-size: 17px;
}

.rank-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  font-size: 12px;
  font-weight: 700;
  color: var(--color-text-secondary);
  background: var(--color-bg-page, #f5f7fa);
}

.rank-badge.gold {
  background: linear-gradient(135deg, #fbbf24, #f59e0b);
  color: #fff;
}

.rank-badge.silver {
  background: linear-gradient(135deg, #cbd5e1, #94a3b8);
  color: #fff;
}

.rank-badge.bronze {
  background: linear-gradient(135deg, #f0a375, #d97706);
  color: #fff;
}

.detail-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 20px;
}

.summary-item {
  flex: 1;
  padding: 16px;
  border-radius: var(--radius-md);
  background: var(--gradient-primary-soft);
  border: 1px solid var(--color-border-light);
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.summary-label {
  font-size: 12px;
  color: var(--color-text-secondary);
}

.summary-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--color-primary-dark);
  letter-spacing: -0.5px;
}

.summary-value.total {
  color: #10b981;
}

.summary-sub {
  font-size: 11px;
  color: var(--color-text-secondary);
}

.detail-section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 20px 0 10px;
  position: relative;
  padding-left: 12px;
}

.detail-section-title::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 14px;
  border-radius: 999px;
  background: var(--gradient-primary);
}

.late-tag {
  color: #f59e0b;
  font-size: 12px;
}

@media (max-width: 768px) {
  .section-bar {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }
}
</style>