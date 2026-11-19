const { getMyCheckins, submitCheckin, submitCheckinByCode, reverseGeocode, getActiveLives } = require('./request')

const STATE = {
  NETWORK: 'NETWORK',
  NO_ACTIVE: 'NO_ACTIVE',
  QR_ONLY: 'QR_ONLY',
  NUMBER_ONLY: 'NUMBER_ONLY',
  READY: 'READY'
}

const MESSAGE = {
  NO_ACTIVE: { title: '当前暂无签到活动', content: '老师发布签到后即可签到' },
  QR_ONLY: { title: '需扫码签到', content: '当前签到活动只能通过扫老师出示的二维码完成' },
  NUMBER_ONLY: { title: '需输入签到码', content: '当前签到活动只能输入老师给出的数字签到码' }
}

function isNumberType(type) {
  return type === 1 || type === 3
}

function isQrType(type) {
  return type === 2 || type === 3
}

function isActive(item) {
  const absent = item.myStatus === 3 || item.myStatus === 4
  return item.status === 1 && !item.checked && !absent
}

function classify(list) {
  const items = list || []
  const active = items.filter(isActive)
  const activeNum = active.filter(i => isNumberType(i.type)).length
  const activeQr = active.filter(i => isQrType(i.type)).length
  let state
  if (active.length === 0) {
    state = STATE.NO_ACTIVE
  } else if (activeNum > 0 && activeQr > 0) {
    state = STATE.READY
  } else if (activeNum > 0) {
    state = STATE.NUMBER_ONLY
  } else {
    state = STATE.QR_ONLY
  }
  return { state, activeCount: active.length, activeNum, activeQr, list: items }
}

async function fetchClassify() {
  try {
    const res = await getMyCheckins()
    return classify(res.data || [])
  } catch (e) {
    return { state: STATE.NETWORK, activeCount: 0, activeNum: 0, activeQr: 0, list: [] }
  }
}

function currentPage() {
  const pages = getCurrentPages()
  return pages.length ? pages[pages.length - 1] : null
}

function showNotice(options) {
  const page = currentPage()
  const popup = page && page.selectComponent ? page.selectComponent('#noticePopup') : null
  if (popup && popup.open) {
    popup.open(options)
    return
  }
  wx.showToast({ title: options.title, icon: 'none', duration: 2000 })
}

function showNoActive() {
  const msg = MESSAGE.NO_ACTIVE
  showNotice({ title: msg.title, content: msg.content })
}

const FAILURE = {
  EXPIRED: 'EXPIRED',
  WRONG_CODE: 'WRONG_CODE',
  NEED_QR: 'NEED_QR',
  NEED_NUMBER: 'NEED_NUMBER',
  DUPLICATE: 'DUPLICATE',
  NOT_MEMBER: 'NOT_MEMBER',
  NETWORK: 'NETWORK',
  UNKNOWN: 'UNKNOWN'
}

function describeFailure(err) {
  const msg = err && err.message ? String(err.message) : ''
  if (/仅支持扫码/.test(msg)) {
    return {
      kind: FAILURE.NEED_QR,
      title: '该签到需扫码',
      content: '老师发布的是二维码签到，请扫描老师出示的二维码完成',
      inline: '该签到仅支持扫码，请扫描二维码'
    }
  }
  if (/仅支持数字/.test(msg)) {
    return {
      kind: FAILURE.NEED_NUMBER,
      title: '该签到需输码',
      content: '老师发布的是数字签到，请直接输入 6 位签到码完成',
      inline: '该签到仅支持数字签到码'
    }
  }
  if (/已签到/.test(msg)) {
    return {
      kind: FAILURE.DUPLICATE,
      title: '已完成签到',
      content: '你已完成本次签到，无需重复签到',
      inline: '你已完成本次签到'
    }
  }
  if (/未加入/.test(msg)) {
    return {
      kind: FAILURE.NOT_MEMBER,
      title: '无法签到',
      content: '你尚未加入该课程，无法参与本次签到',
      inline: '你尚未加入该课程'
    }
  }
  if (/过期/.test(msg)) {
    return {
      kind: FAILURE.EXPIRED,
      title: '二维码已过期',
      content: '该签到活动已结束，二维码已失效，请联系老师重新发起签到',
      inline: '二维码已过期'
    }
  }
  if (/已结束/.test(msg)) {
    return {
      kind: FAILURE.EXPIRED,
      title: '签到已结束',
      content: '该签到活动已结束，无法继续签到',
      inline: '签到已结束'
    }
  }
  if (/二维码/.test(msg)) {
    return {
      kind: FAILURE.UNKNOWN,
      title: '无法识别二维码',
      content: '请扫描老师出示的签到二维码，或改用签到码输入',
      inline: '无法识别该二维码'
    }
  }
  if (/签到码/.test(msg)) {
    return {
      kind: FAILURE.WRONG_CODE,
      title: '签到码错误',
      content: '请核对老师给出的 6 位数字签到码，重新输入后提交',
      inline: '签到码错误，请核对后重新输入'
    }
  }
  if (/网络/.test(msg)) {
    return {
      kind: FAILURE.NETWORK,
      title: '网络异常',
      content: '请检查网络连接后重试',
      inline: '网络异常，请稍后重试'
    }
  }
  return {
    kind: FAILURE.UNKNOWN,
    title: '签到失败',
    content: msg || '请稍后重试，如仍无法签到请联系老师',
    inline: msg || '签到失败，请重试'
  }
}

