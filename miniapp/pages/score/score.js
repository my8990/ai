const { BASE_URL, getScoreRule, setScoreRule, getCourseScores } = require('../../utils/request')

Page({
  data: {
    courseId: '',
    rule: { checkinWeight: 40, homeworkWeight: 30, quizWeight: 30 },
    scoreList: [],
    loading: true
  },

  onLoad(options) {
    this.setData({ courseId: options.id })
  },

  onShow() {
    this.loadData()
  },

  async loadData() {
    this.setData({ loading: true })
    try {
      const [ruleRes, scoreRes] = await Promise.all([
        getScoreRule(this.data.courseId),
        getCourseScores(this.data.courseId)
      ])
      if (ruleRes.data) {
        this.setData({ rule: ruleRes.data })
      }
      this.setData({ scoreList: scoreRes.data || [], loading: false })
    } catch (e) {
      this.setData({ loading: false })
    }
  },

  onSliderChange(e) {
    const key = e.currentTarget.dataset.key
    const val = e.detail.value
    const others = ['checkinWeight', 'homeworkWeight', 'quizWeight'].filter((k) => k !== key)
    const remain = 100 - val
    const [first, second] = others
    const curFirst = this.data.rule[first] || 0
    const curSecond = this.data.rule[second] || 0
    const curSum = curFirst + curSecond
    let nextFirst = curSum > 0
      ? Math.round((remain * curFirst) / curSum / 5) * 5
      : Math.round(remain / 2 / 5) * 5
    if (nextFirst > remain) nextFirst = remain
    if (nextFirst < 0) nextFirst = 0
    this.setData({
      [`rule.${key}`]: val,
      [`rule.${first}`]: nextFirst,
      [`rule.${second}`]: remain - nextFirst
    })
  },

  async handleSetRule() {
    try {
      await setScoreRule({
        courseId: Number(this.data.courseId),
        checkinWeight: this.data.rule.checkinWeight,
        homeworkWeight: this.data.rule.homeworkWeight,
        quizWeight: this.data.rule.quizWeight
      })
      wx.showToast({ title: '规则已保存', icon: 'success' })
      this.loadData()
    } catch (e) { /* handled */ }
  },

  handleExport() {
    const token = wx.getStorageSync('token')
    wx.downloadFile({
      url: BASE_URL + '/score/export/' + this.data.courseId,
      header: { 'Authorization': token ? 'Bearer ' + token : '' },
      success(res) {
        if (res.statusCode === 200) {
          wx.openDocument({
            filePath: res.tempFilePath,
            fileType: 'xlsx',
            showMenu: true
          })
        }
      },
      fail() {
        wx.showToast({ title: '导出失败', icon: 'none' })
      }
    })
  }
})