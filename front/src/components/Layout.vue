<template>
  <div class="app-layout">
    <aside class="sidebar" :class="{ collapsed: sidebarCollapsed }">
      <div class="sidebar-logo">
        <div class="logo-icon">AI</div>
        <span v-if="!sidebarCollapsed" class="logo-text">智慧课堂</span>
      </div>

      <nav class="sidebar-nav">
        <router-link to="/" class="nav-item" :class="{ active: route.path === '/' }">
          <el-icon><HomeFilled /></el-icon>
          <span v-if="!sidebarCollapsed">首页</span>
        </router-link>

        <router-link to="/course" class="nav-item" :class="{ active: route.path.startsWith('/course') }">
          <el-icon><Reading /></el-icon>
          <span v-if="!sidebarCollapsed">我的课程</span>
        </router-link>

        <router-link v-if="userStore.userInfo?.role === 'teacher'" to="/quiz-bank" class="nav-item" :class="{ active: route.path === '/quiz-bank' }">
          <el-icon><Tickets /></el-icon>
          <span v-if="!sidebarCollapsed">测验题库</span>
        </router-link>

        <router-link v-if="userStore.userInfo?.role === 'student'" to="/my-checkin" class="nav-item" :class="{ active: route.path === '/my-checkin' }">
          <el-icon><CircleCheck /></el-icon>
          <span v-if="!sidebarCollapsed">我的签到</span>
        </router-link>

        <router-link to="/chat" class="nav-item" :class="{ active: route.path === '/chat' }">
          <el-icon><ChatDotRound /></el-icon>
          <span v-if="!sidebarCollapsed">AI 助手</span>
        </router-link>

        <router-link v-if="userStore.userInfo?.role === 'admin'" to="/admin/review" class="nav-item" :class="{ active: route.path === '/admin/review' }">
          <el-icon><UserFilled /></el-icon>
          <span v-if="!sidebarCollapsed">用户管理</span>
        </router-link>

        <router-link v-if="userStore.userInfo?.status === 0" to="/appeal" class="nav-item" :class="{ active: route.path === '/appeal' }">
          <el-icon><Warning /></el-icon>
          <span v-if="!sidebarCollapsed">账号申诉</span>
        </router-link>
      </nav>

      <div class="sidebar-footer">
        <div class="nav-item collapse-btn" @click="sidebarCollapsed = !sidebarCollapsed">
          <el-icon><Fold v-if="!sidebarCollapsed" /><Expand v-else /></el-icon>
          <span v-if="!sidebarCollapsed">收起菜单</span>
        </div>
      </div>
    </aside>

    <div class="main-area">
      <header class="top-header">
        <div class="header-left">
          <el-icon class="mobile-menu-btn" @click="mobileMenuVisible = true">
            <Expand />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-for="item in breadcrumbs" :key="item.path" :to="item.path ? { path: item.path } : undefined">
              {{ item.title }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="header-right">
          <el-dropdown @command="handleCommand" trigger="click">
            <div class="user-avatar-area">
              <el-avatar :size="32" class="user-avatar">
                {{ (userStore.userInfo?.nickname || userStore.userInfo?.username || '?')[0] }}
              </el-avatar>
              <div class="user-meta">
                <span class="user-name">{{ userStore.userInfo?.nickname || userStore.userInfo?.username }}</span>
                <el-tag :type="roleTagType" size="small" effect="plain" round>{{ roleLabel }}</el-tag>
              </div>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">
                  <el-icon><SwitchButton /></el-icon>
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main ref="contentArea" class="content-area">
        <router-view v-slot="{ Component }">
          <transition name="fade-slide" mode="out-in">
            <component :is="Component" :key="route.matched[1]?.path ?? route.path" />
          </transition>
        </router-view>
      </main>
    </div>

    <el-drawer v-model="mobileMenuVisible" direction="ltr" size="240px" :with-header="false" class="mobile-drawer">
      <div class="drawer-logo">
        <div class="logo-icon">AI</div>
        <span class="logo-text">智慧课堂</span>
      </div>
      <nav class="drawer-nav">
        <router-link to="/" class="drawer-item" @click="mobileMenuVisible = false">
          <el-icon><HomeFilled /></el-icon> 首页
        </router-link>
        <router-link to="/course" class="drawer-item" @click="mobileMenuVisible = false">
          <el-icon><Reading /></el-icon> 我的课程
        </router-link>
        <router-link v-if="userStore.userInfo?.role === 'teacher'" to="/quiz-bank" class="drawer-item" @click="mobileMenuVisible = false">
          <el-icon><Tickets /></el-icon> 测验题库
        </router-link>
        <router-link v-if="userStore.userInfo?.role === 'student'" to="/my-checkin" class="drawer-item" @click="mobileMenuVisible = false">
          <el-icon><CircleCheck /></el-icon> 我的签到
        </router-link>
        <router-link to="/chat" class="drawer-item" @click="mobileMenuVisible = false">
          <el-icon><ChatDotRound /></el-icon> AI 助手
        </router-link>
        <router-link v-if="userStore.userInfo?.role === 'admin'" to="/admin/review" class="drawer-item" @click="mobileMenuVisible = false">
          <el-icon><UserFilled /></el-icon> 用户管理
        </router-link>
        <router-link v-if="userStore.userInfo?.status === 0" to="/appeal" class="drawer-item" @click="mobileMenuVisible = false">
          <el-icon><Warning /></el-icon> 账号申诉
        </router-link>
      </nav>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import {
  HomeFilled, Reading, CircleCheck, ChatDotRound, UserFilled,
  Warning, Fold, Expand, SwitchButton, Tickets
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const sidebarCollapsed = ref(false)
const mobileMenuVisible = ref(false)

const roleLabel = computed(() => {
  const map = { student: '学生', teacher: '教师', admin: '管理员' }
  return map[userStore.userInfo?.role] || '用户'
})

const roleTagType = computed(() => {
  const map = { student: 'info', teacher: 'warning', admin: 'danger' }
  return map[userStore.userInfo?.role] || 'info'
})

const breadcrumbs = computed(() => {
  const list = []

  if (route.meta?.parent) {
    list.push({ ...route.meta.parent })
  }

  route.matched.forEach((record, index) => {
    if (index === 0) return
    const title = record.meta?.title
    if (!title) return
    list.push({
      title,
      path: record.meta?.crumbPath ?? (record.path.includes(':') ? '' : record.path)
    })
  })

  return list
})

const contentArea = ref(null)

watch(
  () => route.fullPath,
  async () => {
    await nextTick()
    contentArea.value?.scrollTo({ top: 0 })
  }
)

function handleCommand(cmd) {
  if (cmd === 'logout') {
    userStore.logout()
  }
}
</script>

<style scoped>
.app-layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

.sidebar {
  width: var(--sidebar-width);
  background: linear-gradient(180deg, #1c2138 0%, #161a2d 55%, #121528 100%);
  display: flex;
  flex-direction: column;
  transition: width var(--transition-normal);
  overflow: hidden;
  flex-shrink: 0;
  z-index: 100;
  box-shadow: 4px 0 24px -12px rgba(15, 23, 42, 0.5);
}

.sidebar.collapsed {
  width: 64px;
}

.sidebar-logo {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 20px 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.07);
  min-height: 72px;
}

.logo-icon {
  width: 38px;
  height: 38px;
  background: var(--gradient-primary);
  border-radius: 11px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 800;
  letter-spacing: 0.5px;
  color: #fff;
  flex-shrink: 0;
  box-shadow: 0 6px 18px -6px rgba(99, 102, 241, 0.9);
}

.logo-text {
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 0.4px;
  color: #f1f5f9;
  white-space: nowrap;
}

.sidebar-nav {
  flex: 1;
  padding: 12px 8px;
  overflow-y: auto;
}

.nav-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 11px 12px;
  margin-bottom: 6px;
  border-radius: 10px;
  color: #9aa6c4;
  font-size: 14px;
  cursor: pointer;
  transition: all var(--transition-fast);
  text-decoration: none;
  white-space: nowrap;
}

.nav-item .el-icon {
  font-size: 18px;
  flex-shrink: 0;
}

.nav-item:hover {
  background: rgba(255, 255, 255, 0.06);
  color: #e2e8f0;
}

.nav-item.active {
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.95) 0%, rgba(129, 140, 248, 0.9) 100%);
  color: #fff;
  box-shadow: 0 8px 20px -10px rgba(99, 102, 241, 0.9);
}

