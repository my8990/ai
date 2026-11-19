const { getMyCheckins } = require('../../utils/request')
const checkinUtil = require('../../utils/checkin')

function formatRemain(expireTime) {
  if (!expireTime) return ''
  const end = new Date(String(expireTime).replace('T', ' ').replace(/-/g, '/')).getTime()
  if (isNaN(end)) return ''
  const diff = end - Date.now()
  if (diff <= 0) return '已截止'
  const totalMin = Math.floor(diff / 60000)
  if (totalMin >= 60) {
    const hour = Math.floor(totalMin / 60)
    const min = totalMin % 60
    return min > 0 ? `剩余 ${hour} 小时 ${min} 分` : `剩余 ${hour} 小时`
  }
  if (totalMin >= 1) return `剩余 ${totalMin} 分钟`
  return '剩余不足 1 分钟'
}

function decorate(item) {
  const absentLike = item.myStatus === 3 || item.myStatus === 4
  const pending = item.status === 1 && !item.checked && !absentLike
  const isQr = checkinUtil.isQrType(item.type)
  const isNumber = checkinUtil.isNumberType(item.type)
  let statusText = '进行中'
  let statusClass = 'active'
  if (item.myStatus === 3) {
    statusText = '已请假'
    statusClass = 'ended'
  } else if (item.myStatus === 4) {
    statusText = '缺勤'
    statusClass = 'ended'
  } else if (item.checked) {
    statusText = item.myStatus === 2 ? '已签到·迟到' : '已签到'
    statusClass = 'done'
  } else if (item.status !== 1) {
    statusText = '已结束'
    statusClass = 'ended'
  }
  let typeText = '数字'
  if (item.type === 2) typeText = '扫码'
  else if (item.type === 3) typeText = '扫码/数字'
  return {
    id: item.id,
    title: item.title,
    courseName: item.courseName,
    type: item.type,
    isQr,
    isNumber,
    dual: isQr && isNumber,
    pending,
    statusText,
    statusClass,
    typeText,
    createTime: item.createTime,
    expireText: pending ? formatRemain(item.expireTime) : '',
    lateTip: pending && item.lateMinutes > 0 ? `${item.lateMinutes} 分钟后签到记为迟到` : '',
    progressText: item.totalCount > 0 ? `已签 ${item.checkedCount}/${item.totalCount}` : '',
    checked: item.checked,
    myStatus: item.myStatus
  }
}

function sortList(list) {
  return list.sort((a, b) => {
    const rank = (x) => (x.pending ? 0 : x.checked ? 1 : 2)
    const diff = rank(a) - rank(b)
    if (diff !== 0) return diff
    return 0
  })
}

