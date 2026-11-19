<template>
  <div class="auth-container">
    <div class="auth-left">
      <div class="brand-area">
        <div class="brand-logo">AI</div>
        <h1>智慧课堂</h1>
        <p>AI 驱动的智慧教学管理平台</p>
      </div>
      <div class="feature-list">
        <div class="feature-item">
          <el-icon :size="20"><CircleCheck /></el-icon>
          <span>智能签到，多种方式</span>
        </div>
        <div class="feature-item">
          <el-icon :size="20"><CircleCheck /></el-icon>
          <span>作业管理，在线批改</span>
        </div>
        <div class="feature-item">
          <el-icon :size="20"><CircleCheck /></el-icon>
          <span>成绩分析，一键导出</span>
        </div>
        <div class="feature-item">
          <el-icon :size="20"><CircleCheck /></el-icon>
          <span>AI 助手，随时答疑</span>
        </div>
      </div>
    </div>

    <div class="auth-right">
      <div class="auth-card">
        <h2 class="auth-title">欢迎登录</h2>
        <p class="auth-subtitle">请输入您的账号信息</p>

        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @keyup.enter="handleLogin">
          <el-form-item label="用户名" prop="username">
            <el-input v-model="form.username" placeholder="请输入用户名" prefix-icon="User" size="large" />
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input v-model="form.password" type="password" placeholder="请输入密码" prefix-icon="Lock" show-password size="large" />
          </el-form-item>

          <el-form-item>
            <el-button type="primary" :loading="loading" style="width: 100%" size="large" @click="handleLogin">
              登 录
            </el-button>
          </el-form-item>
        </el-form>

        <div class="auth-footer">
          还没有账号？
          <router-link to="/register" class="auth-link">立即注册</router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { ElMessage } from 'element-plus'
import { CircleCheck } from '@element-plus/icons-vue'

const router = useRouter()
const userStore = useUserStore()

const formRef = ref(null)
const loading = ref(false)

const form = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await userStore.login({ username: form.username, password: form.password })
    ElMessage.success('登录成功')
    router.push('/')
  } catch {
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-container {
  min-height: 100vh;
  display: flex;
}

.auth-left {
  flex: 1;
  background:
    radial-gradient(circle at 85% 15%, rgba(255, 255, 255, 0.16) 0%, transparent 45%),
    radial-gradient(circle at 10% 85%, rgba(255, 255, 255, 0.1) 0%, transparent 40%),
    linear-gradient(135deg, #6366f1 0%, #4f46e5 50%, #3730a3 100%);
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 60px;
  color: #fff;
  position: relative;
  overflow: hidden;
}

.auth-left::before {
  content: '';
  position: absolute;
  width: 300px;
  height: 300px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.05);
  right: -80px;
  bottom: -80px;
}

.auth-left::after {
  content: '';
  position: absolute;
  width: 200px;
  height: 200px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.05);
  left: -40px;
  top: -40px;
}

.brand-area {
  position: relative;
  z-index: 1;
}

.brand-logo {
  width: 58px;
  height: 58px;
  background: rgba(255, 255, 255, 0.16);
  border: 1px solid rgba(255, 255, 255, 0.25);
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  font-weight: 800;
  letter-spacing: 0.5px;
  margin-bottom: 24px;
  backdrop-filter: blur(10px);
  box-shadow: 0 10px 28px -12px rgba(0, 0, 0, 0.55);
}

.brand-area h1 {
  font-size: 36px;
  font-weight: 700;
  margin-bottom: 8px;
}

.brand-area p {
  font-size: 16px;
  opacity: 0.7;
}

.feature-list {
  margin-top: 48px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  position: relative;
  z-index: 1;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 15px;
  opacity: 0.85;
}

.auth-right {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-bg-page);
  padding: 40px;
}

.auth-card {
  width: 100%;
  max-width: 420px;
  padding: 40px;
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-xl);
  position: relative;
  overflow: hidden;
}

.auth-card::before {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  height: 4px;
  background: var(--gradient-primary);
}

.auth-title {
  font-size: 24px;
  font-weight: 700;
  color: var(--color-text-primary);
  margin: 0 0 4px;
}

.auth-subtitle {
  font-size: 14px;
  color: var(--color-text-secondary);
  margin: 0 0 28px;
}

.auth-footer {
  text-align: center;
  font-size: 14px;
  color: var(--color-text-secondary);
  margin-top: 8px;
}

.auth-link {
  color: var(--color-primary);
  text-decoration: none;
  font-weight: 500;
}

.auth-link:hover {
  text-decoration: underline;
}

@media (max-width: 768px) {
  .auth-container {
    flex-direction: column;
  }

  .auth-left {
    padding: 40px 24px;
    min-height: auto;
  }

  .brand-area h1 {
    font-size: 28px;
  }

  .feature-list {
    display: none;
  }

  .auth-right {
    padding: 24px;
  }

  .auth-card {
    padding: 28px;
  }
}
</style>