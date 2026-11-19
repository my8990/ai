<template>
  <div class="admin-page">
    <div class="page-header-bar">
      <h2>管理后台</h2>
    </div>

    <el-tabs v-model="activeTab" class="surface-card admin-tabs">
      <el-tab-pane label="待审核教师" name="pending">
        <el-table :data="pendingList" stripe>
          <el-table-column prop="username" label="用户名" />
          <el-table-column prop="nickname" label="昵称" />
          <el-table-column prop="phone" label="手机号" />
          <el-table-column prop="college" label="学院" />
          <el-table-column label="审核状态">
            <template #default>
              <el-tag type="warning" size="small" effect="plain" round>待审核</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180">
            <template #default="{ row }">
              <el-button type="primary" size="small" @click="handleApprove(row.id)">通过</el-button>
              <el-button type="danger" size="small" @click="handleReject(row.id)">驳回</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="pendingList.length === 0" description="暂无待审核教师" />
      </el-tab-pane>

      <el-tab-pane label="所有教师" name="all">
        <el-table :data="allList" stripe>
          <el-table-column prop="username" label="用户名" />
          <el-table-column prop="nickname" label="昵称" />
          <el-table-column prop="phone" label="手机号" />
          <el-table-column prop="college" label="学院" />
          <el-table-column label="审核状态">
            <template #default="{ row }">
              <el-tag :type="row.approved === 1 ? 'success' : 'warning'" size="small" effect="plain" round>
                {{ row.approved === 1 ? '已通过' : '待审核' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="账号状态">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small" effect="plain" round>
                {{ row.status === 1 ? '正常' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="用户管理" name="users">
        <el-table :data="userList" stripe>
          <el-table-column prop="username" label="用户名" />
          <el-table-column prop="nickname" label="昵称" />
          <el-table-column label="角色">
            <template #default="{ row }">
              <el-tag :type="roleTagType(row.role)" size="small" effect="plain" round>{{ roleLabel(row.role) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="账号状态">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small" effect="plain" round>
                {{ row.status === 1 ? '正常' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="banReason" label="禁用原因" />
          <el-table-column label="操作" width="180">
            <template #default="{ row }">
              <template v-if="row.role !== 'admin'">
                <el-button v-if="row.status === 1" type="danger" size="small" @click="handleBan(row)">禁用</el-button>
                <el-button v-else type="success" size="small" @click="handleUnban(row)">启用</el-button>
              </template>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="申诉管理" name="appeals">
        <el-table :data="appealList" stripe>
          <el-table-column prop="username" label="用户名" width="120" />
          <el-table-column prop="nickname" label="昵称" width="120" />
          <el-table-column prop="reason" label="申诉理由" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="appealTagType(row.status)" size="small" effect="plain" round>{{ appealLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="reply" label="管理员回复" />
          <el-table-column label="提交时间" width="170">
            <template #default="{ row }">
              {{ formatTime(row.createTime) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="200">
            <template #default="{ row }">
              <template v-if="row.status === 0">
                <el-button type="primary" size="small" @click="handleAppealAction(row, 1)">通过</el-button>
                <el-button type="danger" size="small" @click="handleAppealAction(row, 2)">驳回</el-button>
              </template>
              <span v-else class="handled-text">已处理</span>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="appealList.length === 0" description="暂无申诉" />
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="banDialogVisible" title="禁用账号" width="400">
      <el-form>
        <el-form-item label="禁用原因">
          <el-input v-model="banReason" type="textarea" :rows="3" placeholder="请输入禁用原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="banDialogVisible = false">取消</el-button>
        <el-button type="danger" @click="confirmBan">确认禁用</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="appealDialogVisible" :title="appealDialogTitle" width="400">
      <el-form>
        <el-form-item label="回复内容">
          <el-input v-model="appealReply" type="textarea" :rows="3" placeholder="请输入回复" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="appealDialogVisible = false">取消</el-button>
        <el-button :type="appealActionStatus === 1 ? 'primary' : 'danger'" @click="confirmAppealAction">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import {
  getPendingTeachers, approveTeacher, rejectTeacher, getAllTeachers,
  getAllUsers, banUser, unbanUser, getAllAppeals, handleAppeal
} from '../api/admin'
import { ElMessage, ElMessageBox } from 'element-plus'

const activeTab = ref('pending')
const pendingList = ref([])
const allList = ref([])
const userList = ref([])
const appealList = ref([])

const banDialogVisible = ref(false)
const banReason = ref('')
const banTargetUser = ref(null)

const appealDialogVisible = ref(false)
const appealReply = ref('')
const appealTarget = ref(null)
const appealActionStatus = ref(1)
const appealDialogTitle = ref('')

function roleLabel(role) {
  return { admin: '管理员', teacher: '教师', student: '学生' }[role] || role
}
function roleTagType(role) {
  return { admin: 'danger', teacher: 'warning', student: '' }[role] || ''
}
function appealLabel(status) {
  return { 0: '待处理', 1: '已通过', 2: '已驳回' }[status] || '未知'
}
function appealTagType(status) {
  return { 0: 'warning', 1: 'success', 2: 'danger' }[status] || ''
}
function formatTime(t) {
  if (!t) return ''
  return t.replace('T', ' ').substring(0, 19)
}

async function loadPending() {
  try {
    const res = await getPendingTeachers()
    pendingList.value = res.data
  } catch (e) {}
}

async function loadAll() {
  try {
    const res = await getAllTeachers()
    allList.value = res.data
  } catch (e) {}
}

async function loadUsers() {
  try {
    const res = await getAllUsers()
    userList.value = res.data
  } catch (e) {}
}

async function loadAppeals() {
  try {
    const res = await getAllAppeals()
    appealList.value = res.data
  } catch (e) {}
}

async function handleApprove(userId) {
  try {
    await ElMessageBox.confirm('确认通过该教师审核？', '确认', { type: 'info' })
    await approveTeacher(userId)
    ElMessage.success('审核通过')
    loadPending()
    if (activeTab.value === 'all') loadAll()
  } catch (e) {}
}

async function handleReject(userId) {
  try {
    await ElMessageBox.confirm('驳回后该教师账号将被禁用，确认？', '确认', { type: 'warning' })
    await rejectTeacher(userId)
    ElMessage.success('已驳回')
    loadPending()
    if (activeTab.value === 'all') loadAll()
  } catch (e) {}
}

function handleBan(row) {
  banTargetUser.value = row
  banReason.value = ''
  banDialogVisible.value = true
}

async function confirmBan() {
  if (!banReason.value.trim()) {
    ElMessage.warning('请输入禁用原因')
    return
  }
  try {
    await banUser(banTargetUser.value.id, banReason.value)
    ElMessage.success('已禁用')
    banDialogVisible.value = false
    loadUsers()
  } catch (e) {}
}

async function handleUnban(row) {
  try {
    await ElMessageBox.confirm(`确认启用账号 ${row.username}？`, '确认', { type: 'info' })
    await unbanUser(row.id)
    ElMessage.success('已启用')
    loadUsers()
  } catch (e) {}
}

function handleAppealAction(row, status) {
  appealTarget.value = row
  appealActionStatus.value = status
  appealReply.value = ''
  appealDialogTitle.value = status === 1 ? '通过申诉' : '驳回申诉'
  appealDialogVisible.value = true
}

async function confirmAppealAction() {
  try {
    await handleAppeal(appealTarget.value.id, appealActionStatus.value, appealReply.value)
    ElMessage.success(appealActionStatus.value === 1 ? '申诉已通过，账号已启用' : '申诉已驳回')
    appealDialogVisible.value = false
    loadAppeals()
    if (activeTab.value === 'users') loadUsers()
  } catch (e) {}
}

watch(activeTab, (val) => {
  if (val === 'all') loadAll()
  if (val === 'users') loadUsers()
  if (val === 'appeals') loadAppeals()
})

onMounted(loadPending)
</script>

<style scoped>
.admin-page {
  max-width: 1140px;
  margin: 0 auto;
  padding: 28px;
}

@media (max-width: 768px) {
  .admin-page {
    padding: 16px;
  }
}

.handled-text {
  color: var(--color-text-placeholder);
  font-size: 12px;
}

.admin-tabs {
  padding: 10px 22px 22px;
}

.admin-tabs :deep(.el-tabs__header) {
  margin-bottom: 18px;
}

.admin-tabs :deep(.el-tabs__item) {
  font-weight: 500;
}
</style>