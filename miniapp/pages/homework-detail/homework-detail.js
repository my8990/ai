const { getHomeworkDetail, getHomeworkSubmissions, gradeHomework } = require('../../utils/request')

Page({
  data: {
    homeworkId: '',
    homework: {},
    submissions: [],
    loading: true,
    gradeVisible: false,
    gradeForm: { submitId: null, score: 0, comment: '' }
  },

  onLoad(options) {
    this.setData({ homeworkId: options.id })
  },

  onShow() {
    this.loadData()
  },

  async loadData() {
    this.setData({ loading: true })
    try {
      const [detailRes, subRes] = await Promise.all([
        getHomeworkDetail(this.data.homeworkId),
        getHomeworkSubmissions(this.data.homeworkId)
      ])
      this.setData({
        homework: detailRes.data,
        submissions: subRes.data || [],
        loading: false
      })
    } catch (e) {
      this.setData({ loading: false })
    }
  },

  openGrade(e) {
    const row = e.currentTarget.dataset.item
    this.setData({
      gradeVisible: true,
      gradeForm: { submitId: row.id, score: row.score || 0, comment: row.comment || '' }
    })
  },

  hideGrade() {
    this.setData({ gradeVisible: false })
  },

  onScoreInput(e) {
    this.setData({ 'gradeForm.score': Number(e.detail.value) })
  },

  onCommentInput(e) {
    this.setData({ 'gradeForm.comment': e.detail.value })
  },

  async handleGrade() {
    try {
      await gradeHomework(this.data.gradeForm)
      wx.showToast({ title: '批改成功', icon: 'success' })
      this.setData({ gradeVisible: false })
      this.loadData()
    } catch (e) { /* handled */ }
  },

  previewImage(e) {
    const url = e.currentTarget.dataset.url
    const urls = e.currentTarget.dataset.urls
    wx.previewImage({ current: url, urls: urls })
  },

  preventBubble() {}
})