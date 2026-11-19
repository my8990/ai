const { request } = require('../../utils/request')

Page({
  data: {
    activeTab: 'pending',
    pendingList: [],
    allList: []
  },

  onShow() {
    this.loadPending()
  },

  async loadPending() {
    try {
      const res = await request({ url: '/user/pending-teachers', method: 'GET' })
      this.setData({ pendingList: res.data || [] })
    } catch (e) { /* handled */ }
  },

  async loadAll() {
    try {
      const res = await request({ url: '/user/teachers', method: 'GET' })
      this.setData({ allList: res.data || [] })
    } catch (e) { /* handled */ }
  },

  switchTab(e) {
    const tab = e.currentTarget.dataset.tab
    this.setData({ activeTab: tab })
    if (tab === 'all') this.loadAll()
  },

  handleApprove(e) {
    const userId = e.currentTarget.dataset.id
    wx.showModal({
      title: '确认',
      content: '确认通过该教师审核？',
      success: async (res) => {
        if (res.confirm) {
          try {
            await request({ url: `/user/approve/${userId}`, method: 'PUT' })
            wx.showToast({ title: '已通过', icon: 'success' })
            this.loadPending()
          } catch (e) { /* handled */ }
        }
      }
    })
  },

  handleReject(e) {
    const userId = e.currentTarget.dataset.id
    wx.showModal({
      title: '确认',
      content: '驳回后该教师账号将被禁用，确认？',
      success: async (res) => {
        if (res.confirm) {
          try {
            await request({ url: `/user/reject/${userId}`, method: 'PUT' })
            wx.showToast({ title: '已驳回', icon: 'success' })
            this.loadPending()
          } catch (e) { /* handled */ }
        }
      }
    })
  }
})