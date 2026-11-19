const TAB_SETS = {
  student: [
    { key: 'home', text: '首页', pagePath: '/pages/index/index' },
    { key: 'course', text: '课程', pagePath: '/pages/course/course' },
    { key: 'feed', text: '动态', pagePath: '/pages/feed/feed' },
    { key: 'profile', text: '我的', pagePath: '/pages/profile/profile' }
  ],
  teacher: [
    { key: 'home', text: '首页', pagePath: '/pages/index/index' },
    { key: 'course', text: '课程', pagePath: '/pages/course/course' },
    { key: 'checkin', text: '签到', pagePath: '/pages/teacher-checkin/teacher-checkin' },
    { key: 'profile', text: '我的', pagePath: '/pages/profile/profile' }
  ],
  admin: [
    { key: 'home', text: '首页', pagePath: '/pages/index/index' },
    { key: 'course', text: '课程', pagePath: '/pages/course/course' },
    { key: 'profile', text: '我的', pagePath: '/pages/profile/profile' }
  ]
}

Component({
  data: {
    selected: 'home',
    list: TAB_SETS.student
  },
  lifetimes: {
    attached() {
      this.applyRole()
    }
  },
  methods: {
    applyRole() {
      const userInfo = wx.getStorageSync('userInfo')
      const role = userInfo ? userInfo.role : 'student'
      this.setData({ list: TAB_SETS[role] || TAB_SETS.student })
    },
    switchTab(e) {
      const path = e.currentTarget.dataset.path
      wx.switchTab({ url: path })
    }
  }
})