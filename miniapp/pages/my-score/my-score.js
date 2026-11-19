const { getMyScore, getScoreDetail } = require('../../utils/request')

Page({
  data: {
    courseId: '',
    score: {},
    detail: {}
  },

  onLoad(options) {
    this.setData({ courseId: options.id })
  },

  onShow() {
    this.loadScore()
  },

  async loadScore() {
    try {
      const res = await getMyScore(this.data.courseId)
      this.setData({ score: res.data || {} })
      const userInfo = wx.getStorageSync('userInfo')
      if (userInfo && userInfo.id) {
        const detailRes = await getScoreDetail(this.data.courseId, userInfo.id)
        const detail = detailRes.data || {}
        detail.checkins = (detail.checkins || []).map(c => {
          c.statusText = c.status === 1 ? '正常' : c.status === 2 ? '迟到' : '缺勤'
          c.timeText = (c.submitTime || c.checkinTime || '').replace('T', ' ').slice(0, 16)
          return c
        })
        detail.homeworks = (detail.homeworks || []).map(h => {
          h.statusText = h.status === 2 ? '已批' : h.status === 1 ? '待批' : '未交'
          return h
        })
        this.setData({ detail })
      }
    } catch (e) { /* handled */ }
  }
})