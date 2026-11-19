const { getMyQuizRecord } = require('../../utils/request')

Page({
  data: {
    quizId: '',
    assignmentId: '',
    loading: true,
    record: null,
    questions: []
  },

  onLoad(options) {
    this.setData({ quizId: options.quizId, assignmentId: options.assignmentId || '' })
    this.loadRecord()
  },

  async loadRecord() {
    this.setData({ loading: true })
    try {
      const res = await getMyQuizRecord(this.data.quizId, this.data.assignmentId || undefined)
      const record = res.data || {}
      const questions = (record.questions || []).map((q, idx) => {
        const mine = this._indexes(q.myAnswer)
        const correct = this._indexes(q.correctAnswer)
        return Object.assign({}, q, {
          no: idx + 1,
          typeText: this._typeText(q.type),
          optionList: (q.options || []).map((text, i) => ({
            text,
            label: String.fromCharCode(65 + i),
            correct: correct.indexOf(i) >= 0,
            mine: mine.indexOf(i) >= 0
          })),
          myAnswerText: q.type === 4 ? (q.myAnswer || '未作答') : null,
          correctAnswerText: q.type === 4 ? (q.correctAnswer || '') : null
        })
      })
      this.setData({ record, questions, loading: false })
    } catch (e) {
      this.setData({ loading: false })
      wx.showToast({ title: e.message || '加载失败', icon: 'none' })
    }
  },

  _indexes(value) {
    if (!value) return []
    return String(value)
      .split(',')
      .filter(s => s !== '')
      .map(s => Number(s))
  },

  _typeText(type) {
    if (type === 2) return '多选题'
    if (type === 3) return '判断题'
    if (type === 4) return '填空题'
    return '单选题'
  },

  goBack() {
    wx.navigateBack()
  }
})