const { getCourseQuizzes, getQuizDetail, publishAssignment, endAssignment, deleteAssignment } = require('../../utils/request')

Page({
  data: {
    courseId: '',
    isTeacher: false,
    list: [],
    loading: true,
    detailVisible: false,
    detail: null,
    detailLoading: false
  },

  onLoad(options) {
    const userInfo = wx.getStorageSync('userInfo')
    this.setData({
      courseId: options.id,
      isTeacher: userInfo && userInfo.role === 'teacher'
    })
  },

  onShow() {
    this.loadList()
  },

  preventBubble() {},

  async loadList() {
    this.setData({ loading: true })
    try {
      const res = await getCourseQuizzes(this.data.courseId)
      const list = (res.data || []).map(q => Object.assign({}, q, {
        timeText: (q.createTime || '').replace('T', ' ').slice(0, 16),
        endText: q.endTime ? q.endTime.replace('T', ' ').slice(0, 16) : '不限',
        statusClass: this._statusClass(q)
      }))
      this.setData({ list, loading: false })
    } catch (e) {
      this.setData({ list: [], loading: false })
    }
  },

  _statusClass(q) {
    if (this.data.isTeacher) {
      if (q.status === 0) return 'draft'
      if (q.status === 2) return 'ended'
      return 'published'
    }
    if (q.taken) return 'done'
    if (q.statusText === '已结束') return 'ended'
    if (q.statusText === '未开始') return 'waiting'
    return 'active'
  },

  onCardTap(e) {
    const id = Number(e.currentTarget.dataset.id)
    const item = this.data.list.find(i => i.id === id)
    if (!item) return
    if (this.data.isTeacher) {
      this.openDetail(item)
      return
    }
    const targetId = item.id
    const assignmentId = item.assignmentId || ''
    if (item.taken) {
      wx.navigateTo({ url: `/pages/quiz-result/quiz-result?quizId=${targetId}&assignmentId=${assignmentId}` })
      return
    }
    if (!item.canTake) {
      wx.showToast({ title: item.statusText || '暂不可作答', icon: 'none' })
      return
    }
    wx.navigateTo({ url: `/pages/quiz-take/quiz-take?id=${targetId}&assignmentId=${assignmentId}` })
  },

  async openDetail(item) {
    this.setData({ detailVisible: true, detailLoading: true, detail: null })
    try {
      const res = await getQuizDetail(item.id)
      const detail = res.data || null
      if (detail) {
        detail.assignmentId = item.assignmentId
        detail.assignmentStatus = item.assignmentStatus
        detail.assignmentStatusText = item.statusText
      }
      if (detail && detail.questions) {
        detail.questions = detail.questions.map((q, idx) => {
          const indexes = String(q.correctAnswer || '').split(',').filter(s => s !== '')
          const optionList = (q.options || []).map((text, i) => ({
            text,
            label: String.fromCharCode(65 + i),
            correct: indexes.indexOf(String(i)) >= 0
          }))
          const answerText = q.type === 4
            ? (q.correctAnswer || '未设置')
            : optionList.filter(o => o.correct).map(o => o.label).join('、')
          return Object.assign({}, q, {
            no: idx + 1,
            typeText: this._typeText(q.type),
            optionList,
            answerText
          })
        })
      }
      this.setData({ detail, detailLoading: false })
    } catch (e) {
      this.setData({ detailLoading: false })
      this.closeDetail()
    }
  },

  closeDetail() {
    this.setData({ detailVisible: false, detail: null })
  },

  _typeText(type) {
    if (type === 2) return '多选'
    if (type === 3) return '判断'
    if (type === 4) return '填空'
    return '单选'
  },

  async handlePublish() {
    const assignmentId = this.data.detail && this.data.detail.assignmentId
    if (!assignmentId) return
    const ok = await new Promise(resolve => {
      wx.showModal({
        title: '发布测验',
        content: '发布后学生将收到公告通知，且不能再次编辑题目',
        success: res => resolve(res.confirm)
      })
    })
    if (!ok) return
    try {
      await publishAssignment(assignmentId)
      wx.showToast({ title: '已发布', icon: 'success' })
      this.closeDetail()
      this.loadList()
    } catch (e) {
      wx.showToast({ title: e.message || '发布失败', icon: 'none' })
    }
  },

  async handleEnd() {
    const assignmentId = this.data.detail && this.data.detail.assignmentId
    if (!assignmentId) return
    const ok = await new Promise(resolve => {
      wx.showModal({
        title: '结束测验',
        content: '结束后学生将无法继续作答，确定结束吗？',
        success: res => resolve(res.confirm)
      })
    })
    if (!ok) return
    try {
      await endAssignment(assignmentId)
      wx.showToast({ title: '已结束', icon: 'success' })
      this.closeDetail()
      this.loadList()
    } catch (e) {
      wx.showToast({ title: e.message || '操作失败', icon: 'none' })
    }
  },

  async handleDelete() {
    const assignmentId = this.data.detail && this.data.detail.assignmentId
    if (!assignmentId) return
    const ok = await new Promise(resolve => {
      wx.showModal({
        title: '删除测验',
        content: '删除后本课程内该测验的答卷记录也会一并清除，确定删除吗？',
        success: res => resolve(res.confirm)
      })
    })
    if (!ok) return
    try {
      await deleteAssignment(assignmentId)
      wx.showToast({ title: '已删除', icon: 'success' })
      this.closeDetail()
      this.loadList()
    } catch (e) {
      wx.showToast({ title: e.message || '删除失败', icon: 'none' })
    }
  }
})