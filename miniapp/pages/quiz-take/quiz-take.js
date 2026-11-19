const { takeQuiz, submitQuiz } = require('../../utils/request')

Page({
  data: {
    id: '',
    assignmentId: '',
    loading: true,
    quiz: null,
    questions: [],
    remaining: -1,
    timeText: '',
    answeredCount: 0,
    submitting: false,
    urgent: false
  },

  onLoad(options) {
    this.setData({ id: options.id, assignmentId: options.assignmentId || '' })
    this.loadQuiz()
  },

  onUnload() {
    this.stopTimer()
  },

  async loadQuiz() {
    this.setData({ loading: true })
    try {
      const res = await takeQuiz(this.data.id, this.data.assignmentId || undefined)
      const quiz = res.data || {}
      const questions = (quiz.questions || []).map((q, idx) => Object.assign({}, q, {
        no: idx + 1,
        typeText: this._typeText(q.type),
        options: (q.options || []).map((text, i) => ({
          text,
          label: String.fromCharCode(65 + i),
          checked: false
        })),
        blank: ''
      }))
      this.setData({
        quiz,
        questions,
        remaining: quiz.remainingSeconds == null ? -1 : quiz.remainingSeconds,
        timeText: this._fmtTime(quiz.remainingSeconds),
        loading: false,
        answeredCount: 0
      })
      if (quiz.remainingSeconds >= 0) {
        this.startTimer(quiz.remainingSeconds)
      }
    } catch (e) {
      this.setData({ loading: false })
      wx.showModal({
        title: '无法开始测验',
        content: e.message || '请稍后重试',
        showCancel: false,
        success: () => wx.navigateBack()
      })
    }
  },

  _typeText(type) {
    if (type === 2) return '多选题'
    if (type === 3) return '判断题'
    if (type === 4) return '填空题'
    return '单选题'
  },

  _fmtTime(seconds) {
    if (seconds == null || seconds < 0) return '不限时'
    const s = Math.max(0, seconds)
    const m = Math.floor(s / 60)
    const sec = s % 60
    return `${String(m).padStart(2, '0')}:${String(sec).padStart(2, '0')}`
  },

  startTimer(seconds) {
    this.endAt = Date.now() + seconds * 1000
    this.tick()
    this.timer = setInterval(() => this.tick(), 1000)
  },

  tick() {
    const left = Math.max(0, Math.round((this.endAt - Date.now()) / 1000))
    this.setData({
      remaining: left,
      timeText: this._fmtTime(left),
      urgent: left <= 60
    })
    if (left <= 0) {
      this.stopTimer()
      this.autoSubmit()
    }
  },

  stopTimer() {
    if (this.timer) {
      clearInterval(this.timer)
      this.timer = null
    }
  },

  onOptionTap(e) {
    const qi = Number(e.currentTarget.dataset.qi)
    const oi = Number(e.currentTarget.dataset.oi)
    const q = this.data.questions[qi]
    if (!q || q.type === 4) return

    if (q.type === 2) {
      this.setData({ [`questions[${qi}].options[${oi}].checked`]: !q.options[oi].checked })
    } else {
      const updates = {}
      q.options.forEach((opt, i) => {
        updates[`questions[${qi}].options[${i}].checked`] = i === oi
      })
      this.setData(updates)
    }
    this.refreshAnswered()
  },

  onBlankInput(e) {
    const qi = Number(e.currentTarget.dataset.qi)
    this.setData({ [`questions[${qi}].blank`]: e.detail.value })
    this.refreshAnswered()
  },

  refreshAnswered() {
    let count = 0
    this.data.questions.forEach(q => {
      if (q.type === 4) {
        if ((q.blank || '').trim()) count++
      } else if (q.options.some(o => o.checked)) {
        count++
      }
    })
    this.setData({ answeredCount: count })
  },

  autoSubmit() {
    if (this.data.submitting) return
    wx.showToast({ title: '时间到，自动提交', icon: 'none' })
    this.doSubmit(true)
  },

  onSubmitTap() {
    if (this.data.submitting) return
    const total = this.data.questions.length
    const done = this.data.answeredCount
    const content = done < total
      ? `还有 ${total - done} 道题未作答，确定提交吗？`
      : '确定提交答卷吗？提交后不可修改。'
    wx.showModal({
      title: '提交答卷',
      content,
      success: res => {
        if (res.confirm) this.doSubmit(false)
      }
    })
  },

  async doSubmit(auto) {
    if (this.data.submitting) return
    this.setData({ submitting: true })
    this.stopTimer()
    try {
      const answers = this.data.questions.map(q => {
        if (q.type === 4) {
          return { questionId: q.id, answer: (q.blank || '').trim() }
        }
        const indexes = q.options
          .map((opt, i) => (opt.checked ? i : -1))
          .filter(i => i >= 0)
        return { questionId: q.id, answer: indexes.join(',') }
      })
      const res = await submitQuiz({
        quizId: Number(this.data.id),
        assignmentId: this.data.quiz && this.data.quiz.assignmentId ? Number(this.data.quiz.assignmentId) : undefined,
        answers
      })
      const record = res.data || {}
      this.setData({ submitting: false })
      const resultQuizId = this.data.id
      const resultAssignmentId = record.assignmentId
        || (this.data.quiz && this.data.quiz.assignmentId)
        || this.data.assignmentId
      wx.redirectTo({
        url: `/pages/quiz-result/quiz-result?quizId=${resultQuizId}&assignmentId=${resultAssignmentId || ''}&recordId=${record.recordId || ''}`
      })
    } catch (e) {
      this.setData({ submitting: false })
      if (auto) {
        wx.showModal({
          title: '提交失败',
          content: e.message || '自动提交失败，请手动提交',
          showCancel: false
        })
      } else {
        wx.showToast({ title: e.message || '提交失败', icon: 'none' })
      }
      if (this.data.remaining > 0) {
        this.startTimer(this.data.remaining)
      }
    }
  }
})