const {
  BASE_URL, request, removeStudent, createCheckin, endCheckin,
  getCourseCheckins, getCheckinDetail, getMyCheckins, reverseGeocode,
  getCourseHomework, getMyHomework,
  getCourseMaterials, getCourseNotices, getCoursePosts, getCourseQuizzes,
  createNotice, deleteNotice,
  getMyScore, getScoreDetail, getScoreRule, setScoreRule, getCourseScores,
  getCurrentLive
} = require('../../utils/request')

Page({
  data: {
    courseId: '',
    course: {},
    students: [],
    role: '',
    tabs: [],
    activeTab: 'info',
    checkins: [],
    homeworks: [],
    materials: [],
    notices: [],
    posts: [],
    quizzes: [],
    myScore: {},
    myScoreDetail: {},
    scoreRule: { checkinWeight: 50, homeworkWeight: 50 },
    scoreList: [],
    scoreLoading: false,
    createCheckinVisible: false,
    checkinForm: { title: '', type: 1, expireMinutes: 0 },
    liveSession: null,
    createNoticeVisible: false,
    noticeForm: { title: '', content: '' }
  },

  onLoad(options) {
    this.setData({ courseId: options.id })
    const userInfo = wx.getStorageSync('userInfo')
    const role = userInfo ? userInfo.role : ''
    const tabs = [
      { key: 'info', name: '概况' },
      { key: 'score', name: '成绩' },
      { key: 'notice', name: '公告' },
      { key: 'checkin', name: '签到' },
      { key: 'homework', name: '作业' },
      { key: 'material', name: '课件' },
      { key: 'discussion', name: '讨论' },
      { key: 'quiz', name: '测验' }
    ]
    if (role === 'teacher') tabs.push({ key: 'students', name: '学生' })
    this.setData({ role, tabs })
  },

  onShow() {
    this.loadDetail()
    if (this.data.role === 'teacher') this.loadStudents()
    this.loadLive()
    this.loadTabData(this.data.activeTab)
  },

  switchTab(e) {
    const key = e.currentTarget.dataset.key
    if (key === this.data.activeTab) return
    this.setData({ activeTab: key })
    this.loadTabData(key)
  },

  loadTabData(key) {
    if (key === 'score') return this.loadScore()
    if (key === 'notice') return this.loadNotices()
    if (key === 'checkin') return this.loadCheckins()
    if (key === 'homework') return this.loadHomeworks()
    if (key === 'material') return this.loadMaterials()
    if (key === 'discussion') return this.loadPosts()
    if (key === 'quiz') return this.loadQuizzes()
  },

  async loadQuizzes() {
    try {
      const res = await getCourseQuizzes(this.data.courseId)
      const quizzes = (res.data || []).slice(0, 3).map(q => Object.assign({}, q, {
        endText: q.endTime ? q.endTime.replace('T', ' ').slice(0, 16) : '不限',
        statusClass: this.data.role === 'teacher'
          ? (q.status === 0 ? 'draft' : q.status === 2 ? 'ended' : 'published')
          : (q.taken ? 'done' : q.statusText === '已结束' ? 'ended' : q.statusText === '未开始' ? 'waiting' : 'active')
      }))
      this.setData({ quizzes })
    } catch (e) {
      this.setData({ quizzes: [] })
    }
  },

  goQuiz() {
    wx.navigateTo({ url: `/pages/quiz/quiz?id=${this.data.courseId}` })
  },

  async loadDetail() {
    try {
      const res = await request({ url: `/course/detail/${this.data.courseId}`, method: 'GET' })
      this.setData({ course: res.data })
    } catch (e) { /* handled */ }
  },

  async loadLive() {
    try {
      const res = await getCurrentLive(this.data.courseId)
      this.setData({ liveSession: res.data || null })
    } catch (e) {
      this.setData({ liveSession: null })
    }
  },

  enterLive() {
    const live = this.data.liveSession
    if (!live || live.status !== 1) {
      wx.showToast({ title: '当前没有进行中的课堂', icon: 'none' })
      return
    }
    wx.navigateTo({
      url: `/pages/live/live?sessionId=${live.id}&code=${live.joinCode || ''}`
    })
  },

  async loadStudents() {
    try {
      const res = await request({ url: `/course/students/${this.data.courseId}`, method: 'GET' })
      const students = (res.data || []).map(s => {
        s.letter = (s.nickname || s.username || '?').charAt(0).toUpperCase()
        return s
      })
      this.setData({ students })
    } catch (e) { /* handled */ }
  },

  async loadCheckins() {
    try {
      if (this.data.role === 'teacher') {
        const res = await getCourseCheckins(this.data.courseId)
        this.setData({ checkins: res.data || [] })
        return
      }
      const res = await getMyCheckins()
      const list = (res.data || []).filter(c => String(c.courseId) === String(this.data.courseId))
      const checkins = list.map(c => {
        c.statusText = c.status === 1 ? '进行中' : '已结束'
        c.myStatusText = c.checked ? (c.myStatus === 2 ? '迟到' : '已签到') : '未签到'
        c.myStatusClass = c.checked ? (c.myStatus === 2 ? 'late' : 'ok') : 'miss'
        c.createTimeText = (c.createTime || '').replace('T', ' ').slice(0, 16)
        if (c.checked) {
          c.checkinTimeText = (c.myCheckinTime || '').replace('T', ' ').slice(0, 16)
          c.addressText = c.myAddress || ''
          c.latitude = c.myLatitude
          c.longitude = c.myLongitude
        }
        return c
      })
      this.setData({ checkins })
    } catch (e) { /* handled */ }
  },

  async loadHomeworks() {
    try {
      const fn = this.data.role === 'teacher' ? getCourseHomework : getMyHomework
      const res = await fn(this.data.courseId)
      this.setData({ homeworks: res.data || [] })
    } catch (e) { /* handled */ }
  },

  async loadMaterials() {
    try {
      const res = await getCourseMaterials(this.data.courseId)
      const materials = (res.data || []).map(item => {
        item.fileIcon = this.fileIcon(item.fileType, item.fileName)
        item.sizeText = this.formatSize(item.fileSize)
        item.timeText = (item.createTime || '').replace('T', ' ').slice(0, 16)
        item.isImage = this.isImage(item.fileType, item.fileName)
        return item
      })
      this.setData({ materials })
    } catch (e) { /* handled */ }
  },

  async loadNotices() {
    try {
      const res = await getCourseNotices(this.data.courseId)
      const notices = (res.data || []).map(item => {
        item.timeText = (item.createTime || '').replace('T', ' ').slice(0, 16)
        item.typeText = item.type === 1 ? '课堂课件发布' : '教师公告'
        item.typeClass = item.type === 1 ? 'auto' : 'teacher'
        return item
      })
      this.setData({ notices })
    } catch (e) { /* handled */ }
  },

  async loadPosts() {
    try {
      const res = await getCoursePosts(this.data.courseId)
      const posts = (res.data || []).map(item => {
        item.letter = (item.authorName || '?').charAt(0).toUpperCase()
        item.timeText = (item.createTime || '').replace('T', ' ').slice(0, 16)
        item.imageList = item.imageList || []
        return item
      })
      this.setData({ posts })
    } catch (e) { /* handled */ }
  },

  async loadScore() {
    this.setData({ scoreLoading: true })
    try {
      if (this.data.role === 'teacher') {
        const [ruleRes, scoreRes] = await Promise.all([
          getScoreRule(this.data.courseId),
          getCourseScores(this.data.courseId)
        ])
        this.setData({
          scoreRule: ruleRes.data || { checkinWeight: 50, homeworkWeight: 50 },
          scoreList: scoreRes.data || []
        })
      } else {
        const res = await getMyScore(this.data.courseId)
        const myScore = res.data || {}
        let myScoreDetail = {}
        const userInfo = wx.getStorageSync('userInfo')
        if (userInfo && userInfo.id) {
          const detailRes = await getScoreDetail(this.data.courseId, userInfo.id)
          myScoreDetail = detailRes.data || {}
          myScoreDetail.checkins = (myScoreDetail.checkins || []).map(c => {
            c.statusText = c.status === 1 ? '正常' : c.status === 2 ? '迟到' : '缺勤'
            c.timeText = (c.submitTime || c.checkinTime || '').replace('T', ' ').slice(0, 16)
            return c
          })
          myScoreDetail.homeworks = (myScoreDetail.homeworks || []).map(h => {
            h.statusText = h.status === 2 ? '已批' : h.status === 1 ? '待批' : '未交'
            return h
          })
        }
        this.setData({ myScore, myScoreDetail })
      }
    } catch (e) { /* handled */ } finally {
      this.setData({ scoreLoading: false })
    }
  },

  async handleToggle() {
    try {
      await request({ url: `/course/toggle/${this.data.courseId}`, method: 'PUT' })
      wx.showToast({ title: '操作成功', icon: 'success' })
      this.loadDetail()
    } catch (e) { /* handled */ }
  },

  handleDissolve() {
    wx.showModal({
      title: '确认解散',
      content: '解散后不可恢复，确认吗？',
      success: async (res) => {
        if (res.confirm) {
          try {
            await request({ url: `/course/dissolve/${this.data.courseId}`, method: 'DELETE' })
            wx.showToast({ title: '已解散', icon: 'success' })
            wx.navigateBack()
          } catch (e) { /* handled */ }
        }
      }
    })
  },

  handleQuit() {
    wx.showModal({
      title: '确认退课',
      content: '确认退出该课程？',
      success: async (res) => {
        if (res.confirm) {
          try {
            await request({ url: `/course/quit/${this.data.courseId}`, method: 'PUT' })
            wx.showToast({ title: '已退出', icon: 'success' })
            wx.navigateBack()
          } catch (e) { /* handled */ }
        }
      }
    })
  },

  handleRemoveStudent(e) {
    const { studentId, name } = e.currentTarget.dataset
    wx.showModal({
      title: '移除学生',
      content: `确认将 ${name} 移出课程？`,
      success: async (res) => {
        if (res.confirm) {
          try {
            await removeStudent(this.data.courseId, studentId)
            wx.showToast({ title: '已移除', icon: 'success' })
            this.loadStudents()
            this.loadDetail()
          } catch (e) { /* handled */ }
        }
      }
    })
  },

  goCheckin() {
    wx.navigateTo({ url: `/pages/checkin/checkin?id=${this.data.courseId}` })
  },

  goCheckinDetail(e) {
    const id = e.currentTarget.dataset.id
    wx.navigateTo({ url: `/pages/checkin/checkin?id=${this.data.courseId}&checkinId=${id}` })
  },

  openCheckinMap(e) {
    const latitude = Number(e.currentTarget.dataset.lat)
    const longitude = Number(e.currentTarget.dataset.lng)
    if (!latitude || !longitude) return
    wx.openLocation({
      latitude,
      longitude,
      scale: 18,
      name: '签到位置',
      address: e.currentTarget.dataset.address || ''
    })
  },

  handleEndCheckin(e) {
    const checkinId = e.currentTarget.dataset.id
    wx.showModal({
      title: '结束签到',
      content: '确认结束该签到？',
      success: async (res) => {
        if (!res.confirm) return
        try {
          await endCheckin(checkinId)
          wx.showToast({ title: '已结束', icon: 'success' })
          this.loadCheckins()
        } catch (err) { /* handled */ }
      }
    })
  },

  goHomework() {
    wx.navigateTo({ url: `/pages/homework/homework?id=${this.data.courseId}` })
  },

  goHomeworkDetail(e) {
    const id = e.currentTarget.dataset.id
    if (this.data.role === 'teacher') {
      wx.navigateTo({ url: `/pages/homework-detail/homework-detail?id=${id}` })
    } else {
      wx.navigateTo({ url: `/pages/submit-homework/submit-homework?id=${id}` })
    }
  },

  goMaterial() {
    wx.navigateTo({ url: `/pages/material/material?id=${this.data.courseId}` })
  },

  goDiscussion() {
    wx.navigateTo({ url: `/pages/discussion/discussion?id=${this.data.courseId}` })
  },

  onScoreSliderChange(e) {
    const val = e.detail.value
    this.setData({ 'scoreRule.checkinWeight': val, 'scoreRule.homeworkWeight': 100 - val })
  },

  async handleSaveScoreRule() {
    try {
      await setScoreRule({
        courseId: Number(this.data.courseId),
        checkinWeight: this.data.scoreRule.checkinWeight,
        homeworkWeight: this.data.scoreRule.homeworkWeight
      })
      wx.showToast({ title: '规则已保存', icon: 'success' })
      this.loadScore()
    } catch (e) { /* handled */ }
  },

  handleExportScore() {
    const token = wx.getStorageSync('token')
    wx.downloadFile({
      url: BASE_URL + '/score/export/' + this.data.courseId,
      header: { 'Authorization': token ? 'Bearer ' + token : '' },
      success(res) {
        if (res.statusCode === 200) {
          wx.openDocument({ filePath: res.tempFilePath, fileType: 'xlsx', showMenu: true })
        } else {
          wx.showToast({ title: '导出失败', icon: 'none' })
        }
      },
      fail() {
        wx.showToast({ title: '导出失败', icon: 'none' })
      }
    })
  },

  isImage(fileType, fileName) {
    const type = (fileType || '').toLowerCase()
    const name = (fileName || '').toLowerCase()
    return type.indexOf('image') >= 0 || /\.(jpg|jpeg|png|gif|webp)$/.test(name)
  },

  fileIcon(fileType, fileName) {
    const name = (fileName || '').toLowerCase()
    if (this.isImage(fileType, fileName)) return 'IMG'
    if (name.endsWith('.pdf')) return 'PDF'
    if (name.endsWith('.doc') || name.endsWith('.docx')) return 'DOC'
    if (name.endsWith('.ppt') || name.endsWith('.pptx')) return 'PPT'
    if (name.endsWith('.xls') || name.endsWith('.xlsx')) return 'XLS'
    return 'FILE'
  },

  formatSize(size) {
    if (!size) return ''
    if (size < 1024) return size + ' B'
    if (size < 1024 * 1024) return (size / 1024).toFixed(1) + ' KB'
    return (size / 1024 / 1024).toFixed(1) + ' MB'
  },

  openMaterial(e) {
    const item = this.data.materials[e.currentTarget.dataset.index]
    if (!item || !item.fileUrl) return
    if (item.isImage) {
      wx.previewImage({ urls: [item.fileUrl], current: item.fileUrl })
      return
    }
    wx.showLoading({ title: '打开中...' })
    wx.downloadFile({
      url: item.fileUrl,
      success: (res) => {
        if (res.statusCode !== 200) {
          wx.hideLoading()
          wx.showToast({ title: '文件下载失败', icon: 'none' })
          return
        }
        const name = (item.fileName || item.fileUrl || '').toLowerCase()
        let fileType = 'pdf'
        if (name.endsWith('.doc') || name.endsWith('.docx')) fileType = 'doc'
        else if (name.endsWith('.ppt') || name.endsWith('.pptx')) fileType = 'ppt'
        else if (name.endsWith('.xls') || name.endsWith('.xlsx')) fileType = 'xls'
        wx.openDocument({
          filePath: res.tempFilePath,
          fileType,
          showMenu: true,
          complete: () => wx.hideLoading()
        })
      },
      fail: () => {
        wx.hideLoading()
        wx.showToast({ title: '文件下载失败', icon: 'none' })
      }
    })
  },

  showCreateCheckin() {
    this.setData({ createCheckinVisible: true })
  },

  hideCreateCheckin() {
    this.setData({ createCheckinVisible: false })
  },

  onCheckinInput(e) {
    const field = e.currentTarget.dataset.field
    this.setData({ [`checkinForm.${field}`]: e.detail.value })
  },

  setCheckinType(e) {
    const type = Number(e.currentTarget.dataset.type)
    this.setData({ 'checkinForm.type': type })
  },

  async handleCreateCheckin() {
    const { title, type, expireMinutes } = this.data.checkinForm
    if (!title) {
      wx.showToast({ title: '请输入签到标题', icon: 'none' })
      return
    }
    try {
      const res = await createCheckin({
        courseId: Number(this.data.courseId),
        title,
        type,
        expireMinutes: expireMinutes ? Number(expireMinutes) : null
      })
      wx.showToast({ title: '签到已发布', icon: 'success' })
      this.setData({
        createCheckinVisible: false,
        checkinForm: { title: '', type: 1, expireMinutes: 0 }
      })
      wx.navigateTo({ url: `/pages/checkin/checkin?id=${this.data.courseId}&checkinId=${res.data.id}` })
    } catch (e) { /* handled */ }
  },

  showCreateNotice() {
    this.setData({ createNoticeVisible: true, noticeForm: { title: '', content: '' } })
  },

  hideCreateNotice() {
    this.setData({ createNoticeVisible: false })
  },

  onNoticeInput(e) {
    const field = e.currentTarget.dataset.field
    this.setData({ [`noticeForm.${field}`]: e.detail.value })
  },

  async handleCreateNotice() {
    const { title, content } = this.data.noticeForm
    if (!title || !title.trim()) {
      wx.showToast({ title: '请输入公告标题', icon: 'none' })
      return
    }
    try {
      await createNotice({
        courseId: Number(this.data.courseId),
        title: title.trim(),
        content: content ? content.trim() : ''
      })
      wx.showToast({ title: '公告已发布', icon: 'success' })
      this.setData({ createNoticeVisible: false })
      this.loadNotices()
    } catch (e) { /* handled */ }
  },

  handleDeleteNotice(e) {
    const noticeId = e.currentTarget.dataset.id
    const title = e.currentTarget.dataset.title || ''
    wx.showModal({
      title: '删除公告',
      content: `确认删除「${title}」？`,
      success: async (res) => {
        if (!res.confirm) return
        try {
          await deleteNotice(noticeId)
          wx.showToast({ title: '已删除', icon: 'success' })
          this.loadNotices()
        } catch (err) { /* handled */ }
      }
    })
  },

  preventBubble() {}
})