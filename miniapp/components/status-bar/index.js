Component({
  data: {
    height: 0
  },
  lifetimes: {
    attached() {
      const info = wx.getWindowInfo ? wx.getWindowInfo() : wx.getSystemInfoSync()
      this.setData({ height: (info && info.statusBarHeight) || 20 })
    }
  }
})