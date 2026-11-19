<template>
  <div class="discussion-page">
    <div class="page-header-bar">
      <h2>班级讨论</h2>
    </div>

    <div class="publish-card">
      <el-input
        v-model="form.content"
        type="textarea"
        :rows="3"
        placeholder="分享你的想法、提问或解答…"
        maxlength="1000"
        show-word-limit
      />
      <div class="publish-bar">
        <el-upload
          action="/api/file/upload"
          :headers="uploadHeaders"
          :show-file-list="false"
          :on-success="handleImageSuccess"
          accept="image/*"
        >
          <el-button size="small" text>
            <el-icon><Picture /></el-icon> 添加图片
          </el-button>
        </el-upload>
        <div v-if="imageUrls.length" class="image-preview">
          <div v-for="(url, i) in imageUrls" :key="i" class="thumb">
            <el-image :src="url" fit="cover" class="thumb-img" :preview-src-list="imageUrls" :initial-index="i" preview-teleported />
            <el-icon class="thumb-remove" @click="removeImage(i)"><Close /></el-icon>
          </div>
        </div>
        <el-button type="primary" size="small" :loading="publishing" @click="handlePublish">发布</el-button>
      </div>
    </div>

    <el-empty v-if="posts.length === 0 && !loading" description="还没有讨论，来发第一条吧" />

    <div v-else v-loading="loading" class="post-list">
      <div v-for="post in posts" :key="post.id" class="post-card">
        <div class="post-header">
          <el-avatar :size="34" class="post-avatar">{{ (post.authorName || '?')[0] }}</el-avatar>
          <div class="post-author">
            <span class="author-name">{{ post.authorName }}</span>
            <el-tag v-if="post.authorRole === 'teacher'" type="warning" size="small" effect="plain" round>教师</el-tag>
            <span class="post-time">{{ post.createTime }}</span>
          </div>
          <el-button v-if="post.canDelete" size="small" type="danger" link @click="handleDeletePost(post)">删除</el-button>
        </div>

        <p class="post-content">{{ post.content }}</p>

        <div v-if="post.imageList?.length" class="post-images">
          <el-image
            v-for="(url, i) in post.imageList"
            :key="i"
            :src="url"
            :preview-src-list="post.imageList"
            :initial-index="i"
            fit="cover"
            preview-teleported
            class="post-img"
          />
        </div>

        <div class="post-footer">
          <div class="post-action" :class="{ active: post.liked }" @click="handleLike(post)">
            <el-icon><Star /></el-icon>
            <span>{{ post.likeCount || 0 }}</span>
          </div>
          <div class="post-action" @click="toggleComments(post)">
            <el-icon><ChatDotRound /></el-icon>
            <span>{{ post.commentCount || 0 }}</span>
          </div>
        </div>

        <div v-if="expandedId === post.id" class="comment-area">
          <div v-for="c in post.comments" :key="c.id" class="comment-item">
            <div class="comment-head">
              <span class="comment-author">{{ c.authorName }}</span>
              <el-tag v-if="c.authorRole === 'teacher'" type="warning" size="small" effect="plain" round>教师</el-tag>
              <span class="comment-time">{{ c.createTime }}</span>
              <el-button v-if="c.canDelete" size="small" type="danger" link @click="handleDeleteComment(post, c)">删除</el-button>
            </div>
            <p class="comment-content">{{ c.content }}</p>
          </div>

          <div class="comment-input">
            <el-input v-model="commentInput[post.id]" placeholder="写下你的评论…" maxlength="500" @keyup.enter="handleComment(post)" />
            <el-button type="primary" size="small" @click="handleComment(post)">发送</el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getCoursePosts, createPost, deletePost, addComment, deleteComment, toggleLike } from '../api/post'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Picture, Close, Star, ChatDotRound } from '@element-plus/icons-vue'

const route = useRoute()
const courseId = route.params.id

const posts = ref([])
const loading = ref(false)
const publishing = ref(false)
const form = ref({ content: '' })
const imageUrls = ref([])
const expandedId = ref(null)
const commentInput = reactive({})

const uploadHeaders = computed(() => ({
  Authorization: 'Bearer ' + localStorage.getItem('token')
}))

async function loadPosts() {
  loading.value = true
  try {
    const res = await getCoursePosts(courseId)
    posts.value = res.data || []
  } catch (e) {
  } finally {
    loading.value = false
  }
}

function handleImageSuccess(response) {
  if (response.code === 200) {
    imageUrls.value.push(response.data)
  }
}

function removeImage(index) {
  imageUrls.value.splice(index, 1)
}

