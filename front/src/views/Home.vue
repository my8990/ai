<template>
  <div class="dashboard-page">
    <div class="welcome-banner">
      <div class="welcome-text">
        <h1>欢迎回来，{{ userStore.userInfo?.nickname || userStore.userInfo?.username }}</h1>
        <p>{{ greetingText }}</p>
      </div>
      <div class="welcome-decoration">
        <div class="deco-circle deco-1"></div>
        <div class="deco-circle deco-2"></div>
        <div class="deco-circle deco-3"></div>
      </div>
    </div>

    <div class="stats-grid">
      <div class="stat-card" @click="router.push('/course')">
        <div class="stat-icon" style="background: linear-gradient(135deg, #6366f1, #818cf8)">
          <el-icon :size="24"><Reading /></el-icon>
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ courseCount }}</span>
          <span class="stat-label">我的课程</span>
        </div>
      </div>

      <div v-if="userStore.userInfo?.role === 'student'" class="stat-card" @click="router.push('/my-checkin')">
        <div class="stat-icon" style="background: linear-gradient(135deg, #10b981, #34d399)">
          <el-icon :size="24"><CircleCheck /></el-icon>
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ pendingCheckinCount }}</span>
          <span class="stat-label">待签到</span>
        </div>
      </div>

      <div class="stat-card" @click="router.push('/chat')">
        <div class="stat-icon" style="background: linear-gradient(135deg, #f59e0b, #fbbf24)">
          <el-icon :size="24"><ChatDotRound /></el-icon>
        </div>
        <div class="stat-info">
          <span class="stat-value">AI</span>
          <span class="stat-label">智能助手</span>
        </div>
      </div>

      <div v-if="userStore.userInfo?.role === 'admin'" class="stat-card" @click="router.push('/admin/review')">
        <div class="stat-icon" style="background: linear-gradient(135deg, #ef4444, #f87171)">
          <el-icon :size="24"><UserFilled /></el-icon>
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ pendingTeacherCount }}</span>
          <span class="stat-label">待审核</span>
        </div>
      </div>
    </div>

    <div class="quick-section">
      <h2 class="section-heading">快捷操作</h2>
      <div class="quick-grid">
        <div class="quick-card" @click="router.push('/course')">
          <el-icon :size="28" color="var(--color-primary)"><Reading /></el-icon>
          <span>我的课程</span>
        </div>

        <div v-if="userStore.userInfo?.role === 'student'" class="quick-card" @click="router.push('/my-checkin')">
          <el-icon :size="28" color="var(--color-success)"><CircleCheck /></el-icon>
          <span>我的签到</span>
        </div>

        <div v-if="userStore.userInfo?.role === 'teacher'" class="quick-card" @click="router.push('/course')">
          <el-icon :size="28" color="var(--color-warning)"><Edit /></el-icon>
          <span>发布作业</span>
        </div>

        <div class="quick-card" @click="router.push('/chat')">
          <el-icon :size="28" color="#f59e0b"><ChatDotRound /></el-icon>
          <span>AI 对话</span>
        </div>

        <div v-if="userStore.userInfo?.role === 'admin'" class="quick-card" @click="router.push('/admin/review')">
          <el-icon :size="28" color="var(--color-danger)"><UserFilled /></el-icon>
          <span>用户管理</span>
        </div>

        <div v-if="userStore.userInfo?.status === 0" class="quick-card" @click="router.push('/appeal')">
          <el-icon :size="28" color="var(--color-warning)"><Warning /></el-icon>
          <span>账号申诉</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { getMyCourses } from '../api/course'
import { getMyCheckins } from '../api/checkin'
import { getPendingTeachers } from '../api/admin'
import {
  Reading, CircleCheck, ChatDotRound, UserFilled, Edit, Warning
} from '@element-plus/icons-vue'

const router = useRouter()
const userStore = useUserStore()

const courseCount = ref(0)
const pendingCheckinCount = ref(0)
const pendingTeacherCount = ref(0)

const greetingText = computed(() => {
  const hour = new Date().getHours()
  const role = userStore.userInfo?.role
  const roleText = { student: '同学', teacher: '老师', admin: '管理员' }[role] || ''
  if (hour < 6) return `夜深了，${roleText}，注意休息`
  if (hour < 12) return `早上好，${roleText}，新的一天开始了`
  if (hour < 14) return `中午好，${roleText}，记得午休`
  if (hour < 18) return `下午好，${roleText}，继续加油`
  return `晚上好，${roleText}，辛苦了`
})

