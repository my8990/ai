<template>
  <div class="course-page">
    <div class="page-header-bar">
      <h2>我的课程</h2>
      <div class="header-actions">
        <el-button v-if="userStore.userInfo?.role === 'teacher'" type="primary" @click="showCreateDialog = true">
          <el-icon><Plus /></el-icon> 创建课程
        </el-button>
        <el-button v-else type="primary" @click="showJoinDialog = true">
          <el-icon><Plus /></el-icon> 加入课程
        </el-button>
      </div>
    </div>

    <div v-loading="loading" class="course-wrap">
      <el-empty v-if="courses.length === 0 && !loading" description="暂无课程，先创建或加入一门课程吧">
        <el-button type="primary" @click="userStore.userInfo?.role === 'teacher' ? (showCreateDialog = true) : (showJoinDialog = true)">
          {{ userStore.userInfo?.role === 'teacher' ? '创建课程' : '加入课程' }}
        </el-button>
      </el-empty>

      <div class="course-grid">
        <div v-for="course in courses" :key="course.id" class="course-card" @click="goDetail(course.id)">
          <div class="card-cover" :style="{ background: getCoverGradient(course.id) }">
            <span class="cover-text">{{ course.name?.[0] || '课' }}</span>
          </div>
          <div class="card-body">
            <div class="card-top">
              <h3>{{ course.name }}</h3>
              <el-tag :type="course.status === 1 ? 'success' : 'danger'" size="small" effect="plain" round>
                {{ course.status === 1 ? '开放' : '关闭' }}
              </el-tag>
            </div>
            <p class="card-semester">{{ course.semester }}</p>
            <p v-if="course.college" class="card-college">{{ course.college }}</p>
            <div class="card-bottom">
              <span class="card-code">
                <el-icon><Key /></el-icon> {{ course.code }}
              </span>
              <span class="card-count">
                <el-icon><User /></el-icon> {{ course.studentCount }}人
              </span>
            </div>
            <p class="card-teacher">{{ course.teacherName }}</p>
          </div>
        </div>
      </div>
    </div>

    <el-dialog v-model="showCreateDialog" title="创建课程" width="500px" :close-on-click-modal="false">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="80px">
        <el-form-item label="课程名称" prop="name">
          <el-input v-model="createForm.name" maxlength="100" placeholder="请输入课程名称" />
        </el-form-item>
        <el-form-item label="学期" prop="semester">
          <el-input v-model="createForm.semester" placeholder="例: 2025-2026学年第1学期" />
        </el-form-item>
        <el-form-item label="学院">
          <el-input v-model="createForm.college" placeholder="请输入所属学院" />
        </el-form-item>
        <el-form-item label="课程简介">
          <el-input v-model="createForm.description" type="textarea" :rows="3" placeholder="请输入课程简介" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showJoinDialog" title="加入课程" width="400px" :close-on-click-modal="false">
      <el-form @submit.prevent="handleJoin">
        <el-form-item label="课程码">
          <el-input v-model="joinCode" maxlength="6" placeholder="请输入6位课程码" style="text-transform: uppercase; letter-spacing: 4px; font-size: 18px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showJoinDialog = false">取消</el-button>
        <el-button type="primary" @click="handleJoin">加入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { getMyCourses, createCourse, joinCourse } from '../api/course'
import { ElMessage } from 'element-plus'
import { Plus, Key, User } from '@element-plus/icons-vue'

const router = useRouter()
const userStore = useUserStore()

const courses = ref([])
const loading = ref(false)
const creating = ref(false)
const showCreateDialog = ref(false)
const showJoinDialog = ref(false)
const joinCode = ref('')

const createFormRef = ref(null)
const createForm = ref({ name: '', semester: '', college: '', description: '' })
const createRules = {
  name: [{ required: true, message: '请输入课程名称', trigger: 'blur' }],
  semester: [{ required: true, message: '请输入学期', trigger: 'blur' }]
}