async function handlePublish() {
  if (!form.value.content?.trim() && imageUrls.value.length === 0) {
    ElMessage.warning('请输入内容或添加图片')
    return
  }
  publishing.value = true
  try {
    await createPost({
      courseId: Number(courseId),
      content: form.value.content?.trim() || '分享图片',
      images: imageUrls.value
    })
    ElMessage.success('发布成功')
    form.value.content = ''
    imageUrls.value = []
    loadPosts()
  } catch (e) {
  } finally {
    publishing.value = false
  }
}

async function handleLike(post) {
  try {
    const res = await toggleLike(post.id)
    post.liked = res.data
    post.likeCount = Math.max(0, (post.likeCount || 0) + (res.data ? 1 : -1))
  } catch (e) {}
}

function toggleComments(post) {
  expandedId.value = expandedId.value === post.id ? null : post.id
}

async function handleComment(post) {
  const content = (commentInput[post.id] || '').trim()
  if (!content) {
    ElMessage.warning('请输入评论内容')
    return
  }
  try {
    await addComment({ postId: post.id, content })
    commentInput[post.id] = ''
    ElMessage.success('评论成功')
    loadPosts()
  } catch (e) {}
}

async function handleDeletePost(post) {
  try {
    await ElMessageBox.confirm('确认删除该帖子？', '删除帖子', { type: 'warning' })
    await deletePost(post.id)
    ElMessage.success('已删除')
    loadPosts()
  } catch (e) {}
}

async function handleDeleteComment(post, comment) {
  try {
    await ElMessageBox.confirm('确认删除该评论？', '删除评论', { type: 'warning' })
    await deleteComment(comment.id)
    ElMessage.success('已删除')
    loadPosts()
  } catch (e) {}
}

onMounted(() => loadPosts())
</script>

<style scoped>
.discussion-page {
  padding: 0;
}

.publish-card {
  padding: 18px;
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  margin-bottom: 18px;
}

.publish-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 10px;
  flex-wrap: wrap;
}

.image-preview {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.thumb {
  position: relative;
}

.thumb-img {
  width: 56px;
  height: 56px;
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.thumb-remove {
  position: absolute;
  top: -6px;
  right: -6px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--color-danger, #ef4444);
  color: #fff;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.publish-bar .el-button {
  margin-left: auto;
}

.post-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.post-card {
  padding: 20px 22px;
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  transition: transform var(--transition-normal), box-shadow var(--transition-normal);
}

.post-card:hover {
  box-shadow: var(--shadow-md);
}

.post-header {
  display: flex;
  align-items: center;
  gap: 10px;
}

.post-avatar {
  background: var(--gradient-primary);
  color: #fff;
  flex-shrink: 0;
  box-shadow: 0 4px 12px -4px rgba(99, 102, 241, 0.5);
}

.post-author {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  flex-wrap: wrap;
}

.author-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.post-time {
  font-size: 12px;
  color: var(--color-text-placeholder);
}

.post-content {
  margin: 12px 0 0;
  font-size: 14px;
  line-height: 1.7;
  color: var(--color-text-primary);
  white-space: pre-wrap;
  word-break: break-word;
}

.post-images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.post-img {
  width: 112px;
  height: 112px;
  border-radius: var(--radius-md);
  overflow: hidden;
  cursor: pointer;
}

.post-footer {
  display: flex;
  gap: 10px;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--color-border-light, #f0f0f0);
}

.post-action {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 5px 12px;
  font-size: 13px;
  color: var(--color-text-regular);
  background: var(--color-bg-page);
  border-radius: 999px;
  cursor: pointer;
  user-select: none;
  transition: color 0.2s, background 0.2s;
}

.post-action:hover {
  background: var(--color-primary-bg);
  color: var(--color-primary);
}

.post-action.active {
  color: #f59e0b;
  background: var(--color-warning-bg);
}

.comment-area {
  margin-top: 14px;
  padding: 14px 16px;
  background: var(--color-primary-soft);
  border-radius: var(--radius-md);
}

.comment-item {
  padding: 10px 0;
  border-bottom: 1px dashed var(--color-border-light, #f0f0f0);
}

.comment-item:last-child {
  border-bottom: none;
}

.comment-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.comment-author {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.comment-time {
  font-size: 12px;
  color: var(--color-text-secondary);
  flex: 1;
}

.comment-content {
  margin: 4px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-text-regular);
  word-break: break-word;
}

.comment-input {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

@media (max-width: 768px) {
  .post-card {
    padding: 16px;
  }
}
</style>