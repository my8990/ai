const api = require('../../utils/request')

Page({
  data: {
    role: 'student',
    username: '',
    password: '',
    nickname: '',
    phone: '',
    studentNo: '',
    college: ''
  },

  onRoleChange(e) {
    this.setData({ role: e.detail.value })
  },

  onInput(e) {
    const field = e.currentTarget.dataset.field
    this.setData({ [field]: e.detail.value })
  },

  async handleRegister() {
    const { role, username, password, nickname, phone, studentNo, college } = this.data
    if (!username) {
      wx.showToast({ title: '请输入用户名', icon: 'none' })
      return
    }
    if (!password || password.length < 6 || password.length > 20) {
      wx.showToast({ title: '密码长度6-20位', icon: 'none' })
      return
    }

    const data = { role, username, password }
    if (nickname) data.nickname = nickname
    if (phone) data.phone = phone
    if (role === 'student' && studentNo) data.studentNo = studentNo
    if (role === 'teacher' && college) data.college = college

    wx.showLoading({ title: '注册中' })
    try {
      await api.register(data)
      wx.hideLoading()
      wx.showToast({ title: '注册成功', icon: 'success' })
      setTimeout(() => {
        wx.navigateBack()
      }, 1000)
    } catch {
      wx.hideLoading()
    }
  },

  goToLogin() {
    wx.navigateBack()
  }
})