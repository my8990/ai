<template>
  <div class="course-detail-page">
    <div v-if="course.id" v-loading="loading" class="detail-content">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="课程名称">{{ course.name }}</el-descriptions-item>
        <el-descriptions-item label="学期">{{ course.semester }}</el-descriptions-item>
        <el-descriptions-item label="学院">{{ course.college || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="course.status === 1 ? 'success' : 'danger'" size="small" effect="plain" round>
            {{ course.status === 1 ? '开放' : '关闭' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="课程码">
          <span class="course-code">{{ course.code }}</span>
          <el-button size="small" type="primary" link @click="copyCode">复制</el-button>
        </el-descriptions-item>
        <el-descriptions-item label="学生人数">{{ course.studentCount }} 人</el-descriptions-item>
        <el-descriptions-item label="授课教师">{{ course.teacherName }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ course.createTime }}</el-descriptions-item>
        <el-descriptions-item label="课程简介" :span="2">{{ course.description || '暂无简介' }}</el-descriptions-item>
      </el-descriptions>

      <div class="action-bar">
        <template v-if="userStore.userInfo?.role === 'teacher'">
          <el-button type="primary" :loading="starting" @click="handleStartClass">开始上课</el-button>
          <el-button @click="showCheckinDialog = true">发布签到</el-button>
          <el-button @click="openQrcode">课程二维码</el-button>
          <el-button @click="showEditDialog = true">编辑课程</el-button>
          <el-button @click="handleToggle">{{ course.status === 1 ? '关闭课程' : '开放课程' }}</el-button>
          <el-button type="danger" @click="handleDissolve">解散课程</el-button>
        </template>
        <template v-else>
          <el-button type="danger" @click="handleQuit">退出课程</el-button>
        </template>
      </div>

      <div v-if="userStore.userInfo?.role === 'teacher'" class="student-section">
        <div class="section-header">
          <h3>学生名单 ({{ course.studentCount }} 人)</h3>
        </div>
        <el-table :data="students" stripe>
          <el-table-column prop="nickname" label="姓名" />
          <el-table-column prop="username" label="用户名" />
          <el-table-column prop="studentNo" label="学号" />
          <el-table-column label="状态">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small" effect="plain" round>
                {{ row.status === 1 ? '正常' : '已退课' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="加入时间" />
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button v-if="row.status === 1" type="danger" size="small" link @click="handleRemoveStudent(row)">移除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <el-dialog v-model="showQrcode" title="课程二维码" width="360px">
      <div class="qrcode-box">
        <canvas ref="qrcodeCanvas" class="qrcode-canvas"></canvas>
        <p class="qrcode-tip">学生使用小程序扫码即可加入课程</p>
        <p class="qrcode-code">课程码：{{ course.code }}</p>
      </div>
      <template #footer>
        <el-button @click="showQrcode = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showEditDialog" title="编辑课程" width="500px">
      <el-form :model="editForm" label-width="80px">
        <el-form-item label="课程名称">
          <el-input v-model="editForm.name" />
        </el-form-item>
        <el-form-item label="学期">
          <el-input v-model="editForm.semester" />
        </el-form-item>
        <el-form-item label="学院">
          <el-input v-model="editForm.college" />
        </el-form-item>
        <el-form-item label="课程简介">
          <el-input v-model="editForm.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEditDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleEdit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showCheckinDialog" title="发布签到" width="500px">
      <el-form :model="checkinForm" label-width="80px">
        <el-form-item label="签到标题">
          <el-input v-model="checkinForm.title" placeholder="例: 第3周课堂签到" />
        </el-form-item>
        <el-form-item label="签到类型">
          <el-radio-group v-model="checkinForm.type">
            <el-radio :value="1">数字签到</el-radio>
            <el-radio :value="2">二维码签到</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="过期时间">
          <el-input-number v-model="checkinForm.expireMinutes" :min="0" :step="5" />
          <span style="margin-left: 8px; color: var(--color-text-secondary); font-size: 12px">分钟，0表示手动结束</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCheckinDialog = false">取消</el-button>
        <el-button type="primary" :loading="publishing" @click="handleCreateCheckin">发布</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { getCourseDetail, updateCourse, toggleCourseStatus, dissolveCourse, getStudentList, quitCourse, removeStudent } from '../api/course'
import { createCheckin } from '../api/checkin'
import { startLive, getCurrentLive } from '../api/live'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useClipboard } from '@vueuse/core'
import QRCode from 'qrcode'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const courseId = route.params.id

const course = ref({})
const students = ref([])
const loading = ref(false)
const saving = ref(false)
const publishing = ref(false)
const starting = ref(false)
const showEditDialog = ref(false)
const editForm = ref({})
const showCheckinDialog = ref(false)
const checkinForm = ref({ title: '', type: 1, expireMinutes: 0 })
const showQrcode = ref(false)
const qrcodeCanvas = ref(null)

const { copy } = useClipboard()

async function loadDetail() {
  loading.value = true
  try {
    const res = await getCourseDetail(courseId)
    course.value = res.data
    editForm.value = { ...res.data }
  } catch (e) {
  } finally {
    loading.value = false
  }
}

async function loadStudents() {
  if (userStore.userInfo?.role !== 'teacher') return
  try {
    const res = await getStudentList(courseId)
    students.value = res.data
  } catch (e) {}
}

function copyCode() {
  copy(course.value.code)
  ElMessage.success('已复制课程码')
}

async function openQrcode() {
  showQrcode.value = true
  await nextTick()
  if (!qrcodeCanvas.value) return
  QRCode.toCanvas(qrcodeCanvas.value, `course:${course.value.code}`, { width: 220, margin: 1 })
}

async function handleToggle() {
  try {
    await toggleCourseStatus(courseId)
    ElMessage.success('操作成功')
    loadDetail()
  } catch (e) {}
}

async function handleDissolve() {
  try {
    await ElMessageBox.confirm('解散后课程和学生关联将被删除，不可恢复！', '确认解散', { type: 'warning' })
    await dissolveCourse(courseId)
    ElMessage.success('课程已解散')
    router.push('/course')
  } catch (e) {}
}

async function handleEdit() {
  if (!editForm.value.name?.trim()) {
    ElMessage.warning('请输入课程名称')
    return
  }
  saving.value = true
  try {
    await updateCourse({
      id: Number(courseId),
      name: editForm.value.name.trim(),
      semester: editForm.value.semester,
      college: editForm.value.college,
      description: editForm.value.description
    })
    ElMessage.success('编辑成功')
    showEditDialog.value = false
    loadDetail()
  } catch (e) {
  } finally {
    saving.value = false
  }
}

async function handleQuit() {
  try {
    await ElMessageBox.confirm('确认退出该课程？', '确认退课', { type: 'warning' })
    await quitCourse(courseId)
    ElMessage.success('已退出课程')
    router.push('/course')
  } catch (e) {}
}

async function handleRemoveStudent(row) {
  try {
    await ElMessageBox.confirm(`确认将学生 ${row.nickname || row.username} 移出课程？`, '移除学生', { type: 'warning' })
    await removeStudent(courseId, row.studentId)
    ElMessage.success('已移除')
    loadStudents()
    loadDetail()
  } catch (e) {}
}

async function handleCreateCheckin() {
  if (!checkinForm.value.title?.trim()) {
    ElMessage.warning('请输入签到标题')
    return
  }
  if (![1, 2, 3].includes(Number(checkinForm.value.type))) {
    ElMessage.warning('签到类型只能是数字签到、二维码签到或扫码/数字签到')
    return
  }
  publishing.value = true
  try {
    const res = await createCheckin({
      courseId: Number(courseId),
      title: checkinForm.value.title.trim(),
      type: checkinForm.value.type,
      expireMinutes: checkinForm.value.expireMinutes || null
    })
    ElMessage.success('签到已发布')
    showCheckinDialog.value = false
    checkinForm.value = { title: '', type: 1, expireMinutes: 0 }
    router.push(`/checkin-detail/${res.data.id}`)
  } catch (e) {
  } finally {
    publishing.value = false
  }
}

async function handleStartClass() {
  starting.value = true
  try {
    const res = await getCurrentLive(courseId)
    if (!res.data) {
      await startLive({ courseId: Number(courseId) })
      ElMessage.success('已开始上课，签到已自动发布')
    }
    router.push(`/course/${courseId}/live`)
  } catch (e) {
  } finally {
    starting.value = false
  }
}

onMounted(() => {
  loadDetail()
  loadStudents()
})
</script>

<style scoped>
.course-detail-page {
  padding: 20px 24px 24px;
}

.detail-content {
  margin-top: 4px;
}

.course-code {
  font-family: monospace;
  font-weight: 700;
  font-size: 16px;
  color: var(--color-primary-dark);
  margin-right: 8px;
  padding: 3px 12px;
  background: var(--color-primary-bg);
  border-radius: 999px;
}

.action-bar {
  margin: 20px 0;
  display: flex;
  gap: 8px;
}

.qrcode-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 8px 0;
}

.qrcode-canvas {
  border-radius: var(--radius-md);
}

.qrcode-tip {
  margin: 14px 0 4px;
  font-size: 13px;
  color: var(--color-text-secondary);
}

.qrcode-code {
  margin: 0;
  font-family: monospace;
  font-weight: 700;
  color: var(--color-primary);
}

.student-section {
  margin-top: 32px;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.section-header h3 {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 0;
}

@media (max-width: 768px) {
  .course-detail-page {
    padding: 16px;
  }
}
</style>