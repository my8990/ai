const { getActiveLives, getMyNotices, getMyAllHomework, getMyAllQuizzes } = require('../../utils/request')
const checkinUtil = require('../../utils/checkin')

Page({
  data: {
    role: '',
    tabs: [
      { key: 'live', label: '课堂' },
      { key: 'notice', label: '公告' },
      { key: 'homework', label: '作业' },
      { key: 'test', label: '测试' }
    ],
    activeTab: 'live',
    sessions: [],
    notices: [],
    homeworks: [],
    quizzes: [],
    loading: false
  },

  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({ selected: 'feed' })
    }
    const userInfo = wx.getStorageSync('userInfo')
    if (!userInfo) {
      wx.reLaunch({ url: '/pages/login/login' })
      return
    }
    this.setData({ role: userInfo.role })
    this.loadActive()
  },

  onPullDownRefresh() {
    const tab = this.data.activeTab
    let p
    if (tab === 'live') p = this.loadActive()
    else if (tab === 'notice') p = this.loadNotices()
    else if (tab === 'homework') p = this.loadHomeworks()
    else if (tab === 'test') p = this.loadQuizzes()
    else p = Promise.resolve()
    p.then(() => wx.stopPullDownRefresh())
  },

  switchTab(e) {
    const key = e.currentTarget.dataset.key
    if (key === this.data.activeTab) return
    const tabDef = this.data.tabs.find(t => t.key === key)
    if (tabDef && tabDef.disabled) {
      wx.showToast({ title: '功能开发中', icon: 'none' })
      return
    }
    this.setData({ activeTab: key })
    if (key === 'live') this.loadActive()
    if (key === 'notice') this.loadNotices()
    if (key === 'homework') this.loadHomeworks()
    if (key === 'test') this.loadQuizzes()
  },

  async loadActive() {
    if (this.data.loading) return
    this.setData({ loading: true })
    try {
      const res = await getActiveLives()
      const list = res.data || []
      const sessions = list.map(item => Object.assign({}, item, {
        checked: !!item.checked
      }))
      this.setData({ sessions, loading: false })
    } catch (e) {
      this.setData({ sessions: [], loading: false })
    }
  },

  async loadNotices() {
    this.setData({ loading: true })
    try {
      const res = await getMyNotices()
      const notices = (res.data || []).map(item => {
        item.timeText = (item.createTime || '').replace('T', ' ').slice(0, 16)
        if (item.type === 1) {
          item.typeText = '课件发布'
          item.typeClass = 'auto'
        } else if (item.type === 3) {
          item.typeText = '课堂讨论'
          item.typeClass = 'discussion'
        } else if (item.type === 4) {
          item.typeText = '课堂测验'
          item.typeClass = 'quiz'
        } else {
          item.typeText = '教师公告'
          item.typeClass = 'teacher'
        }
        return item
      })
      this.setData({ notices })
    } catch (e) {
      this.setData({ notices: [] })
    } finally {
      this.setData({ loading: false })
    }
  },

  async loadHomeworks() {
    this.setData({ loading: true })
    try {
      const res = await getMyAllHomework()
      const homeworks = (res.data || []).map(hw => {
        hw.timeText = (hw.createTime || '').replace('T', ' ').slice(0, 16)
        hw.deadlineText = hw.deadline ? hw.deadline.replace('T', ' ').slice(0, 16) : '无截止'
        hw.statusTag = this._hwStatus(hw)
        hw.statusClass = this._hwStatusClass(hw)
        return hw
      })
      this.setData({ homeworks })
    } catch (e) {
      this.setData({ homeworks: [] })
    } finally {
      this.setData({ loading: false })
    }
  },

  _hwStatus(hw) {
    if (hw.submitted && hw.graded) return '已批改'
    if (hw.submitted && hw.isLate) return '迟交已提交'
    if (hw.submitted) return '已提交'
    return '待提交'
  },

  _hwStatusClass(hw) {
    if (hw.submitted && hw.graded) return 'graded'
    if (hw.submitted) return 'submitted'
    return 'pending'
  },

  async loadQuizzes() {
    this.setData({ loading: true })
    try {
      const res = await getMyAllQuizzes()
      const quizzes = (res.data || []).map(q => Object.assign({}, q, {
        endText: q.endTime ? q.endTime.replace('T', ' ').slice(0, 16) : '不限',
        statusClass: this._quizStatusClass(q)
      }))
      this.setData({ quizzes })
    } catch (e) {
      this.setData({ quizzes: [] })
    } finally {
      this.setData({ loading: false })
    }
  },

  _quizStatusClass(q) {
    if (q.taken) return 'done'
    if (q.statusText === '已结束') return 'ended'
    if (q.statusText === '未开始') return 'waiting'
    if (q.statusText === '草稿' || q.statusText === '已发布' || q.statusText === '已就绪') return 'published'
    return 'active'
  },

  goQuiz(e) {
    const ds = e.currentTarget.dataset
    const id = Number(ds.id)
    if (!id) return
    if (this.data.role === 'teacher') {
      if (!ds.courseId) {
        wx.showToast({ title: '请在电脑端「测验题库」管理', icon: 'none' })
        return
      }
      wx.navigateTo({ url: `/pages/quiz/quiz?id=${ds.courseId}` })
      return
    }
    const targetId = id
    const assignmentId = Number(ds.assignmentId) || ''
    if (ds.taken) {
      wx.navigateTo({ url: `/pages/quiz-result/quiz-result?quizId=${targetId}&assignmentId=${assignmentId}` })
      return
    }
    if (!ds.canTake) {
      wx.showToast({ title: ds.statusText || '暂不可作答', icon: 'none' })
      return
    }
    wx.navigateTo({ url: `/pages/quiz-take/quiz-take?id=${targetId}&assignmentId=${assignmentId}` })
  },

  goCourse(e) {
    const { courseId } = e.currentTarget.dataset
    if (!courseId) return
    wx.navigateTo({ url: `/pages/course-detail/course-detail?id=${courseId}` })
  },

  goNoticeAction(e) {
    const { type, courseId } = e.currentTarget.dataset
    if (!courseId) return
    if (type === 1) {
      wx.navigateTo({ url: `/pages/material/material?id=${courseId}` })
    } else if (type === 3) {
      wx.navigateTo({ url: `/pages/discussion/discussion?id=${courseId}` })
    } else if (type === 4) {
      wx.navigateTo({ url: `/pages/quiz/quiz?id=${courseId}` })
    } else {
      wx.navigateTo({ url: `/pages/course-detail/course-detail?id=${courseId}` })
    }
  },

  goHomework(e) {
    const { id } = e.currentTarget.dataset
    if (!id) return
    if (this.data.role === 'teacher') {
      wx.navigateTo({ url: `/pages/homework-detail/homework-detail?id=${id}` })
    } else {
      wx.navigateTo({ url: `/pages/submit-homework/submit-homework?id=${id}` })
    }
  },

  enterLive(e) {
    const id = Number(e.currentTarget.dataset.id)
    const session = this.data.sessions.find(i => i.id === id)
    if (!session) return
    if (this.data.role === 'teacher') {
      wx.showToast({ title: '请在电脑端进入课堂', icon: 'none' })
      return
    }
    if (session.checked) {
      wx.navigateTo({ url: `/pages/live/live?sessionId=${session.id}` })
      return
    }
    this.checkinThenEnter(session)
  },

  async checkinThenEnter(session) {
    const result = await checkinUtil.fetchClassify()
    if (result.state === checkinUtil.STATE.NETWORK) {
      checkinUtil.showCheckinError(new Error('网络异常'), {
        onRefresh: () => this.loadActive()
      })
      return
    }
    const target = result.list.find(i => i.id === session.checkinId)
    if (!target || !checkinUtil.isActive(target)) {
      checkinUtil.showCheckinError(new Error('签到已结束'), {
        onRefresh: () => this.loadActive()
      })
      return
    }
    if (!checkinUtil.isNumberType(target.type)) {
      checkinUtil.showMethodHint({
        title: '该签到需扫码',
        content: '请扫描老师出示的二维码完成签到后再进入课堂',
        confirmText: '去扫码',
        onConfirm: () => wx.switchTab({ url: '/pages/index/index' })
      })
      return
    }
    checkinUtil.openNumberInput(
      { list: [target], activeCount: 1 },
      () => this.loadActive()
    )
  }
})