Page({
  data: {
    checkins: [],
    inputCodes: {},
    inputErrors: {},
    focusId: 0,
    activeCount: 0,
    hasQr: false,
    submittingId: 0,
    loading: false
  },

  rawList: [],

  onLoad(options) {
    const opts = options || {}
    const focusId = opts.focusId ? Number(opts.focusId) : 0
    const presetCode = opts.code ? String(opts.code) : ''
    if (focusId) {
      this.setData({ focusId })
    }
    if (presetCode) {
      this.presetCode = presetCode
      if (focusId) this.setData({ ['inputCodes.' + focusId]: presetCode })
    }
  },

  onShow() {
    this.loadCheckins()
    this.startTimer()
  },

  onHide() {
    this.stopTimer()
  },

  onUnload() {
    this.stopTimer()
  },

  startTimer() {
    this.stopTimer()
    this.timer = setInterval(() => this.refreshCountdown(), 30000)
  },

  stopTimer() {
    if (this.timer) {
      clearInterval(this.timer)
      this.timer = null
    }
  },

  refreshCountdown() {
    if (this.rawList.length === 0) return
    const hasExpire = this.rawList.some(i => i.status === 1 && !i.checked && i.expireTime)
    if (!hasExpire) return
    const checkins = sortList(this.rawList.map(i => decorate(i)))
    this.setData({ checkins })
  },

  async loadCheckins() {
    this.setData({ loading: true })
    try {
      const res = await getMyCheckins()
      const list = res.data || []
      this.rawList = list
      const checkins = sortList(list.map(i => decorate(i)))
      const activeCount = checkins.filter(i => i.pending).length
      const hasQr = checkins.some(i => i.pending && i.isQr)
      const inputCodes = {}
      checkins.forEach(item => {
        inputCodes[item.id] = this.data.inputCodes[item.id] || ''
      })
      if (this.presetCode) {
        let targetId = this.data.focusId
        if (!targetId) {
          const target = checkins.find(i => i.pending && i.isNumber)
          if (target) targetId = target.id
        }
        if (targetId) {
          inputCodes[targetId] = this.presetCode
          if (!this.data.focusId) this.setData({ focusId: targetId })
        }
      }
      this.setData({ checkins, inputCodes, activeCount, hasQr, loading: false }, () => {
        if (this.data.focusId) {
          wx.pageScrollTo({ selector: '#ck-' + this.data.focusId, duration: 300, offsetTop: -24 })
        }
      })
    } catch (e) {
      this.setData({ loading: false })
    }
  },

  onCodeInput(e) {
    const id = e.currentTarget.dataset.id
    this.setData({
      [`inputCodes.${id}`]: e.detail.value,
      [`inputErrors.${id}`]: ''
    })
  },

  async handleSubmit(e) {
    const id = Number(e.currentTarget.dataset.id)
    const code = this.data.inputCodes[id]
    if (!code) {
      wx.showToast({ title: '请输入签到码', icon: 'none' })
      return
    }
    if (this.data.submittingId) return
    this.setData({ submittingId: id, [`inputErrors.${id}`]: '' })
    try {
      const status = await checkinUtil.submitWithLocation(id, code, 'number')
      this.presetCode = ''
      this.setData({ submittingId: 0 })
      checkinUtil.showCheckinSuccess(status)
      this.loadCheckins()
    } catch (err) {
      const info = checkinUtil.describeFailure(err)
      this.setData({ submittingId: 0 })
      if (info.kind === checkinUtil.FAILURE.EXPIRED) {
        checkinUtil.showCheckinError(err, { onRefresh: () => this.loadCheckins() })
        return
      }
      this.setData({ [`inputErrors.${id}`]: info.inline })
    }
  },

  handleScan() {
    wx.scanCode({
      onlyFromCamera: false,
      scanType: ['qrCode'],
      success: async (res) => {
        const payload = checkinUtil.parseQrPayload(res.result)
        if (!payload) {
          wx.showToast({ title: '未识别到内容', icon: 'none' })
          return
        }
        if (payload.kind === 'live') {
          wx.navigateTo({ url: `/pages/live/live?sessionId=${payload.sessionId}&code=${payload.code}` })
          return
        }
        if (payload.kind === 'code') {
          wx.showLoading({ title: '正在签到', mask: true })
          try {
            const status = await checkinUtil.submitByCodeWithLocation(payload.code, 'number')
            wx.hideLoading()
            this.presetCode = ''
            checkinUtil.showCheckinSuccess(status)
            this.loadCheckins()
          } catch (e) {
            wx.hideLoading()
            checkinUtil.showCheckinError(e, {
              onRefresh: () => this.loadCheckins()
            })
          }
          return
        }
        if (payload.kind === 'checkin') {
          const target = this.data.checkins.find(i => i.id === payload.checkinId)
          if (!target || !target.pending) {
            checkinUtil.showCheckinError(new Error('签到已结束'), {
              onRefresh: () => this.loadCheckins()
            })
            return
          }
          if (!target.isQr) {
            wx.showModal({
              title: '该签到仅支持数字签到',
              content: '老师发布的是数字签到，请直接输入 6 位签到码完成',
              confirmText: '去输码',
              cancelText: '取消',
              success: (r) => {
                if (!r.confirm) return
                this.setData({ focusId: target.id })
                wx.pageScrollTo({ selector: '#ck-' + target.id, duration: 300, offsetTop: -24 })
              }
            })
            return
          }
          wx.showLoading({ title: '正在签到', mask: true })
          try {
            const status = await checkinUtil.submitWithLocation(payload.checkinId, payload.code, 'qr', payload.token)
            wx.hideLoading()
            this.presetCode = ''
            checkinUtil.showCheckinSuccess(status)
            this.loadCheckins()
          } catch (e) {
            wx.hideLoading()
            checkinUtil.showCheckinError(e, {
              onRefresh: () => this.loadCheckins()
            })
          }
          return
        }
        checkinUtil.showCheckinError(new Error('不是有效的签到二维码'), {
          onRefresh: () => this.loadCheckins()
        })
      },
      fail: () => {}
    })
  },

  goScan() {
    this.handleScan()
  }
})