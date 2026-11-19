<template>
  <div class="submit-page">
    <div class="page-header-bar">
      <h2>{{ homework.title || '提交作业' }}</h2>
      <el-button @click="router.back()">
        <el-icon><ArrowLeft /></el-icon> 返回
      </el-button>
    </div>

    <div class="content" v-if="homework.id" v-loading="loading">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="标题">{{ homework.title }}</el-descriptions-item>
        <el-descriptions-item label="要求">{{ homework.description || '无' }}</el-descriptions-item>
        <el-descriptions-item label="截止时间">{{ homework.deadline || '无限制' }}</el-descriptions-item>
      </el-descriptions>

      <div v-if="homework.expired" class="expired-tip">
        <el-alert title="作业已过截止时间，无法提交或修改" type="warning" :closable="false" show-icon />
      </div>

      <div class="submit-form">
        <h3>我的提交</h3>
        <el-form label-width="80px">
          <el-form-item label="文字内容">
            <el-input v-model="form.content" type="textarea" :rows="5" placeholder="输入文字内容" :disabled="homework.expired" maxlength="5000" show-word-limit />
          </el-form-item>
          <el-form-item label="图片">
            <el-upload
              action="/api/file/upload"
              :headers="uploadHeaders"
              list-type="picture-card"
              :file-list="fileList"
              :on-success="handleUploadSuccess"
              :on-remove="handleRemove"
              :disabled="homework.expired"
              accept="image/*"
            >
              <el-icon><Plus /></el-icon>
            </el-upload>
          </el-form-item>
          <el-form-item label="附件">
            <el-upload
              action="/api/file/upload"
              :headers="uploadHeaders"
              :on-success="handleFileSuccess"
              :on-remove="handleFileRemove"
              :disabled="homework.expired"
              :limit="1"
            >
              <el-button size="small">上传文件</el-button>
            </el-upload>
            <span v-if="form.fileName" class="file-name">{{ form.fileName }}</span>
          </el-form-item>
        </el-form>

        <el-button v-if="!homework.expired" type="primary" @click="handleSubmit" :loading="submitting">
          {{ mySubmit ? '更新提交' : '提交作业' }}
        </el-button>
      </div>

      <div v-if="mySubmit?.status === 1" class="grade-result">
        <el-descriptions :column="2" border title="批改结果">
          <el-descriptions-item label="分数">
            <span class="score">{{ mySubmit.score }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="评语">{{ mySubmit.comment || '无' }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getHomeworkDetail, getMySubmit, submitHomework } from '../api/homework'
import { ElMessage } from 'element-plus'
import { Plus, ArrowLeft } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const homeworkId = route.params.id

const homework = ref({})
const mySubmit = ref(null)
const loading = ref(false)
const form = ref({ content: '', images: '', fileUrl: '', fileName: '' })
const fileList = ref([])
const imageUrls = ref([])
const submitting = ref(false)

const uploadHeaders = computed(() => ({
  Authorization: 'Bearer ' + localStorage.getItem('token')
}))

async function loadDetail() {
  loading.value = true
  try {
    const res = await getHomeworkDetail(homeworkId)
    homework.value = res.data
  } catch (e) {
  } finally {
    loading.value = false
  }
}

async function loadMySubmit() {
  try {
    const res = await getMySubmit(homeworkId)
    if (res.data) {
      mySubmit.value = res.data
      form.value.content = res.data.content || ''
      form.value.fileUrl = res.data.fileUrl || ''
      form.value.fileName = res.data.fileName || ''
      if (res.data.imageList?.length) {
        imageUrls.value = [...res.data.imageList]
        form.value.images = res.data.imageList.join(',')
        fileList.value = imageUrls.value.map((url, i) => ({ name: `img${i}`, url, status: 'success' }))
      }
    }
  } catch (e) {}
}

function handleUploadSuccess(response, file) {
  if (response.code === 200) {
    imageUrls.value.push(response.data)
    form.value.images = imageUrls.value.join(',')
    file.url = response.data
  }
}

function handleRemove(file) {
  const url = file.response?.data || file.url
  imageUrls.value = imageUrls.value.filter(u => u !== url)
  form.value.images = imageUrls.value.join(',')
}

function handleFileSuccess(response, file) {
  if (response.code === 200) {
    form.value.fileUrl = response.data
    form.value.fileName = file.name || response.data.split('/').pop()
  }
}

function handleFileRemove() {
  form.value.fileUrl = ''
  form.value.fileName = ''
}

async function handleSubmit() {
  submitting.value = true
  try {
    await submitHomework({
      homeworkId: Number(homeworkId),
      content: form.value.content,
      images: form.value.images,
      fileUrl: form.value.fileUrl,
      fileName: form.value.fileName
    })
    ElMessage.success('提交成功')
    loadMySubmit()
  } catch (e) {
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadDetail()
  loadMySubmit()
})
</script>

<style scoped>
.submit-page {
  max-width: 840px;
  margin: 0 auto;
  padding: 28px;
}

.content {
  margin-top: 4px;
}

.expired-tip {
  margin: 16px 0;
}

.submit-form {
  margin-top: 20px;
}

.submit-form h3 {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: 14px;
  position: relative;
  padding-left: 12px;
}

.submit-form h3::before {
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

.file-name {
  margin-left: 12px;
  color: var(--color-primary);
  font-size: 13px;
}

.score {
  color: var(--color-primary);
  font-weight: 700;
  font-size: 18px;
}

.grade-result {
  margin-top: 24px;
}

@media (max-width: 768px) {
  .submit-page {
    padding: 16px;
  }
}
</style>