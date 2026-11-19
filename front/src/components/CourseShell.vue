<template>
  <div class="course-shell">
    <div class="course-topbar">
      <div class="topbar-left">
        <router-link to="/course" class="back-link">
          <el-icon><ArrowLeft /></el-icon>
          <span>全部课程</span>
        </router-link>
        <span class="topbar-divider"></span>
        <span class="course-name">{{ course.name || '课程' }}</span>
        <el-tag v-if="course.status === 0" type="danger" size="small" effect="plain" round>已关闭</el-tag>
      </div>
      <div class="topbar-meta">
        <span v-if="course.semester" class="meta-item">
          <el-icon><Calendar /></el-icon>{{ course.semester }}
        </span>
        <span v-if="course.college" class="meta-item">
          <el-icon><School /></el-icon>{{ course.college }}
        </span>
        <span class="meta-item">
          <el-icon><User /></el-icon>{{ course.studentCount ?? 0 }} 人
        </span>
        <span v-if="course.code" class="code-chip" @click="copyCode">{{ course.code }}</span>
      </div>
    </div>

    <div class="shell-body">
      <aside class="course-nav">
        <router-link
          v-for="tab in tabs"
          :key="tab.key"
          :to="tab.to"
          class="tab-item"
          :class="{ active: isActive(tab) }"
        >
          <el-icon><component :is="tab.icon" /></el-icon>
          <span>{{ tab.label }}</span>
        </router-link>
      </aside>

      <section class="course-main">
        <router-view v-slot="{ Component }">
          <transition name="fade-slide" mode="out-in">
            <component :is="Component" :key="route.fullPath" />
          </transition>
        </router-view>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '../stores/user'
import { getCourseDetail } from '../api/course'
import { ElMessage } from 'element-plus'
import { useClipboard } from '@vueuse/core'
import {
  ArrowLeft, Calendar, School, User, HomeFilled,
  CircleCheck, Edit, FolderOpened, ChatLineSquare, DataAnalysis, VideoCamera, Bell, Tickets
} from '@element-plus/icons-vue'

const route = useRoute()
const userStore = useUserStore()
const { copy } = useClipboard()

const course = ref({})
const courseId = computed(() => route.params.id)

const isTeacher = computed(() => userStore.userInfo?.role === 'teacher')

const tabs = computed(() => {
  const base = `/course/${courseId.value}`
  const list = [
    { key: 'overview', label: '课程概览', to: base, icon: HomeFilled, exact: true },
    { key: 'notice', label: '公告', to: `${base}/notice`, icon: Bell },
    { key: 'homework', label: '作业', to: `${base}/homework`, icon: Edit },
    { key: 'material', label: '资料', to: `${base}/material`, icon: FolderOpened },
    { key: 'discussion', label: '讨论', to: `${base}/discussion`, icon: ChatLineSquare },
    { key: 'quiz', label: '测验', to: `${base}/quiz`, icon: Tickets }
  ]
  if (isTeacher.value) {
    list.splice(2, 0, { key: 'checkin', label: '签到', to: `${base}/checkin`, icon: CircleCheck })
    list.splice(2, 0, { key: 'live', label: '课堂', to: `${base}/live`, icon: VideoCamera })
    list.push({ key: 'score', label: '成绩', to: `${base}/score`, icon: DataAnalysis })
  } else {
    list.push({ key: 'my-checkin', label: '我的签到', to: `${base}/my-checkin`, icon: CircleCheck, exact: true })
    list.push({ key: 'my-score', label: '我的成绩', to: `${base}/my-score`, icon: DataAnalysis })
  }
  return list
})

function isActive(tab) {
  if (tab.exact) return route.path === tab.to
  return route.path === tab.to || route.path.startsWith(tab.to + '/')
}

function copyCode() {
  if (!course.value.code) return
  copy(course.value.code)
  ElMessage.success('已复制课程码')
}

async function loadCourse() {
  try {
    const res = await getCourseDetail(courseId.value)
    course.value = res.data || {}
  } catch (e) {
    course.value = {}
  }
}

watch(courseId, loadCourse, { immediate: true })
</script>

<style scoped>
.course-shell {
  display: flex;
  flex-direction: column;
  min-height: 100%;
}

.course-topbar {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  padding: 12px 24px;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: saturate(180%) blur(10px);
  border-bottom: 1px solid var(--color-border);
}

.topbar-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.back-link {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--color-text-secondary);
  padding: 5px 10px;
  border-radius: var(--radius-sm);
  transition: all var(--transition-fast);
  flex-shrink: 0;
}

.back-link:hover {
  color: var(--color-primary-dark);
  background: var(--color-primary-soft);
}

.topbar-divider {
  width: 1px;
  height: 16px;
  background: var(--color-border);
}

.course-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-primary);
  letter-spacing: -0.2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 320px;
}

.topbar-meta {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 13px;
  color: var(--color-text-secondary);
}

.code-chip {
  font-family: monospace;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 1px;
  color: var(--color-primary-dark);
  background: var(--color-primary-bg);
  padding: 4px 12px;
  border-radius: 999px;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.code-chip:hover {
  background: var(--color-primary-lighter);
  color: #fff;
}

.shell-body {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  padding: 16px 16px 24px 12px;
}

.course-nav {
  position: sticky;
  top: 64px;
  width: 164px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 8px;
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-sm);
}

.tab-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  font-size: 14px;
  color: var(--color-text-regular);
  transition: all var(--transition-fast);
  white-space: nowrap;
}

.tab-item .el-icon {
  font-size: 17px;
  flex-shrink: 0;
}

.tab-item:hover {
  background: var(--color-primary-soft);
  color: var(--color-primary-dark);
}

.tab-item.active {
  background: var(--color-primary-bg);
  color: var(--color-primary-dark);
  font-weight: 600;
  box-shadow: inset 3px 0 0 var(--color-primary);
}

.course-main {
  flex: 1;
  min-width: 0;
}

@media (max-width: 900px) {
  .course-topbar {
    padding: 10px 16px;
  }

  .course-name {
    max-width: 180px;
  }

  .shell-body {
    flex-direction: column;
    padding: 12px 12px 24px;
  }

  .course-nav {
    position: static;
    width: 100%;
    flex-direction: row;
    overflow-x: auto;
  }

  .tab-item {
    padding: 8px 12px;
  }
}
</style>