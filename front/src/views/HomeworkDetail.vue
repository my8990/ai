<template>
  <div class="homework-detail-page">
    <div class="page-header-bar">
      <h2>{{ homework.title || '作业详情' }}</h2>
      <el-button @click="router.back()">
        <el-icon><ArrowLeft /></el-icon> 返回
      </el-button>
    </div>

    <div class="content" v-if="homework.id" v-loading="detailLoading">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="标题">{{ homework.title }}</el-descriptions-item>
        <el-descriptions-item label="截止时间">{{ homework.deadline || '无限制' }}</el-descriptions-item>
        <el-descriptions-item label="满分分值">{{ homework.fullScore ?? 100 }}</el-descriptions-item>
        <el-descriptions-item label="参考文件">
          <a v-if="homework.refFileUrl" :href="homework.refFileUrl" target="_blank" class="file-link">{{ homework.refFileName || '下载' }}</a>
          <span v-else>无</span>
        </el-descriptions-item>
        <el-descriptions-item label="要求" :span="2">{{ homework.description || '无' }}</el-descriptions-item>
        <el-descriptions-item label="提交情况">{{ homework.submitCount }} / {{ homework.totalStudents }}</el-descriptions-item>
        <el-descriptions-item label="发布时间">{{ homework.createTime }}</el-descriptions-item>
      </el-descriptions>

      <div class="section-title">学生提交列表</div>
      <el-empty v-if="submissions.length === 0 && !loading" description="暂无学生提交" />
      <el-table v-else :data="submissions" stripe v-loading="loading">
        <el-table-column prop="studentName" label="姓名" width="120" />
        <el-table-column prop="studentNo" label="学号" width="120" />
        <el-table-column label="文字内容" show-overflow-tooltip>
          <template #default="{ row }">{{ row.content || '-' }}</template>
        </el-table-column>
        <el-table-column label="图片" width="80">
          <template #default="{ row }">
            <el-button v-if="row.imageList?.length" size="small" link type="primary" @click="previewImages(row.imageList)">查看</el-button>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="文件" width="80">
          <template #default="{ row }">
            <a v-if="row.fileUrl" :href="row.fileUrl" target="_blank" class="file-link">下载</a>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="提交状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.isLate === 1" type="warning" size="small" effect="plain" round>迟交</el-tag>
            <el-tag v-else type="success" size="small" effect="plain" round>按时</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="分数" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 1" type="success" effect="plain" round>{{ row.score }} / {{ homework.fullScore ?? 100 }}</el-tag>
            <span v-else class="ungraded">未批</span>
          </template>
        </el-table-column>
        <el-table-column prop="comment" label="评语" show-overflow-tooltip />
        <el-table-column label="提交时间" width="170" prop="createTime" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="openGrade(row)">{{ row.status === 1 ? '修改' : '批改' }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="showGradeDialog" title="批改作业" width="400px" :close-on-click-modal="false">
      <el-form :model="gradeForm" label-width="60px">
        <el-form-item label="分数">
          <el-input-number v-model="gradeForm.score" :min="0" :max="homework.fullScore ?? 100" :precision="1" />
          <span class="max-tip">满分 {{ homework.fullScore ?? 100 }}</span>
        </el-form-item>
        <el-form-item label="评语">
          <el-input v-model="gradeForm.comment" type="textarea" :rows="2" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showGradeDialog = false">取消</el-button>
        <el-button type="primary" :loading="grading" @click="handleGrade">确认</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showImageDialog" title="图片预览" width="600px">
      <div class="image-list">
        <el-image
          v-for="(url, i) in previewList"
          :key="i"
          :src="url"
          :preview-src-list="previewList"
          :initial-index="i"
          fit="cover"
          preview-teleported
          class="preview-img"
        />
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getHomeworkDetail, getHomeworkSubmissions, gradeHomework } from '../api/homework'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const homeworkId = route.params.id

const homework = ref({})
const submissions = ref([])
const loading = ref(false)
const detailLoading = ref(false)
const showGradeDialog = ref(false)
const gradeForm = ref({ submitId: null, score: 0, comment: '' })
const grading = ref(false)
const showImageDialog = ref(false)
const previewList = ref([])

async function loadDetail() {
  detailLoading.value = true
  try {
    const res = await getHomeworkDetail(homeworkId)
    homework.value = res.data
  } catch (e) {
  } finally {
    detailLoading.value = false
  }
}

async function loadSubmissions() {
  loading.value = true
  try {
    const res = await getHomeworkSubmissions(homeworkId)
    submissions.value = res.data || []
  } catch (e) {
  } finally {
    loading.value = false
  }
}

function openGrade(row) {
  gradeForm.value = { submitId: row.id, score: row.score || 0, comment: row.comment || '' }
  showGradeDialog.value = true
}

async function handleGrade() {
  if (gradeForm.value.score === null || gradeForm.value.score === undefined) {
    ElMessage.warning('请输入分数')
    return
  }
  grading.value = true
  try {
    await gradeHomework(gradeForm.value)
    ElMessage.success('批改成功')
    showGradeDialog.value = false
    loadSubmissions()
    loadDetail()
  } catch (e) {
  } finally {
    grading.value = false
  }
}

function previewImages(list) {
  previewList.value = list
  showImageDialog.value = true
}

onMounted(() => {
  loadDetail()
  loadSubmissions()
})
</script>

<style scoped>
.homework-detail-page {
  max-width: 1040px;
  margin: 0 auto;
  padding: 28px;
}

@media (max-width: 768px) {
  .homework-detail-page {
    padding: 16px;
  }
}

.content {
  margin-top: 4px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 24px 0 12px;
  position: relative;
  padding-left: 12px;
}

.section-title::before {
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

.score {
  color: var(--color-primary-dark);
  font-weight: 700;
  font-size: 15px;
}

.ungraded {
  color: var(--color-text-secondary);
}

.file-link {
  color: var(--color-primary);
  text-decoration: none;
  padding: 2px 10px;
  background: var(--color-primary-bg);
  border-radius: 999px;
  font-size: 13px;
}

.max-tip {
  margin-left: 8px;
  font-size: 12px;
  color: var(--color-text-secondary);
}

.image-list {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.preview-img {
  width: 120px;
  height: 120px;
  border-radius: var(--radius-md);
  cursor: pointer;
  overflow: hidden;
  border: 1px solid var(--color-border);
}
</style>