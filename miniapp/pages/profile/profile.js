Page({
  data: {
    userInfo: null,
    roleLabel: '',
    avatarLetter: ''
  },

  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({ selected: 'profile' })
    }
    const userInfo = wx.getStorageSync('userInfo')
    if (!userInfo) {
      wx.reLaunch({ url: '/pages/login/login' })
      return
    }
    const roleMap = { student: '学生', teacher: '教师', admin: '管理员' }
    const name = userInfo.nickname || userInfo.username || ''
    this.setData({
      userInfo,
      roleLabel: roleMap[userInfo.role] || '未知',
      avatarLetter: name.charAt(0).toUpperCase()
    })
  },

  goAdmin() {
    wx.navigateTo({ url: '/pages/admin-review/admin-review' })
  },

  goTeacherCheckin() {
    wx.switchTab({ url: '/pages/teacher-checkin/teacher-checkin' })
  },

  goAppeal() {
    wx.navigateTo({ url: '/pages/appeal/appeal' })
  },

  handleLogout() {
    wx.showModal({
      title: '提示',
      content: '确定退出登录吗？',
      success(res) {
        if (res.confirm) {
          wx.removeStorageSync('token')
          wx.removeStorageSync('userInfo')
          wx.reLaunch({ url: '/pages/login/login' })
        }
      }
    })
  }
})