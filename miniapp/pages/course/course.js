const { request, joinCourse } = require('../../utils/request')

const RECENT_KEY = 'recentCourseSearch'

function parseCourseCode(text) {
  if (!text) return ''
  const match = String(text).match(/[A-Za-z0-9]{6}/)
  return match ? match[0].toUpperCase() : ''
}

Page({
  data: {
    courses: [],
    visibleCourses: [],
    role: '',
    searchMode: false,
    searchFocus: false,
    keyword: '',
    recentSearches: [],
    createVisible: false,
    joinVisible: false,
    creating: false,
    joining: false,
    joinCode: '',
    createForm: { name: '', semester: '', college: '', description: '' }
  },

  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({ selected: 'course' })
    }
    const userInfo = wx.getStorageSync('userInfo')
    this.setData({
      role: userInfo ? userInfo.role : '',
      recentSearches: wx.getStorageSync(RECENT_KEY) || []
    })
    this.loadCourses()
  },

  async loadCourses() {
    try {
      const res = await request({ url: '/course/my', method: 'GET' })
      const courses = (res.data || []).map((c, i) => {
        c.colorIndex = i % 8
        return c
      })
      this.setData({ courses, visibleCourses: this.filterCourses(courses, this.data.keyword) })
    } catch (e) { /* handled */ }
  },

  filterCourses(list, keyword) {
    const kw = (keyword || '').trim().toUpperCase()
    if (!kw) return list || []
    return (list || []).filter(c =>
      (c.name || '').toUpperCase().indexOf(kw) > -1 ||
      (c.code || '').toUpperCase().indexOf(kw) > -1
    )
  },

  enterSearch() {
    if (this.data.searchMode) return
    this.setData({ searchMode: true, searchFocus: true })
  },

  exitSearch() {
    this.setData({ searchMode: false, searchFocus: false, keyword: '', visibleCourses: this.data.courses })
  },

  onSearchInput(e) {
    const keyword = e.detail.value
    this.setData({ keyword, visibleCourses: this.filterCourses(this.data.courses, keyword) })
  },

  onSearchConfirm() {
    this.saveRecent((this.data.keyword || '').trim())
  },

  clearKeyword() {
    this.setData({ keyword: '', visibleCourses: this.data.courses })
  },

  saveRecent(text) {
    const kw = (text || '').trim()
    if (!kw) return
    const list = (this.data.recentSearches || []).filter(i => i !== kw)
    list.unshift(kw)
    const recentSearches = list.slice(0, 10)
    this.setData({ recentSearches })
    wx.setStorageSync(RECENT_KEY, recentSearches)
  },

  useRecent(e) {
    const text = e.currentTarget.dataset.text || ''
    this.setData({ keyword: text, visibleCourses: this.filterCourses(this.data.courses, text) })
    this.saveRecent(text)
  },

  clearRecent() {
    this.setData({ recentSearches: [] })
    wx.removeStorageSync(RECENT_KEY)
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
        this.joinByCode(code)
      },
      fail: () => {}
    })
  },

  async joinByCode(code) {
    if (!code || this.data.joining) return
    this.setData({ joining: true })
    try {
      await joinCourse(code)
      wx.showToast({ title: '加入成功', icon: 'success' })
      this.loadCourses()
    } catch (e) { /* handled */ } finally {
      this.setData({ joining: false })
    }
  },

  showCreate() { this.setData({ createVisible: true }) },
  hideCreate() { this.setData({ createVisible: false, creating: false }) },
  showJoin() { this.setData({ joinVisible: true, joinCode: '' }) },
  hideJoin() { this.setData({ joinVisible: false, joining: false }) },

  onCreateInput(e) {
    const field = e.currentTarget.dataset.field
    this.setData({ [`createForm.${field}`]: e.detail.value })
  },

  onJoinInput(e) {
    const value = (e.detail.value || '').replace(/[^0-9A-Za-z]/g, '').toUpperCase()
    this.setData({ joinCode: value })
  },

  async handleCreate() {
    if (this.data.creating) return
    const name = (this.data.createForm.name || '').trim()
    const semester = (this.data.createForm.semester || '').trim()
    if (!name || !semester) {
      wx.showToast({ title: '请填写课程名称和学期', icon: 'none' })
      return
    }
    this.setData({ creating: true })
    try {
      await request({
        url: '/course/create',
        method: 'POST',
        data: {
          name,
          semester,
          college: (this.data.createForm.college || '').trim(),
          description: (this.data.createForm.description || '').trim()
        }
      })
      wx.showToast({ title: '创建成功', icon: 'success' })
      this.setData({ createVisible: false, createForm: { name: '', semester: '', college: '', description: '' } })
      this.loadCourses()
    } catch (e) { /* handled */ } finally {
      this.setData({ creating: false })
    }
  },

  async handleJoin() {
    if (this.data.joining) return
    const code = (this.data.joinCode || '').trim()
    if (!code) {
      wx.showToast({ title: '请输入课程码', icon: 'none' })
      return
    }
    if (code.length !== 6) {
      wx.showToast({ title: '课程码为6位字符', icon: 'none' })
      return
    }
    this.setData({ joining: true })
    try {
      await request({ url: '/course/join', method: 'POST', data: { code } })
      wx.showToast({ title: '加入成功', icon: 'success' })
      this.setData({ joinVisible: false, joinCode: '' })
      this.loadCourses()
    } catch (e) { /* handled */ } finally {
      this.setData({ joining: false })
    }
  },

  goDetail(e) {
    const id = e.currentTarget.dataset.id
    wx.navigateTo({ url: `/pages/course-detail/course-detail?id=${id}` })
  },

  preventBubble() {}
})