<template>
  <div class="my-score-page">
    <div class="page-header-bar">
      <h2>我的成绩</h2>
    </div>

    <div v-if="loading" v-loading="loading" class="loading-box"></div>

    <el-empty v-else-if="!score.courseId" description="暂无成绩数据，请先完成签到或提交作业" />

    <div v-else class="content">
      <div class="score-hero">
        <div class="hero-left">
          <p class="hero-label">总评成绩</p>
          <p class="hero-value" :style="{ color: gradeColor }">{{ score.totalScore }}</p>
          <el-tag :type="gradeTagType" effect="dark" round>{{ gradeText }}</el-tag>
        </div>
        <div class="hero-course">
          <p class="hero-label">课程</p>
          <p class="hero-course-name">{{ score.courseName }}</p>
        </div>
      </div>

      <div class="stat-grid">
        <div class="stat-card">
          <div class="stat-icon" style="background: linear-gradient(135deg, #6366f1, #818cf8)">
            <el-icon :size="22"><CircleCheck /></el-icon>
          </div>
          <div class="stat-info">
            <p class="stat-title">签到得分</p>
            <p class="stat-value">{{ score.checkinScore }}</p>
            <p class="stat-sub">出勤 {{ score.checkinCount }} / {{ score.totalCheckin }} 次 · 占比 {{ score.checkinWeight }}%</p>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-icon" style="background: linear-gradient(135deg, #f59e0b, #fbbf24)">
            <el-icon :size="22"><Edit /></el-icon>
          </div>
          <div class="stat-info">
            <p class="stat-title">作业得分</p>
            <p class="stat-value">{{ score.homeworkScore }}</p>
            <p class="stat-sub">已批改作业平均分 · 占比 {{ score.homeworkWeight }}%</p>
          </div>
        </div>
      </div>

      <div class="chart-section">
        <h3>签到明细</h3>
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
      </div>

      <div class="chart-section">
        <h3>作业明细</h3>
        <el-table :data="detail.homeworks || []" size="small" stripe>
          <el-table-column prop="title" label="作业" show-overflow-tooltip />
          <el-table-column label="状态" width="70">
            <template #default="{ row }">
              <el-tag v-if="row.status === 2" type="success" size="small" effect="plain" round>已批</el-tag>
              <el-tag v-else-if="row.status === 1" type="primary" size="small" effect="plain" round>待批</el-tag>
              <el-tag v-else type="info" size="small" effect="plain" round>未交</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="得分" width="110">
            <template #default="{ row }">{{ row.score != null ? `${row.score} / ${row.fullScore}` : '-' }}</template>
          </el-table-column>
          <el-table-column label="评语" prop="comment" show-overflow-tooltip />
        </el-table>
      </div>

      <div class="chart-section">
        <h3>成绩构成</h3>
        <div class="bar-chart">
          <div class="bar-item">
            <span class="bar-label">签到 {{ score.checkinWeight }}%</span>
            <div class="bar-track">
              <div class="bar-fill checkin" :style="{ width: barWidth(score.checkinScore) }"></div>
            </div>
            <span class="bar-val">{{ score.checkinScore }}</span>
          </div>
          <div class="bar-item">
            <span class="bar-label">作业 {{ score.homeworkWeight }}%</span>
            <div class="bar-track">
              <div class="bar-fill homework" :style="{ width: barWidth(score.homeworkScore) }"></div>
            </div>
            <span class="bar-val">{{ score.homeworkScore }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '../stores/user'
import { getMyScore, getScoreDetail } from '../api/homework'
import { CircleCheck, Edit } from '@element-plus/icons-vue'

const route = useRoute()
const userStore = useUserStore()
const courseId = route.params.id
const score = ref({})
const detail = ref({})
const loading = ref(false)

const gradeColor = computed(() => {
  const v = Number(score.value.totalScore) || 0
  if (v >= 85) return '#10b981'
  if (v >= 70) return '#6366f1'
  if (v >= 60) return '#f59e0b'
  return '#ef4444'
})

const gradeText = computed(() => {
  const v = Number(score.value.totalScore) || 0
  if (v >= 85) return '优秀'
  if (v >= 70) return '良好'
  if (v >= 60) return '及格'
  return '不及格'
})

const gradeTagType = computed(() => {
  const v = Number(score.value.totalScore) || 0
  if (v >= 85) return 'success'
  if (v >= 70) return 'primary'
  if (v >= 60) return 'warning'
  return 'danger'
})

function barWidth(val) {
  const v = Math.max(0, Math.min(100, Number(val) || 0))
  return v + '%'
}

async function loadScore() {
  loading.value = true
  try {
    const res = await getMyScore(courseId)
    score.value = res.data || {}
    const myId = userStore.userInfo?.id
    if (myId) {
      const detailRes = await getScoreDetail(courseId, myId)
      detail.value = detailRes.data || {}
    }
  } catch (e) {
  } finally {
    loading.value = false
  }
}

onMounted(() => loadScore())
</script>

<style scoped>
.my-score-page {
  padding: 0;
}

.loading-box {
  height: 200px;
}

.content {
  margin-top: 4px;
}

.score-hero {
  position: relative;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 30px 34px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xl);
  background:
    radial-gradient(circle at 92% 10%, rgba(99, 102, 241, 0.14) 0%, transparent 46%),
    linear-gradient(135deg, #eef2ff 0%, #f5f3ff 100%);
  box-shadow: var(--shadow-sm);
  margin-bottom: 22px;
}

.score-hero::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 5px;
  background: var(--gradient-primary);
}

.hero-label {
  margin: 0 0 6px;
  font-size: 13px;
  color: var(--color-text-secondary);
  letter-spacing: 0.3px;
}

.hero-value {
  margin: 0 0 10px;
  font-size: 52px;
  font-weight: 800;
  line-height: 1;
  letter-spacing: -1.5px;
}

.hero-course {
  text-align: right;
}

.hero-course-name {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 16px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 22px;
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  transition: transform var(--transition-normal), box-shadow var(--transition-normal);
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

.stat-icon {
  width: 50px;
  height: 50px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
  box-shadow: 0 8px 18px -8px rgba(99, 102, 241, 0.6);
}

.stat-title {
  margin: 0;
  font-size: 13px;
  color: var(--color-text-secondary);
}

.stat-value {
  margin: 2px 0;
  font-size: 26px;
  font-weight: 700;
  color: var(--color-text-primary);
  letter-spacing: -0.5px;
}

.stat-sub {
  margin: 0;
  font-size: 12px;
  color: var(--color-text-secondary);
}

.chart-section {
  margin-top: 24px;
  padding: 22px;
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.chart-section h3 {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: 18px;
  position: relative;
  padding-left: 12px;
}

.chart-section h3::before {
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

.bar-item {
  display: flex;
  align-items: center;
  margin-bottom: 12px;
}

.bar-label {
  width: 100px;
  font-size: 14px;
  color: var(--color-text-regular);
}

.bar-track {
  flex: 1;
  height: 24px;
  background: var(--color-bg-page);
  border-radius: 12px;
  overflow: hidden;
  margin: 0 12px;
}

.bar-fill {
  height: 100%;
  border-radius: 12px;
  transition: width 0.5s;
}

.bar-fill.checkin {
  background: linear-gradient(135deg, #6366f1, #818cf8);
}

.bar-fill.homework {
  background: linear-gradient(135deg, #f59e0b, #fbbf24);
}

.bar-val {
  width: 50px;
  text-align: right;
  font-weight: 600;
  color: var(--color-text-primary);
}

@media (max-width: 768px) {
  .score-hero {
    flex-direction: column;
    align-items: flex-start;
    gap: 16px;
    padding: 20px 24px;
  }

  .hero-course {
    text-align: left;
  }

  .stat-grid {
    grid-template-columns: 1fr;
  }
}
</style>