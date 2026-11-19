const api = require('../../utils/request')
const { getMyCheckins, getMyCourses, getCourseHomework, getMyHomework, joinCourse } = require('../../utils/request')
const checkinUtil = require('../../utils/checkin')

function shortTime(value) {
  if (!value) return ''
  const text = String(value).replace('T', ' ')
  return text.length >= 16 ? text.slice(5, 16) : text
}

function parseCourseCode(text) {
  if (!text) return ''
  const match = String(text).match(/[A-Za-z0-9]{6}/)
  return match ? match[0].toUpperCase() : ''
}

function buildGreetText(role, name) {
  const hour = new Date().getHours()
  let greet = '晚上好'
  if (hour < 5) greet = '夜深了'
  else if (hour < 9) greet = '早上好'
  else if (hour < 12) greet = '上午好'
  else if (hour < 14) greet = '中午好'
  else if (hour < 18) greet = '下午好'
  const suffix = role === 'student' ? '同学' : role === 'teacher' ? '老师' : ''
  return `${greet}！${name}${suffix}`
}

Page({
  data: {
    userInfo: null,
    roleLabel: '',
    avatarLetter: '',
    greetText: '',
    stats: { checkinCount: 0, doneCount: 0, totalCount: 0 },
    teacherStats: { studentCount: 0, checkinCount: 0, activeCount: 0 },
    courses: [],
    filteredCourses: [],
    keyword: '',
    joining: false,
    scanning: false,
    checking: false,
    todoList: [],
    recentList: []
  },

  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({ selected: 'home' })
    }
    const userInfo = wx.getStorageSync('userInfo')
    if (!userInfo) {
      wx.reLaunch({ url: '/pages/login/login' })
      return
    }
    const roleMap = { student: '学生', teacher: '教师', admin: '管理员' }
    const name = userInfo.nickname || userInfo.username || ''
    this.setData({
      userInfo,
      roleLabel: roleMap[userInfo.role] || '未知',
      avatarLetter: name.charAt(0).toUpperCase(),
      greetText: buildGreetText(userInfo.role, name)
    })
    this.loadStats()
  },

  async loadStats() {
    try {
      const courseRes = await getMyCourses()
      const courses = courseRes.data || []
      const role = this.data.userInfo.role

      if (role === 'student') {
        let checkinCount = 0, doneCount = 0, totalCount = 0
        const [checkinRes, hwGroups] = await Promise.all([
          getMyCheckins(),
          Promise.all(courses.map(c => getMyHomework(c.id).catch(() => ({ data: [] }))))
        ])
        const checkins = checkinRes.data || []
        const todoList = []
        checkins.forEach(c => {
          if (c.status === 1) checkinCount++
          else if (c.status === 2) doneCount++
          totalCount++
          if (c.status === 1 && !c.checked) {
            todoList.push({
              key: 'ck-' + c.id,
              type: 'checkin',
              tag: '签到',
              title: c.title,
              courseId: c.courseId,
              courseName: c.courseName || '',
              desc: '签到进行中',
              createTime: c.createTime || ''
            })
          }
        })
        hwGroups.forEach((res, idx) => {
          const course = courses[idx]
          const list = (res && res.data) || []
          list.forEach(hw => {
            if (hw.expired) return
            todoList.push({
              key: 'hw-' + hw.id,
              type: 'homework',
              tag: '作业',
              title: hw.title,
              courseId: hw.courseId || course.id,
              courseName: course.name,
              desc: hw.deadline ? '截止 ' + shortTime(hw.deadline) : '待提交',
              createTime: hw.createTime || ''
            })
          })
        })
        todoList.sort((a, b) => (b.createTime || '').localeCompare(a.createTime || ''))
        this.setData({
          stats: { checkinCount, doneCount, totalCount },
          courses,
          filteredCourses: this.matchCourses(courses, this.data.keyword),
          todoList: todoList.slice(0, 5)
        })
      } else if (role === 'teacher') {
        let studentCount = 0, checkinCount = 0, activeCount = 0
        const recentList = []
        const groups = await Promise.all(courses.map(async c => {
          const [ckRes, hwRes] = await Promise.all([
            api.getCourseCheckins(c.id).catch(() => ({ data: [] })),
            getCourseHomework(c.id).catch(() => ({ data: [] }))
          ])
          return {
            course: c,
            checkins: (ckRes && ckRes.data) || [],
            homeworks: (hwRes && hwRes.data) || []
          }
        }))
        groups.forEach(g => {
          studentCount += (g.course.studentCount || 0)
          g.checkins.forEach(ck => {
            checkinCount++
            if (ck.status === 1) activeCount++
            recentList.push({
              key: 'ck-' + ck.id,
              type: 'checkin',
              title: ck.title,
              courseName: g.course.name,
              active: ck.status === 1,
              statusText: ck.status === 1 ? '进行中' : '已结束',
              createTime: ck.createTime || ''
            })
          })
          g.homeworks.forEach(hw => {
            recentList.push({
              key: 'hw-' + hw.id,
              type: 'homework',
              title: hw.title,
              courseName: g.course.name,
              active: !hw.expired,
              statusText: hw.expired ? '已截止' : `提交 ${hw.submitCount}/${hw.totalStudents}`,
              createTime: hw.createTime || ''
            })
          })
        })
        recentList.sort((a, b) => (b.createTime || '').localeCompare(a.createTime || ''))
        this.setData({
          teacherStats: { studentCount, checkinCount, activeCount },
          recentList: recentList.slice(0, 5).map(item => ({ ...item, timeText: shortTime(item.createTime) }))
        })
      } else {
        this.setData({
          teacherStats: { studentCount: 0, checkinCount: 0, activeCount: 0 }
        })
      }
    } catch (e) { /* handled */ }
  },

  goCourse() {
    wx.switchTab({ url: '/pages/course/course' })
  },

  matchCourses(list, keyword) {
    const kw = (keyword || '').trim().toUpperCase()
    if (!kw) return list || []
    return (list || []).filter(c =>
      (c.name || '').toUpperCase().indexOf(kw) > -1 ||
      (c.code || '').toUpperCase().indexOf(kw) > -1
    )
  },

  onSearchInput(e) {
    const keyword = e.detail.value
    this.setData({
      keyword,
      filteredCourses: this.matchCourses(this.data.courses, keyword)
    })
  },

  clearKeyword() {
    this.setData({ keyword: '', filteredCourses: this.data.courses })
  },

  goCourseDetail(e) {
    const id = e.currentTarget.dataset.id
    wx.navigateTo({ url: `/pages/course-detail/course-detail?id=${id}` })
  },

  goTodo(e) {
    const type = e.currentTarget.dataset.type
    const courseId = e.currentTarget.dataset.course
    if (type === 'checkin') {
      const role = this.data.userInfo ? this.data.userInfo.role : 'student'
      if (role === 'teacher') {
        wx.switchTab({ url: '/pages/teacher-checkin/teacher-checkin' })
      } else {
        wx.navigateTo({ url: '/pages/my-checkin/my-checkin' })
      }
      return
    }
    if (courseId) {
      wx.navigateTo({ url: `/pages/homework/homework?id=${courseId}` })
    }
  },

  joinByKeyword() {
    this.joinCourseByCode((this.data.keyword || '').trim().toUpperCase())
  },

  scanJoinCourse() {
    wx.scanCode({
      onlyFromCamera: false,
      scanType: ['qrCode'],
      success: (res) => {
        const code = parseCourseCode(res.result)
        if (!code) {
          wx.showToast({ title: '未识别到课程码', icon: 'none' })
          return
        }
        this.joinCourseByCode(code)
      },
      fail: () => {}
    })
  },

  async joinCourseByCode(code) {
    if (!code) {
      wx.showToast({ title: '请输入课程码', icon: 'none' })
      return
    }
    if (this.data.joining) return
    this.setData({ joining: true })
    try {
      await joinCourse(code)
      wx.showToast({ title: '加入成功', icon: 'success' })
      this.setData({ joining: false, keyword: '' })
      this.loadStats()
    } catch (e) {
      this.setData({ joining: false })
    }
  },

  goAdmin() {
    wx.navigateTo({ url: '/pages/admin-review/admin-review' })
  },

  goTeacherCheckin() {
    wx.switchTab({ url: '/pages/teacher-checkin/teacher-checkin' })
  },

  async goScanCheckin() {
    if (this.data.scanning) return
    this.setData({ scanning: true })
    const result = await checkinUtil.fetchClassify()
    if (result.state === checkinUtil.STATE.NETWORK) {
      this.setData({ scanning: false })
      wx.showToast({ title: '网络异常，请稍后重试', icon: 'none' })
      return
    }
    if (result.state === checkinUtil.STATE.NO_ACTIVE) {
      this.setData({ scanning: false })
      checkinUtil.showNoActive()
      return
    }
    if (result.activeQr === 0) {
      this.setData({ scanning: false })
      checkinUtil.showMethodHint({
        title: '当前签到为数字签到',
        content: '老师发布的是数字签到，请直接输入 6 位签到码完成',
        confirmText: '去输码',
        onConfirm: () => checkinUtil.openNumberInput(result, () => this.loadStats())
      })
      return
    }
    wx.scanCode({
      onlyFromCamera: false,
      scanType: ['qrCode'],
      success: async (res) => {
        const payload = checkinUtil.parseQrPayload(res.result)
        if (!payload) {
          wx.showToast({ title: '未识别到内容', icon: 'none' })
          this.setData({ scanning: false })
          return
        }
        if (payload.kind === 'live') {
          this.setData({ scanning: false })
          wx.navigateTo({ url: `/pages/live/live?sessionId=${payload.sessionId}&code=${payload.code}` })
          return
        }
        if (payload.kind === 'code') {
          wx.showLoading({ title: '正在签到', mask: true })
          try {
            const status = await checkinUtil.submitByCodeWithLocation(payload.code, 'number')
            wx.hideLoading()
            this.setData({ scanning: false })
            checkinUtil.showCheckinSuccess(status)
            this.loadStats()
          } catch (e) {
            wx.hideLoading()
            this.setData({ scanning: false })
            checkinUtil.showCheckinError(e, {
              onRetryCode: () => checkinUtil.openNumberInput(result, () => this.loadStats()),
              onRefresh: () => this.goScanCheckin()
            })
          }
          return
        }
        if (payload.kind === 'checkin') {
          const target = checkinUtil.findById(result.list, payload.checkinId)
          if (!target || !checkinUtil.isActive(target)) {
            this.setData({ scanning: false })
            checkinUtil.showCheckinError(new Error('签到已结束'), {
              onRefresh: () => this.goScanCheckin()
            })
            return
          }
          if (!checkinUtil.isQrType(target.type)) {
            this.setData({ scanning: false })
            checkinUtil.showMethodHint({
              title: '该签到仅支持数字签到',
              content: '老师发布的是数字签到，请直接输入 6 位签到码完成',
              confirmText: '去输码',
              onConfirm: () => checkinUtil.openNumberInput(result, () => this.loadStats())
            })
            return
          }
          wx.showLoading({ title: '正在签到', mask: true })
          try {
            const status = await checkinUtil.submitWithLocation(payload.checkinId, payload.code, 'qr', payload.token)
            wx.hideLoading()
            this.setData({ scanning: false })
            checkinUtil.showCheckinSuccess(status)
            this.loadStats()
          } catch (e) {
            wx.hideLoading()
            this.setData({ scanning: false })
            checkinUtil.showCheckinError(e, {
              onRetryCode: () => checkinUtil.openNumberInput(result, () => this.loadStats()),
              onScan: () => this.goScanCheckin(),
              onRefresh: () => this.goScanCheckin()
            })
          }
          return
        }
        this.setData({ scanning: false })
        checkinUtil.showCheckinError(new Error('不是有效的签到二维码'), {
          onRefresh: () => this.goScanCheckin()
        })
      },
      fail: () => {
        this.setData({ scanning: false })
      }
    })
  },

  async goMyCheckin() {
    if (this.data.checking) return
    this.setData({ checking: true })
    const result = await checkinUtil.fetchClassify()
    this.setData({ checking: false })
    if (result.state === checkinUtil.STATE.NETWORK) {
      wx.showToast({ title: '网络异常，请稍后重试', icon: 'none' })
      return
    }
    if (result.state === checkinUtil.STATE.NO_ACTIVE) {
      checkinUtil.showNoActive()
      return
    }
    if (result.activeNum === 0) {
      checkinUtil.showMethodHint({
        title: '当前签到需扫码',
        content: '老师发布的是二维码签到，请扫老师出示的二维码完成',
        confirmText: '去扫码',
        onConfirm: () => this.goScanCheckin()
      })
      return
    }
    checkinUtil.openNumberInput(result, () => this.loadStats())
  },

  goCheckinList() {
    wx.navigateTo({ url: '/pages/my-checkin/my-checkin' })
  },

  goChat() {
    wx.navigateTo({ url: '/pages/chat/chat' })
  }
})