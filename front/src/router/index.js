import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import Layout from '../components/Layout.vue'
import CourseShell from '../components/CourseShell.vue'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('../views/Register.vue'),
    meta: { title: '注册' }
  },
  {
    path: '/',
    component: Layout,
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        name: 'Home',
        component: () => import('../views/Home.vue'),
        meta: { title: '首页' }
      },
      {
        path: 'course',
        name: 'CourseList',
        component: () => import('../views/CourseList.vue'),
        meta: { title: '我的课程' }
      },
      {
        path: 'course/:id',
        component: CourseShell,
        meta: { title: '我的课程', crumbPath: '/course' },
        children: [
          {
            path: '',
            name: 'CourseDetail',
            component: () => import('../views/CourseDetail.vue'),
            meta: { title: '课程概览' }
          },
          {
            path: 'checkin',
            name: 'CheckinList',
            component: () => import('../views/CheckinList.vue'),
            meta: { title: '签到管理', roles: ['teacher'] }
          },
          {
            path: 'live',
            name: 'LiveClass',
            component: () => import('../views/LiveClass.vue'),
            meta: { title: '在线课堂', roles: ['teacher'] }
          },
          {
            path: 'homework',
            name: 'HomeworkList',
            component: () => import('../views/HomeworkList.vue'),
            meta: { title: '作业管理' }
          },
          {
            path: 'material',
            name: 'MaterialList',
            component: () => import('../views/MaterialList.vue'),
            meta: { title: '课件资源' }
          },
          {
            path: 'notice',
            name: 'NoticeList',
            component: () => import('../views/NoticeList.vue'),
            meta: { title: '课程公告' }
          },
          {
            path: 'discussion',
            name: 'Discussion',
            component: () => import('../views/Discussion.vue'),
            meta: { title: '班级讨论' }
          },
          {
            path: 'quiz',
            name: 'QuizList',
            component: () => import('../views/QuizList.vue'),
            meta: { title: '测验管理' }
          },
          {
            path: 'score',
            name: 'ScoreManage',
            component: () => import('../views/ScoreManage.vue'),
            meta: { title: '成绩管理', roles: ['teacher'] }
          },
          {
            path: 'my-score',
            name: 'MyScore',
            component: () => import('../views/MyScore.vue'),
            meta: { title: '我的成绩', roles: ['student'] }
          },
          {
            path: 'my-checkin',
            name: 'CourseMyCheckin',
            component: () => import('../views/MyCheckin.vue'),
            meta: { title: '我的签到', roles: ['student'] }
          }
        ]
      },
      {
        path: 'quiz-bank',
        name: 'QuizBank',
        component: () => import('../views/QuizBank.vue'),
        meta: { title: '测验题库', roles: ['teacher'] }
      },
      {
        path: 'admin/review',
        name: 'AdminReview',
        component: () => import('../views/AdminReview.vue'),
        meta: { title: '用户管理', roles: ['admin'] }
      },
      {
        path: 'appeal',
        name: 'Appeal',
        component: () => import('../views/Appeal.vue'),
        meta: { title: '账号申诉' }
      },
      {
        path: 'checkin-detail/:id',
        name: 'CheckinDetail',
        component: () => import('../views/CheckinDetail.vue'),
        meta: { title: '签到详情', parent: { title: '我的课程', path: '/course' } }
      },
      {
        path: 'my-checkin',
        name: 'MyCheckin',
        component: () => import('../views/MyCheckin.vue'),
        meta: { title: '我的签到', roles: ['student'] }
      },
      {
        path: 'homework-detail/:id',
        name: 'HomeworkDetail',
        component: () => import('../views/HomeworkDetail.vue'),
        meta: { title: '作业详情', roles: ['teacher'], parent: { title: '我的课程', path: '/course' } }
      },
      {
        path: 'submit-homework/:id',
        name: 'SubmitHomework',
        component: () => import('../views/SubmitHomework.vue'),
        meta: { title: '提交作业', roles: ['student'], parent: { title: '我的课程', path: '/course' } }
      },
      {
        path: 'chat',
        name: 'Chat',
        component: () => import('../views/ChatView.vue'),
        meta: { title: 'AI 助手' }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('../views/NotFound.vue'),
    meta: { title: '页面未找到' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) return savedPosition
    return { top: 0 }
  }
})

function readUserInfo() {
  try {
    return JSON.parse(localStorage.getItem('userInfo') || 'null')
  } catch (e) {
    return null
  }
}

router.beforeEach((to, from, next) => {
  document.title = to.meta.title ? `${to.meta.title} - 智慧课堂` : '智慧课堂'
  const token = localStorage.getItem('token')
  if (to.meta.requiresAuth && !token) {
    next('/login')
    return
  }
  if ((to.path === '/login' || to.path === '/register') && token) {
    next('/')
    return
  }
  const roles = to.meta.roles
  if (roles && roles.length > 0) {
    const user = readUserInfo()
    if (user && user.role && !roles.includes(user.role)) {
      ElMessage.warning('当前账号无权访问该页面')
      next('/')
      return
    }
  }
  next()
})

export default router