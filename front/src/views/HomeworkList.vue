<template>
  <div class="homework-page">
    <div class="page-header-bar">
      <h2>作业管理</h2>
      <el-button v-if="isTeacher" type="primary" @click="openCreate">
        <el-icon><Plus /></el-icon> 发布作业
      </el-button>
    </div>

    <el-empty v-if="homeworkList.length === 0 && !loading" description="暂无作业" />

    <div v-else class="table-card">
      <el-table :data="homeworkList" stripe v-loading="loading">
      <el-table-column prop="title" label="标题" />
      <el-table-column prop="description" label="要求" show-overflow-tooltip />
      <el-table-column label="满分" width="80">
        <template #default="{ row }">{{ row.fullScore ?? 100 }}</template>
      </el-table-column>
      <el-table-column label="参考文件" width="90">
        <template #default="{ row }">
          <a v-if="row.refFileUrl" :href="row.refFileUrl" target="_blank" class="file-link">下载</a>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="截止时间" width="170">
        <template #default="{ row }">{{ row.deadline || '无限制' }}</template>
      </el-table-column>
      <el-table-column label="提交" width="100">
        <template #default="{ row }">{{ row.submitCount }} / {{ row.totalStudents }}</template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.expired ? 'info' : 'success'" size="small" effect="plain" round>
            {{ row.expired ? '已截止' : '进行中' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="发布时间" width="170" />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <template v-if="isTeacher">
            <el-button size="small" link type="primary" @click="router.push(`/homework-detail/${row.id}`)">查看提交</el-button>
            <el-button size="small" link type="warning" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
          <template v-else>
            <el-button size="small" link type="primary" @click="router.push(`/submit-homework/${row.id}`)">{{ row.expired ? '查看' : '提交' }}</el-button>
          </template>
        </template>
      </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="showDialog" :title="isEdit ? '编辑作业' : '发布作业'" width="500px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="标题">
          <el-input v-model="form.title" placeholder="例: 第3周课后作业" />
        </el-form-item>
        <el-form-item label="要求">
          <el-input v-model="form.description" type="textarea" :rows="4" placeholder="作业要求描述" />
        </el-form-item>
        <el-form-item label="截止时间">
          <el-date-picker v-model="form.deadline" type="datetime" placeholder="不填则无截止" style="width: 100%" />
        </el-form-item>
        <el-form-item label="满分分值">
          <el-input-number v-model="form.fullScore" :min="1" :max="1000" :precision="1" />
        </el-form-item>
        <el-form-item label="参考文件">
          <el-upload
            action="/api/file/upload"
            :headers="uploadHeaders"
            :show-file-list="false"
            :on-success="handleRefSuccess"
            :on-remove="handleRefRemove"
          >
            <el-button size="small">上传参考文件</el-button>
            <span v-if="form.refFileName" class="ref-file">{{ form.refFileName }}</span>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">{{ isEdit ? '保存' : '发布' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { getCourseHomework, createHomework, updateHomework, deleteHomework } from '../api/homework'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const courseId = route.params.id
const isTeacher = userStore.userInfo?.role === 'teacher'

const homeworkList = ref([])
const loading = ref(false)
const showDialog = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const form = ref({ id: null, title: '', description: '', deadline: null, fullScore: 100, refFileUrl: '', refFileName: '' })

const uploadHeaders = computed(() => ({
  Authorization: 'Bearer ' + localStorage.getItem('token')
}))

function formatDate(date) {
  if (!date) return ''
  const d = new Date(date)
  if (Number.isNaN(d.getTime())) return ''
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function openCreate() {
  isEdit.value = false
  form.value = { id: null, title: '', description: '', deadline: null, fullScore: 100, refFileUrl: '', refFileName: '' }
  showDialog.value = true
}

function openEdit(row) {
  isEdit.value = true
  form.value = {
    id: row.id,
    title: row.title,
    description: row.description,
    deadline: row.deadline ? new Date(row.deadline.replace(/-/g, '/')) : null,
    fullScore: row.fullScore ?? 100,
    refFileUrl: row.refFileUrl || '',
    refFileName: row.refFileName || ''
  }
  showDialog.value = true
}

function handleRefSuccess(response, file) {
  if (response.code === 200) {
    form.value.refFileUrl = response.data
    form.value.refFileName = file.name
  }
}

function handleRefRemove() {
  form.value.refFileUrl = ''
  form.value.refFileName = ''
}

async function loadHomework() {
  loading.value = true
  try {
    const res = await getCourseHomework(courseId)
    homeworkList.value = res.data || []
  } catch (e) {
  } finally {
    loading.value = false
  }
}

async function handleSubmit() {
  if (!form.value.title) {
    ElMessage.warning('请输入作业标题')
    return
  }
  submitting.value = true
  try {
    const data = {
      title: form.value.title,
      description: form.value.description,
      deadline: formatDate(form.value.deadline),
      fullScore: form.value.fullScore || 100,
      refFileUrl: form.value.refFileUrl,
      refFileName: form.value.refFileName
    }
    if (isEdit.value) {
      await updateHomework({ id: form.value.id, ...data })
      ElMessage.success('作业已更新')
    } else {
      await createHomework({ courseId: Number(courseId), ...data })
      ElMessage.success('作业已发布')
    }
    showDialog.value = false
    loadHomework()
  } catch (e) {
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm('删除后该作业及学生提交记录将被清除，确认删除？', '删除作业', { type: 'warning' })
    await deleteHomework(row.id)
    ElMessage.success('作业已删除')
    loadHomework()
  } catch (e) {}
}

onMounted(() => loadHomework())
</script>

<style scoped>
.homework-page {
  padding: 0;
}

.table-card {
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  padding: 6px 8px;
  overflow: hidden;
}

@media (max-width: 768px) {
  .homework-page {
    padding: 0;
  }
}

.file-link {
  color: var(--color-primary);
  text-decoration: none;
  font-size: 13px;
}

.ref-file {
  margin-left: 10px;
  font-size: 12px;
  color: var(--color-primary);
}
</style>