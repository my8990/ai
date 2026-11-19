<template>
  <div class="material-page">
    <div class="page-header-bar">
      <h2>课件资源</h2>
      <div class="bar-actions">
        <el-button v-if="isTeacher" type="primary" @click="openUpload">
          <el-icon><Upload /></el-icon> 上传课件
        </el-button>
      </div>
    </div>

    <el-empty v-if="materials.length === 0 && !loading" description="暂无课件" />

    <div v-else v-loading="loading" class="material-list">
      <div v-for="item in materials" :key="item.id" class="material-card hover-lift">
        <div class="file-icon" :class="`type-${item.fileType}`">
          <el-icon :size="22"><Document /></el-icon>
        </div>
        <div class="material-info">
          <div class="material-title-row">
            <h4>{{ item.title }}</h4>
            <el-tag v-if="item.status !== 1" type="info" size="small" effect="plain" round>已下架</el-tag>
          </div>
          <p v-if="item.description" class="material-desc">{{ item.description }}</p>
          <div class="material-meta">
            <span>{{ item.fileName || '未命名文件' }}</span>
            <span v-if="item.fileSize">{{ formatSize(item.fileSize) }}</span>
            <span>{{ item.createTime }}</span>
          </div>
        </div>
        <div class="material-actions">
          <el-button size="small" type="primary" link @click="preview(item)">查看</el-button>
          <template v-if="isTeacher">
            <el-button size="small" type="warning" link @click="handleToggle(item)">{{ item.status === 1 ? '下架' : '上架' }}</el-button>
            <el-button size="small" type="danger" link @click="handleDelete(item)">删除</el-button>
          </template>
        </div>
      </div>
    </div>

    <el-dialog v-model="showUpload" title="上传课件" width="480px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="课件标题">
          <el-input v-model="form.title" placeholder="例: 第3章 数据库设计" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="课件说明">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="选填，课件内容简介" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="课件文件">
          <el-upload
            action="/api/file/upload"
            :headers="uploadHeaders"
            :show-file-list="false"
            :on-success="handleUploadSuccess"
            :before-upload="beforeUpload"
            accept=".pdf,.jpg,.jpeg,.png,.gif,.webp,.bmp,.ppt,.pptx,.doc,.docx"
          >
            <el-button size="small">
              <el-icon><Upload /></el-icon> 选择文件
            </el-button>
            <span v-if="form.fileName" class="file-name">{{ form.fileName }}</span>
          </el-upload>
          <div class="upload-tip">支持 PDF、图片、Word、PPT，单个文件不超过 20MB</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showUpload = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">上传</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showPreview" :title="current.title" width="720px">
      <div class="preview-body">
        <el-image v-if="isImage" :src="current.fileUrl" fit="contain" class="preview-image" :preview-src-list="[current.fileUrl]" preview-teleported />
        <iframe v-else-if="isPdf" :src="current.fileUrl" class="preview-frame"></iframe>
        <div v-else class="preview-tip">
          <el-icon :size="36"><Document /></el-icon>
          <p>该文件类型暂不支持在线预览，请下载后查看</p>
          <el-button type="primary" @click="download(current)">下载文件</el-button>
        </div>
      </div>
      <template #footer>
        <el-button @click="showPreview = false">关闭</el-button>
        <el-button type="primary" @click="download(current)">下载</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '../stores/user'
import { getCourseMaterials, createMaterial, deleteMaterial, toggleMaterial } from '../api/material'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload, Document } from '@element-plus/icons-vue'

const route = useRoute()
const userStore = useUserStore()
const courseId = route.params.id
const isTeacher = userStore.userInfo?.role === 'teacher'

const materials = ref([])
const loading = ref(false)
const showUpload = ref(false)
const submitting = ref(false)
const form = ref({ title: '', description: '', fileUrl: '', fileName: '', fileSize: 0 })
const showPreview = ref(false)
const current = ref({})

const uploadHeaders = computed(() => ({
  Authorization: 'Bearer ' + localStorage.getItem('token')
}))

const isImage = computed(() => current.value.fileType === 'image')
const isPdf = computed(() => current.value.fileType === 'pdf')

