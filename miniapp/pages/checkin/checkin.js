const { request, getCourseCheckins, endCheckin, getCheckinDetail } = require('../../utils/request')

Page({
  data: {
    courseId: '',
    checkins: [],
    detailVisible: false,
    detail: {}
  },

  onLoad(options) {
    this.setData({ courseId: options.id })
    if (options.checkinId) {
      this.autoCheckinId = options.checkinId
    }
  },

  onShow() {
    this.loadCheckins().then(() => {
      if (this.autoCheckinId) {
        this.showDetailByCheckinId(this.autoCheckinId)
        this.autoCheckinId = null
      }
    })
  },

  async loadCheckins() {
    try {
      const res = await getCourseCheckins(this.data.courseId)
      this.setData({ checkins: res.data || [] })
    } catch (e) { /* handled */ }
  },

  async showDetailByCheckinId(checkinId) {
    try {
      const res = await getCheckinDetail(checkinId)
      this.setData({ detailVisible: true, detail: res.data })
    } catch (e) { /* handled */ }
  },

  async handleEnd(e) {
    const checkinId = e.currentTarget.dataset.id
    wx.showModal({
      title: '结束签到',
      content: '确认结束该签到？',
      success: async (res) => {
        if (res.confirm) {
          try {
            await endCheckin(checkinId)
            wx.showToast({ title: '已结束', icon: 'success' })
            this.loadCheckins()
          } catch (e) { /* handled */ }
        }
      }
    })
  },

  async showDetail(e) {
    const checkinId = e.currentTarget.dataset.id
    try {
      const res = await getCheckinDetail(checkinId)
      this.setData({ detailVisible: true, detail: res.data })
    } catch (e) { /* handled */ }
  },

  hideDetail() {
    this.setData({ detailVisible: false })
  },

  preventBubble() {}
})