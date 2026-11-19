const { BASE_URL, joinLive, getLiveState, getLiveSlides, heartbeatLive, createLiveWsTicket, toggleLiveHand, askLiveQuestion } = require('../../utils/request')
const checkinUtil = require('../../utils/checkin')

const WS_BASE = BASE_URL.replace(/^http/, 'ws')

function parseScene(scene) {
  if (!scene) return null
  let text = String(scene)
  try {
    text = decodeURIComponent(text)
  } catch (e) { /* keep raw */ }
  const match = text.match(/(\d+)[-_/](\d{6})/)
  return match ? { sessionId: match[1], code: match[2] } : null
}

function parseLiveParams(options) {
  const fromScene = parseScene(options.scene)
  if (fromScene) return fromScene
  const sessionId = options.sessionId || options.sessionid || ''
  if (sessionId) return { sessionId, code: options.code || '' }
  if (options.q) {
    let q = String(options.q)
    try {
      q = decodeURIComponent(q)
    } catch (e) { /* keep raw */ }
    const match = q.match(/live:\/\/(\d+)\?code=(\d{6})/)
    if (match) return { sessionId: match[1], code: match[2] }
  }
  return { sessionId: '', code: '' }
}

Page({
  data: {
    sessionId: '',
    code: '',
    courseId: '',
    title: '',
    courseName: '',
    status: 0,
    currentPage: 0,
    slideTotal: 0,
    slide: null,
    onlineCount: 0,
    checkedCount: 0,
    totalCount: 0,
    connected: false,
    ended: false,
    errorMsg: '',
    joined: false,
    preloadUrls: [],
    handRaised: false,
    handLoading: false,
    askVisible: false,
    askText: '',
    askLoading: false,
    quiz: null
  },

  onLoad(options) {
    const userInfo = wx.getStorageSync('userInfo')
    if (!userInfo) {
      wx.reLaunch({ url: '/pages/login/login' })
      return
    }
    const params = parseLiveParams(options)
    this.setData({ sessionId: params.sessionId, code: params.code })
    this.join()
  },

  onUnload() {
    this.unloading = true
    this.closeSocket()
    this.stopPolling()
    this.stopHeartbeat()
  },

  onHide() {
    this.closeSocket()
    this.stopPolling()
    this.stopHeartbeat()
  },

  onShow() {
    if (this.data.joined && !this.data.ended) {
      this.startHeartbeat()
      this.connectSocket()
      getLiveState(this.data.sessionId)
        .then((res) => this.applyState(res.data || {}))
        .catch(() => {})
    }
  },

  async join() {
    const sessionId = Number(this.data.sessionId)
    if (!sessionId) {
      this.setData({ ended: true, errorMsg: '课堂信息不完整，请重新扫码' })
      return
    }
    try {
      const loc = await checkinUtil.getLocation()
      const payload = {
        sessionId,
        latitude: loc.latitude,
        longitude: loc.longitude,
        address: loc.address || ''
      }
      if (this.data.code) payload.code = this.data.code
      const res = await joinLive(payload)
      this.setData({ joined: true })
      this.applyState(res.data || {})
      if (!this.data.ended) {
        wx.showToast({ title: '已进入课堂', icon: 'success' })
        this.startHeartbeat()
        this.connectSocket()
        this.loadSlideCache()
      }
    } catch (e) {
      this.setData({ ended: true, errorMsg: (e && e.message) || '进入课堂失败' })
    }
  },

  applyState(data) {
    this.setData({
      courseId: data.courseId || this.data.courseId,
      title: data.title || '',
      courseName: data.courseName || '',
      status: data.status || 0,
      currentPage: data.currentPage || 0,
      slideTotal: data.slideTotal || 0,
      slide: data.currentSlide || null,
      onlineCount: data.onlineCount || 0,
      checkedCount: data.checkedCount || 0,
      totalCount: data.totalCount || 0,
      ended: data.status === 0,
      handRaised: typeof data.myHandRaised === 'boolean' ? data.myHandRaised : this.data.handRaised,
      quiz: data.currentQuiz || null
    })
    const page = data.currentPage || 0
    if (page > 0 && page !== this.lastPreloadPage) {
      this.lastPreloadPage = page
      this.preloadNearby(page)
    }
    if (data.status === 0) {
      this.stopPolling()
      this.stopHeartbeat()
      this.closeSocket()
      this.setData({ preloadUrls: [], handRaised: false, askVisible: false })
    }
  },

  async toggleHand() {
    if (this.data.status !== 1 || this.data.handLoading) return
    this.setData({ handLoading: true })
    try {
      const res = await toggleLiveHand(this.data.sessionId)
      const raised = Boolean(res && res.data)
      this.setData({ handRaised: raised })
      wx.showToast({ title: raised ? '已举手' : '已取消举手', icon: 'none' })
    } catch (e) {
      wx.showToast({ title: e.message || '举手失败', icon: 'none' })
    } finally {
      this.setData({ handLoading: false })
    }
  },

  openAsk() {
    if (this.data.status !== 1) return
    this.setData({ askVisible: true, askText: '' })
  },

  goQuiz() {
    const quiz = this.data.quiz
    if (!quiz || !quiz.quizId) return
    if (quiz.mySubmitted) {
      wx.showToast({ title: '你已提交本次测验', icon: 'none' })
      return
    }
    const assignmentId = quiz.assignmentId || ''
    wx.navigateTo({
      url: `/pages/quiz-take/quiz-take?id=${quiz.quizId}&assignmentId=${assignmentId}`
    })
  },

  closeAsk() {
    this.setData({ askVisible: false })
  },

  onAskInput(e) {
    this.setData({ askText: e.detail.value })
  },

  noop() {},

  async submitAsk() {
    const content = (this.data.askText || '').trim()
    if (!content) {
      wx.showToast({ title: '请输入提问内容', icon: 'none' })
      return
    }
    if (this.data.askLoading) return
    this.setData({ askLoading: true })
    try {
      await askLiveQuestion({ sessionId: Number(this.data.sessionId), content })
      this.setData({ askVisible: false, askText: '' })
      wx.showToast({ title: '已提交提问', icon: 'success' })
    } catch (e) {
    } finally {
      this.setData({ askLoading: false })
    }
  },

  loadSlideCache() {
    if (this.slidesLoaded) return
    this.slidesLoaded = true
    getLiveSlides(this.data.sessionId)
      .then((res) => {
        this.slides = (res.data || []).map((item) => ({
          pageNo: item.pageNo,
          imageUrl: item.imageUrl
        }))
        if (this.data.currentPage > 0) this.preloadNearby(this.data.currentPage)
      })
      .catch(() => {
        this.slidesLoaded = false
      })
  },

  preloadNearby(page) {
    const list = this.slides || []
    if (!list.length) return
    const currentUrl = (this.data.slide && this.data.slide.imageUrl) || ''
    const urls = []
    for (const item of list) {
      if (!item.imageUrl || item.imageUrl === currentUrl) continue
      if (Math.abs(item.pageNo - page) <= 1) urls.push(item.imageUrl)
    }
    if (urls.length) {
      this.setData({ preloadUrls: urls })
    }
  },

  connectSocket() {
    if (this.socket || !this.data.joined || this.data.ended) return
    const seq = (this.socketSeq || 0) + 1
    this.socketSeq = seq
    createLiveWsTicket(this.data.sessionId)
      .then((res) => {
        if (this.socketSeq !== seq) return
        const ticket = (res && res.data && res.data.ticket) || ''
        if (!ticket) throw new Error('empty ticket')
        if (this.socket || this.data.ended || this.unloading) return
        this.openSocket(ticket)
      })
      .catch(() => {
        if (this.socketSeq !== seq) return
        if (this.data.ended || this.unloading) return
        this.startPolling()
        this.reconnectTimer = setTimeout(() => this.connectSocket(), 3000)
      })
  },

  openSocket(ticket) {
    const url = `${WS_BASE}/ws/live?ticket=${encodeURIComponent(ticket)}`
    const socket = wx.connectSocket({ url })
    this.socket = socket
    this.socketOpen = false
    socket.onOpen(() => {
      if (this.socket !== socket) return
      this.socketOpen = true
      this.setData({ connected: true })
      this.stopPolling()
      this.sendPing()
    })
    socket.onMessage((res) => {
      try {
        const msg = JSON.parse(res.data)
        if (msg && msg.type === 'state' && msg.data) {
          this.applyState(msg.data)
        }
      } catch (e) { /* ignore */ }
    })
    socket.onError(() => {
      if (this.socket !== socket) return
      this.socketOpen = false
      this.setData({ connected: false })
    })
    socket.onClose(() => {
      if (this.socket !== socket) return
      this.socket = null
      this.socketOpen = false
      this.setData({ connected: false })
      if (this.data.ended || this.unloading) return
      this.startPolling()
      this.reconnectTimer = setTimeout(() => this.connectSocket(), 3000)
    })
  },

  closeSocket() {
    this.socketSeq = (this.socketSeq || 0) + 1
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
    const socket = this.socket
    this.socket = null
    this.socketOpen = false
    if (socket) {
      try {
        socket.close({})
      } catch (e) { /* ignore */ }
    }
    if (this.data.connected) {
      this.setData({ connected: false })
    }
  },

  sendPing() {
    if (this.socket && this.socketOpen) {
      this.socket.send({ data: 'ping' })
    }
  },

  startHeartbeat() {
    this.stopHeartbeat()
    this.heartbeatTimer = setInterval(() => {
      if (this.socketOpen) {
        this.sendPing()
        return
      }
      heartbeatLive({ sessionId: Number(this.data.sessionId) }).catch(() => {})
    }, 15000)
  },

  stopHeartbeat() {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer)
      this.heartbeatTimer = null
    }
  },

  startPolling() {
    this.stopPolling()
    this.schedulePoll()
  },

  schedulePoll() {
    const base = 5000
    const jitter = Math.round(base * 0.3 * (Math.random() * 2 - 1))
    this.timer = setTimeout(async () => {
      this.timer = null
      if (this.data.ended || this.unloading) return
      try {
        const res = await getLiveState(this.data.sessionId)
        this.applyState(res.data || {})
      } catch (e) { /* handled */ }
      if (this.socketOpen || this.data.ended || this.unloading) return
      this.schedulePoll()
    }, base + jitter)
  },

  stopPolling() {
    if (this.timer) {
      clearTimeout(this.timer)
      this.timer = null
    }
  },

  previewSlide() {
    if (!this.data.slide || !this.data.slide.imageUrl) return
    wx.previewImage({
      urls: [this.data.slide.imageUrl],
      current: this.data.slide.imageUrl
    })
  },

  goCourse() {
    if (!this.data.courseId) return
    wx.navigateTo({ url: `/pages/course-detail/course-detail?id=${this.data.courseId}` })
  }
})