onMounted(async () => {
  try {
    const res = await getMyCourses()
    courseCount.value = res.data?.length || 0
  } catch {}

  if (userStore.userInfo?.role === 'student') {
    try {
      const res = await getMyCheckins()
      pendingCheckinCount.value = (res.data || []).filter(c => c.status === 1).length
    } catch {}
  }

  if (userStore.userInfo?.role === 'admin') {
    try {
      const res = await getPendingTeachers()
      pendingTeacherCount.value = res.data?.length || 0
    } catch {}
  }
})
</script>

<style scoped>
.dashboard-page {
  padding: 24px;
  max-width: 1100px;
  margin: 0 auto;
}

.welcome-banner {
  position: relative;
  background: linear-gradient(120deg, #5b5ff0 0%, #7c7ff5 45%, #a78bfa 100%);
  border-radius: var(--radius-xl);
  padding: 34px 38px;
  color: #fff;
  overflow: hidden;
  margin-bottom: 26px;
  box-shadow: 0 18px 40px -22px rgba(79, 70, 229, 0.85);
}

.welcome-text {
  position: relative;
  z-index: 2;
}

.welcome-text h1 {
  font-size: 26px;
  font-weight: 700;
  letter-spacing: -0.3px;
  margin-bottom: 8px;
}

.welcome-text p {
  font-size: 14px;
  opacity: 0.88;
}

.welcome-decoration {
  position: absolute;
  right: -20px;
  top: -20px;
}

.deco-circle {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.09);
}

.deco-1 {
  width: 150px;
  height: 150px;
  right: -10px;
  top: -10px;
}

.deco-2 {
  width: 92px;
  height: 92px;
  right: 96px;
  top: 66px;
}

.deco-3 {
  width: 46px;
  height: 46px;
  right: 190px;
  top: 18px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(210px, 1fr));
  gap: 16px;
  margin-bottom: 32px;
}

.stat-card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 22px;
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  cursor: pointer;
  overflow: hidden;
  transition: transform var(--transition-normal), box-shadow var(--transition-normal), border-color var(--transition-normal);
}

.stat-card::after {
  content: '';
  position: absolute;
  right: -30px;
  top: -30px;
  width: 90px;
  height: 90px;
  border-radius: 50%;
  background: var(--color-primary-soft);
  opacity: 0.6;
  transition: transform var(--transition-normal);
}

.stat-card:hover {
  transform: translateY(-3px);
  border-color: rgba(99, 102, 241, 0.35);
  box-shadow: var(--shadow-lg);
}

.stat-card:hover::after {
  transform: scale(1.25);
}

.stat-icon {
  position: relative;
  z-index: 1;
  width: 50px;
  height: 50px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
  box-shadow: 0 8px 18px -10px rgba(15, 23, 42, 0.6);
}

.stat-info {
  position: relative;
  z-index: 1;
}

.stat-value {
  display: block;
  font-size: 26px;
  font-weight: 700;
  letter-spacing: -0.5px;
  color: var(--color-text-primary);
  line-height: 1.2;
}

.stat-label {
  display: block;
  font-size: 13px;
  color: var(--color-text-secondary);
  margin-top: 3px;
}

.section-heading {
  font-size: 18px;
  font-weight: 700;
  color: var(--color-text-primary);
  margin-bottom: 16px;
  padding-left: 12px;
  position: relative;
}

.section-heading::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 4px;
  height: 18px;
  border-radius: 999px;
  background: var(--gradient-primary);
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(146px, 1fr));
  gap: 14px;
}

.quick-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 22px 16px;
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  cursor: pointer;
  transition: all var(--transition-normal);
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-regular);
}

.quick-card .el-icon {
  padding: 12px;
  border-radius: 14px;
  background: var(--color-primary-soft);
  box-sizing: content-box;
}

.quick-card:hover {
  transform: translateY(-3px);
  border-color: rgba(99, 102, 241, 0.3);
  box-shadow: var(--shadow-md);
  color: var(--color-primary-dark);
}

@media (max-width: 768px) {
  .dashboard-page {
    padding: 16px;
  }

  .welcome-banner {
    padding: 24px 20px;
  }

  .welcome-text h1 {
    font-size: 20px;
  }

  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .quick-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}
</style>