.nav-item.active::before {
  content: '';
  position: absolute;
  left: -8px;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 18px;
  border-radius: 999px;
  background: #c7d2fe;
}

.sidebar-footer {
  padding: 8px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}

.collapse-btn {
  color: #64748b;
}

.collapse-btn:hover {
  color: #94a3b8;
}

.main-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
}

.top-header {
  height: var(--header-height);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: saturate(180%) blur(12px);
  border-bottom: 1px solid var(--color-border);
  flex-shrink: 0;
  z-index: 10;
}

.header-left {
  display: flex;
  align-items: center;
}

.header-right {
  display: flex;
  align-items: center;
}

.user-avatar-area {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  padding: 5px 10px;
  border-radius: 12px;
  transition: background var(--transition-fast);
}

.user-avatar-area:hover {
  background: var(--color-primary-soft);
}

.user-avatar {
  background: var(--gradient-primary);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  box-shadow: 0 4px 12px -6px rgba(99, 102, 241, 0.8);
}

.user-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-name {
  font-size: 14px;
  color: var(--color-text-primary);
  font-weight: 500;
}

.content-area {
  flex: 1;
  overflow-y: auto;
  background: var(--color-bg-page);
}

.mobile-menu-btn {
  display: none;
  font-size: 20px;
  color: var(--color-text-regular);
  cursor: pointer;
  margin-right: 12px;
}

.drawer-logo {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 4px 8px 16px;
  border-bottom: 1px solid var(--color-border);
  margin-bottom: 12px;
}

.drawer-nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.drawer-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 12px;
  border-radius: var(--radius-sm);
  color: var(--color-text-regular);
  font-size: 14px;
  text-decoration: none;
  transition: background var(--transition-fast);
}

.drawer-item:hover {
  background: var(--color-primary-bg);
  color: var(--color-primary);
}

.drawer-item.router-link-active {
  background: var(--color-primary);
  color: #fff;
}

@media (max-width: 768px) {
  .sidebar {
    display: none;
  }

  .mobile-menu-btn {
    display: flex;
    align-items: center;
  }

  .top-header {
    padding: 0 16px;
  }

  .user-meta {
    display: none;
  }
}
</style>