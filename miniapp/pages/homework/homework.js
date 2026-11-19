const { getCourseHomework, getMyHomework } = require('../../utils/request')

Page({
  data: {
    courseId: '',
    isTeacher: false,
    list: [],
    loading: true,
    createVisible: false,
    createForm: { title: '', description: '', deadline: '' }
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
      const fn = this.data.isTeacher ? getCourseHomework : getMyHomework
      const res = await fn(this.data.courseId)
      this.setData({ list: res.data || [], loading: false })
    } catch (e) {
      this.setData({ loading: false })
    }
  },

  goDetail(e) {
    const id = e.currentTarget.dataset.id
    if (this.data.isTeacher) {
      wx.navigateTo({ url: `/pages/homework-detail/homework-detail?id=${id}` })
    } else {
      wx.navigateTo({ url: `/pages/submit-homework/submit-homework?id=${id}` })
    }
  },

  showCreate() {
    this.setData({ createVisible: true })
  },

  hideCreate() {
    this.setData({ createVisible: false })
  },

  onTitleInput(e) {
    this.setData({ 'createForm.title': e.detail.value })
  },

  onDescInput(e) {
    this.setData({ 'createForm.description': e.detail.value })
  },

  onDeadlineChange(e) {
    this.setData({ 'createForm.deadline': e.detail.value })
  },

  async handleCreate() {
    const { createForm } = this.data
    if (!createForm.title) {
      wx.showToast({ title: '请输入标题', icon: 'none' })
      return
    }
    const { createHomework } = require('../../utils/request')
    try {
      await createHomework({
        courseId: Number(this.data.courseId),
        title: createForm.title,
        description: createForm.description || '',
        deadline: createForm.deadline || ''
      })
      wx.showToast({ title: '发布成功', icon: 'success' })
      this.setData({ createVisible: false, createForm: { title: '', description: '', deadline: '' } })
      this.loadList()
    } catch (e) { /* handled */ }
  }
})