function formatSize(size) {
  if (!size) return ''
  if (size < 1024) return size + ' B'
  if (size < 1024 * 1024) return (size / 1024).toFixed(1) + ' KB'
  return (size / 1024 / 1024).toFixed(1) + ' MB'
}

async function loadMaterials() {
  loading.value = true
  try {
    const res = await getCourseMaterials(courseId)
    materials.value = res.data || []
  } catch (e) {
  } finally {
    loading.value = false
  }
}

function openUpload() {
  form.value = { title: '', description: '', fileUrl: '', fileName: '', fileSize: 0 }
  showUpload.value = true
}

function beforeUpload(file) {
  if (file.size > 20 * 1024 * 1024) {
    ElMessage.warning('文件大小不能超过 20MB')
    return false
  }
  return true
}

function handleUploadSuccess(response, file) {
  if (response.code === 200) {
    form.value.fileUrl = response.data
    form.value.fileName = file.name
    form.value.fileSize = file.size
    ElMessage.success('文件上传成功')
  }
}

async function handleSubmit() {
  if (!form.value.title?.trim()) {
    ElMessage.warning('请输入课件标题')
    return
  }
  if (!form.value.fileUrl) {
    ElMessage.warning('请先选择课件文件')
    return
  }
  submitting.value = true
  try {
    await createMaterial({
      courseId: Number(courseId),
      title: form.value.title.trim(),
      description: form.value.description,
      fileUrl: form.value.fileUrl,
      fileName: form.value.fileName,
      fileSize: form.value.fileSize
    })
    ElMessage.success('课件已上传')
    showUpload.value = false
    loadMaterials()
  } catch (e) {
  } finally {
    submitting.value = false
  }
}

async function handleToggle(item) {
  try {
    await toggleMaterial(item.id)
    ElMessage.success(item.status === 1 ? '已下架' : '已上架')
    loadMaterials()
  } catch (e) {}
}

async function handleDelete(item) {
  try {
    await ElMessageBox.confirm(`确认删除课件「${item.title}」？`, '删除课件', { type: 'warning' })
    await deleteMaterial(item.id)
    ElMessage.success('已删除')
    loadMaterials()
  } catch (e) {}
}

function preview(item) {
  current.value = item
  showPreview.value = true
}

function download(item) {
  window.open(item.fileUrl, '_blank')
}

onMounted(() => loadMaterials())
</script>

<style scoped>
.material-page {
  padding: 0;
}

.bar-actions {
  display: flex;
  gap: 8px;
}

.material-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.material-card {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 18px 22px;
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  transition: transform var(--transition-normal), box-shadow var(--transition-normal);
}

.material-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-lg);
}

.file-icon {
  width: 50px;
  height: 50px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
  background: linear-gradient(135deg, #6366f1, #818cf8);
}

.file-icon.type-pdf {
  background: linear-gradient(135deg, #ef4444, #f87171);
}

.file-icon.type-image {
  background: linear-gradient(135deg, #10b981, #34d399);
}

.file-icon.type-ppt {
  background: linear-gradient(135deg, #f59e0b, #fbbf24);
}

.material-info {
  flex: 1;
  min-width: 0;
}

.material-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.material-title-row h4 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.material-desc {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--color-text-secondary);
}

.material-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-top: 6px;
  font-size: 12px;
  color: var(--color-text-placeholder);
}

.material-actions {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex-shrink: 0;
}

.file-name {
  margin-left: 12px;
  color: var(--color-primary);
  font-size: 13px;
}

.upload-tip {
  font-size: 12px;
  color: var(--color-text-placeholder);
  margin-top: 4px;
}

.preview-body {
  min-height: 320px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.preview-image {
  max-width: 100%;
  max-height: 60vh;
}

.preview-frame {
  width: 100%;
  height: 60vh;
  border: none;
}

.preview-tip {
  text-align: center;
  color: var(--color-text-secondary);
}

.preview-tip p {
  margin: 12px 0 16px;
}

@media (max-width: 768px) {
  .material-card {
    padding: 12px;
    gap: 12px;
  }
}
</style>