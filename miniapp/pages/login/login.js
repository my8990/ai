const api = require('../../utils/request')

Page({
  data: {
    username: '',
    password: ''
  },

  onInputUsername(e) {
    this.setData({ username: e.detail.value })
  },

  onInputPassword(e) {
    this.setData({ password: e.detail.value })
  },

  async handleLogin() {
    const { username, password } = this.data
    if (!username) {
      wx.showToast({ title: '请输入用户名', icon: 'none' })
      return
    }
    if (!password) {
      wx.showToast({ title: '请输入密码', icon: 'none' })
      return
    }

    wx.showLoading({ title: '登录中' })
    try {
      const res = await api.login({ username, password })
      wx.setStorageSync('token', res.data.token)
      wx.setStorageSync('userInfo', res.data)
      wx.hideLoading()
      wx.showToast({ title: '登录成功', icon: 'success' })
      setTimeout(() => {
        wx.reLaunch({ url: '/pages/index/index' })
      }, 1000)
    } catch {
      wx.hideLoading()
    }
  },

  async handleWxLogin() {
    wx.showLoading({ title: '微信登录中' })
    try {
      const loginRes = await new Promise((resolve, reject) => {
        wx.login({
          success: resolve,
          fail: reject
        })
      })

      const res = await api.wxLogin(loginRes.code)
      wx.setStorageSync('token', res.data.token)
      wx.setStorageSync('userInfo', res.data)
      wx.hideLoading()
      wx.showToast({ title: '登录成功', icon: 'success' })
      setTimeout(() => {
        wx.reLaunch({ url: '/pages/index/index' })
      }, 1000)
    } catch {
      wx.hideLoading()
    }
  },

  goToRegister() {
    wx.navigateTo({ url: '/pages/register/register' })
  }
})