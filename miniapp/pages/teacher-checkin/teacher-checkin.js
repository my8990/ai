const { request, getMyCourses, getCourseCheckins, endCheckin, getCheckinDetail, createCheckin } = require('../../utils/request')

function isExpired(expireTime) {
  if (!expireTime) return false
  const end = new Date(String(expireTime).replace('T', ' ').replace(/-/g, '/')).getTime()
  return !isNaN(end) && end <= Date.now()
}

Page({
  data: {
    courses: [],
    selectedCourseId: '',
    selectedCourseName: '',
    checkins: [],
    detailVisible: false,
    detail: {},
    createVisible: false,
    checkinForm: { title: '', type: 1, expireMinutes: 0 }
  },

  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({ selected: 'checkin' })
    }
    this.loadCourses()
  },

  async loadCourses() {
    try {
      const res = await getMyCourses()
      const courses = res.data || []
      this.setData({ courses })
      if (courses.length > 0 && !this.data.selectedCourseId) {
        this.setData({
          selectedCourseId: courses[0].id,
          selectedCourseName: courses[0].name
        })
        this.loadCheckins()
      }
    } catch (e) { /* handled */ }
  },

  onCourseChange(e) {
    const idx = e.detail.value
    const course = this.data.courses[idx]
    if (course) {
      this.setData({
        selectedCourseId: course.id,
        selectedCourseName: course.name
      })
      this.loadCheckins()
    }
  },

  async loadCheckins() {
    if (!this.data.selectedCourseId) return
    try {
      const res = await getCourseCheckins(this.data.selectedCourseId)
      this.setData({ checkins: res.data || [] })
    } catch (e) { /* handled */ }
  },

  showCreate() {
    const active = this.data.checkins.find(i => i.status === 1 && !isExpired(i.expireTime))
    if (active) {
      wx.showModal({
        title: '已有进行中的签到',
        content: `同一课程同时只能有一个进行中的签到，请先结束「${active.title}」后再发布`,
        showCancel: false,
        confirmText: '知道了'
      })
      return
    }
    this.setData({ createVisible: true, checkinForm: { title: '', type: 1, expireMinutes: 0 } })
  },

  hideCreate() {
    this.setData({ createVisible: false })
  },

  onFormInput(e) {
    const field = e.currentTarget.dataset.field
    this.setData({ [`checkinForm.${field}`]: e.detail.value })
  },

  setCheckinType(e) {
    const type = Number(e.currentTarget.dataset.type)
    this.setData({ 'checkinForm.type': type })
  },

  async handleCreate() {
    const { title, type, expireMinutes } = this.data.checkinForm
    if (!title) {
      wx.showToast({ title: '请输入签到标题', icon: 'none' })
      return
    }
    try {
      await createCheckin({
        courseId: Number(this.data.selectedCourseId),
        title,
        type,
        expireMinutes: expireMinutes ? Number(expireMinutes) : null
      })
      wx.showToast({ title: '签到已发布', icon: 'success' })
      this.setData({ createVisible: false })
      this.loadCheckins()
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