const gradients = [
  'linear-gradient(135deg, #6366f1, #818cf8)',
  'linear-gradient(135deg, #10b981, #34d399)',
  'linear-gradient(135deg, #f59e0b, #fbbf24)',
  'linear-gradient(135deg, #ef4444, #f87171)',
  'linear-gradient(135deg, #8b5cf6, #a78bfa)',
  'linear-gradient(135deg, #06b6d4, #22d3ee)',
  'linear-gradient(135deg, #ec4899, #f472b6)',
  'linear-gradient(135deg, #14b8a6, #2dd4bf)'
]

function getCoverGradient(id) {
  return gradients[id % gradients.length]
}

async function loadCourses() {
  loading.value = true
  try {
    const res = await getMyCourses()
    courses.value = res.data || []
  } catch (e) {
  } finally {
    loading.value = false
  }
}

async function handleCreate() {
  try {
    await createFormRef.value.validate()
  } catch (e) {
    return
  }
  creating.value = true
  try {
    await createCourse({
      ...createForm.value,
      name: createForm.value.name.trim(),
      semester: createForm.value.semester.trim()
    })
    ElMessage.success('创建成功')
    showCreateDialog.value = false
    createForm.value = { name: '', semester: '', college: '', description: '' }
    loadCourses()
  } catch (e) {
  } finally {
    creating.value = false
  }
}

async function handleJoin() {
  if (!joinCode.value.trim()) {
    ElMessage.warning('请输入课程码')
    return
  }
  try {
    await joinCourse(joinCode.value.trim())
    ElMessage.success('加入成功')
    showJoinDialog.value = false
    joinCode.value = ''
    loadCourses()
  } catch (e) {}
}

function goDetail(courseId) {
  router.push(`/course/${courseId}`)
}

onMounted(loadCourses)
</script>

<style scoped>
.course-page {
  max-width: 1100px;
  margin: 0 auto;
  padding: 24px;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.course-wrap {
  min-height: 140px;
}

.course-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
}

.course-card {
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  cursor: pointer;
  transition: all var(--transition-normal);
  overflow: hidden;
}

.course-card:hover {
  transform: translateY(-4px);
  border-color: rgba(99, 102, 241, 0.3);
  box-shadow: var(--shadow-lg);
}

.card-cover {
  position: relative;
  height: 96px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.card-cover::before {
  content: '';
  position: absolute;
  right: -24px;
  top: -24px;
  width: 96px;
  height: 96px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.14);
}

.card-cover::after {
  content: '';
  position: absolute;
  left: -18px;
  bottom: -30px;
  width: 70px;
  height: 70px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.1);
}

.cover-text {
  position: relative;
  z-index: 1;
  font-size: 38px;
  font-weight: 800;
  color: rgba(255, 255, 255, 0.42);
  text-shadow: 0 2px 10px rgba(15, 23, 42, 0.12);
}

.card-body {
  padding: 18px;
}

.card-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 8px;
}

.card-top h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  line-height: 1.4;
  color: var(--color-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.card-semester {
  color: var(--color-text-regular);
  font-size: 13px;
  margin: 8px 0 4px;
}

.card-college {
  color: var(--color-text-secondary);
  font-size: 12px;
  margin: 0 0 8px;
}

.card-bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 10px;
  border-top: 1px solid var(--color-border-light);
  margin-top: 8px;
}

.card-code {
  display: flex;
  align-items: center;
  gap: 5px;
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 13px;
  letter-spacing: 0.5px;
  color: var(--color-primary-dark);
  font-weight: 600;
  background: var(--color-primary-bg);
  padding: 4px 10px;
  border-radius: 999px;
}

.card-count {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--color-text-secondary);
}

.card-teacher {
  color: var(--color-text-secondary);
  font-size: 12px;
  margin-top: 8px;
}

@media (max-width: 768px) {
  .course-page {
    padding: 16px;
  }

  .course-grid {
    grid-template-columns: 1fr;
  }
}
</style>