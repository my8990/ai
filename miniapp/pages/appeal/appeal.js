const api = require('../../utils/request')

Page({
  data: {
    reason: '',
    appealList: [],
    hasPending: false,
    userInfo: {}
  },

  onLoad() {
    const userInfo = wx.getStorageSync('userInfo') || {}
    this.setData({ userInfo })
    this.loadAppeals()
  },

  onInputReason(e) {
    this.setData({ reason: e.detail.value })
  },

  async loadAppeals() {
    try {
      const res = await api.getMyAppeals()
      const list = res.data || []
      const hasPending = list.some(a => a.status === 0)
      this.setData({ appealList: list, hasPending })
    } catch (e) { /* handled */ }
  },

  async handleSubmit() {
    const { reason } = this.data
    if (!reason.trim()) {
      wx.showToast({ title: '请输入申诉理由', icon: 'none' })
      return
    }

    wx.showLoading({ title: '提交中' })
    try {
      await api.submitAppeal(reason)
      wx.hideLoading()
      wx.showToast({ title: '申诉已提交', icon: 'success' })
      this.setData({ reason: '' })
      this.loadAppeals()
    } catch {
      wx.hideLoading()
    }
  },

  statusText(status) {
    return { 0: '待处理', 1: '已通过', 2: '已驳回' }[status] || '未知'
  },

  formatTime(t) {
    if (!t) return ''
    return t.replace('T', ' ').substring(0, 19)
  }
})