function showCheckinError(err, handlers) {
  const h = handlers || {}
  const info = describeFailure(err)
  let actionText = ''
  let onAction = null
  if (info.kind === FAILURE.WRONG_CODE && h.onRetryCode) {
    actionText = '重新输入'
    onAction = h.onRetryCode
  } else if (info.kind === FAILURE.NEED_QR && h.onScan) {
    actionText = '去扫码'
    onAction = h.onScan
  } else if (info.kind === FAILURE.NEED_NUMBER && h.onRetryCode) {
    actionText = '去输码'
    onAction = h.onRetryCode
  } else if (info.kind === FAILURE.EXPIRED && h.onRefresh) {
    actionText = '刷新签到列表'
    onAction = h.onRefresh
  } else if (info.kind === FAILURE.NETWORK && h.onRefresh) {
    actionText = '重试'
    onAction = h.onRefresh
  }
  showNotice({
    title: info.title,
    content: info.content,
    actionText,
    onAction,
    variant: 'error'
  })
}

function showMethodHint(options) {
  const page = currentPage()
  const popup = page && page.selectComponent ? page.selectComponent('#noticePopup') : null
  if (popup && popup.open) {
    popup.open({
      title: options.title,
      content: options.content,
      actionText: options.confirmText,
      onAction: options.onConfirm
    })
    return
  }
  wx.showModal({
    title: options.title,
    content: options.content,
    confirmText: options.confirmText || '知道了',
    showCancel: !!options.onConfirm,
    success: (res) => {
      if (res.confirm && options.onConfirm) options.onConfirm()
    }
  })
}

function getLocation() {
  return new Promise((resolve) => {
    wx.getLocation({
      type: 'gcj02',
      isHighAccuracy: true,
      highAccuracyExpireTime: 10000,
      success: async (res) => {
        const lat = res.latitude
        const lng = res.longitude
        const accuracy = res.accuracy
        console.log('[签到定位] lat=' + lat + ', lng=' + lng + ', accuracy=' + accuracy + 'm')

        // 精度太差（> 500m，大概率是基站定位），尝试再取一次高精度
        if (accuracy && accuracy > 500) {
          console.warn('[签到定位] 精度较差(' + accuracy + 'm)，重试高精度定位...')
          try {
            const retry = await new Promise((r) => {
              wx.getLocation({
                type: 'gcj02',
                isHighAccuracy: true,
                highAccuracyExpireTime: 15000,
                success: r,
                fail: r
              })
            })
            if (retry && retry.latitude && (!retry.accuracy || retry.accuracy < accuracy)) {
              console.log('[签到定位] 重试成功: accuracy=' + retry.accuracy + 'm')
              lat = retry.latitude
              lng = retry.longitude
            }
          } catch (e) { /* 重试失败，用原来的 */ }
        }

        let address = ''
        try {
          const geo = await reverseGeocode(lat, lng)
          address = geo ? (geo.poiName || geo.formattedAddress) : ''
        } catch (e) { /* 逆地理失败不影响签到 */ }

        resolve({ latitude: lat, longitude: lng, address })
      },
      fail: () => resolve({})
    })
  })
}

async function submitWithLocation(checkinId, code, source, token) {
  const loc = await getLocation()
  await submitCheckin({
    checkinId,
    code,
    source: source || 'number',
    token: token || '',
    latitude: loc.latitude,
    longitude: loc.longitude,
    address: loc.address || ''
  })
  const status = await queryMyStatus(checkinId)
  enterLiveFor(checkinId)
  return status
}

async function submitByCodeWithLocation(code, source) {
  const loc = await getLocation()
  const res = await submitCheckinByCode({
    code,
    source: source || 'number',
    latitude: loc.latitude,
    longitude: loc.longitude,
    address: loc.address || ''
  })
  const checkinId = res && res.data ? Number(res.data) : 0
  const status = await queryMyStatus(checkinId)
  if (checkinId) enterLiveFor(checkinId)
  return status
}

async function enterLiveFor(checkinId) {
  try {
    const res = await getActiveLives()
    const session = (res.data || []).find(s => s.checkinId === checkinId)
    if (!session) return
    const pages = getCurrentPages()
    const current = pages.length ? pages[pages.length - 1] : null
    if (current && current.route === 'pages/live/live') return
    wx.navigateTo({ url: `/pages/live/live?sessionId=${session.id}` })
  } catch (e) { /* 无进行中课堂时静默 */ }
}

async function queryMyStatus(checkinId) {
  try {
    const res = await getMyCheckins()
    const list = res.data || []
    const target = list.find(i => i.id === checkinId)
    if (!target) return { late: false, checked: true }
    return { late: target.myStatus === 2, checked: !!target.checked }
  } catch (e) {
    return { late: false, checked: true }
  }
}

function showCheckinSuccess(result) {
  wx.vibrateShort()
  wx.showToast({
    title: result && result.late ? '签到成功（迟到）' : '签到成功',
    icon: 'success'
  })
}

function parseQrPayload(raw) {
  if (!raw) return null
  const text = String(raw)
  const liveMatch = text.match(/^live:\/\/(\d+)\?code=(\d+)$/)
  if (liveMatch) {
    return { kind: 'live', sessionId: liveMatch[1], code: liveMatch[2] }
  }
  const checkinMatch = text.match(/^checkin:\/\/(\d+)\?code=(\d+)(?:&token=([A-Za-z0-9_-]+))?$/)
  if (checkinMatch) {
    return {
      kind: 'checkin',
      checkinId: Number(checkinMatch[1]),
      code: checkinMatch[2],
      token: checkinMatch[3] || ''
    }
  }
  if (/^\d{6}$/.test(text)) {
    return { kind: 'code', code: text }
  }
  return { kind: 'invalid' }
}

function findById(list, id) {
  return (list || []).find(i => i.id === id) || null
}

function openNumberInput(result, onDone) {
  const page = currentPage()
  const popup = page && page.selectComponent ? page.selectComponent('#codeInputPopup') : null

  const submitCode = async (code) => {
    try {
      const status = await submitByCodeWithLocation(code, 'number')
      showCheckinSuccess(status)
      if (onDone) onDone()
    } catch (e) {
      const info = describeFailure(e)
      if (info.kind === FAILURE.EXPIRED) {
        if (popup && popup.close) popup.close()
        showCheckinError(e, { onRefresh: () => reopen() })
        return
      }
      throw new Error(info.inline)
    }
  }

  const reopen = async () => {
    const fresh = await fetchClassify()
    if (fresh.state === STATE.NETWORK) {
      wx.showToast({ title: '网络异常，请稍后重试', icon: 'none' })
      return
    }
    if (fresh.state === STATE.NO_ACTIVE) {
      showNoActive()
      return
    }
    openNumberInput(fresh, onDone)
  }

  if (popup && popup.open) {
    popup.open({ onSubmit: submitCode })
    return
  }

  wx.showModal({
    title: '数字签到',
    editable: true,
    placeholderText: '请输入老师给出的 6 位签到码',
    confirmText: '签到',
    cancelText: '取消',
    success: async (res) => {
      if (!res.confirm) return
      const code = String(res.content || '').trim()
      if (!/^\d{6}$/.test(code)) {
        wx.showToast({ title: '请输入 6 位数字签到码', icon: 'none' })
        return
      }
      wx.showLoading({ title: '正在签到', mask: true })
      try {
        await submitCode(code)
        wx.hideLoading()
      } catch (e) {
        wx.hideLoading()
        wx.showToast({ title: describeFailure(e).inline, icon: 'none' })
      }
    }
  })
}

module.exports = {
  STATE,
  MESSAGE,
  FAILURE,
  isNumberType,
  isQrType,
  isActive,
  classify,
  fetchClassify,
  showNotice,
  showNoActive,
  showMethodHint,
  describeFailure,
  showCheckinError,
  getLocation,
  submitWithLocation,
  submitByCodeWithLocation,
  queryMyStatus,
  showCheckinSuccess,
  parseQrPayload,
  findById,
  openNumberInput
}