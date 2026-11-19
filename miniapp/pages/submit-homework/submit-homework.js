const { getHomeworkDetail, getMySubmit, submitHomework, uploadFile } = require('../../utils/request')

Page({
  data: {
    homeworkId: '',
    homework: {},
    mySubmit: null,
    form: { content: '', images: '', fileUrl: '', fileName: '' },
    imageList: [],
    submitting: false
  },

  onLoad(options) {
    this.setData({ homeworkId: options.id })
  },

  onShow() {
    this.loadData()
  },

  async loadData() {
    try {
      const [detailRes, submitRes] = await Promise.all([
        getHomeworkDetail(this.data.homeworkId),
        getMySubmit(this.data.homeworkId)
      ])
      const hw = detailRes.data
      let mySubmit = null
      let form = { content: '', images: '', fileUrl: '', fileName: '' }
      let imageList = []
      if (submitRes.data) {
        mySubmit = submitRes.data
        form.content = mySubmit.content || ''
        form.fileUrl = mySubmit.fileUrl || ''
        form.fileName = mySubmit.fileName || ''
        if (mySubmit.images) {
          imageList = mySubmit.images.split(',')
          form.images = mySubmit.images
        }
      }
      this.setData({ homework: hw, mySubmit, form, imageList })
    } catch (e) { /* handled */ }
  },

  onContentInput(e) {
    this.setData({ 'form.content': e.detail.value })
  },

  chooseImage() {
    wx.chooseImage({
      count: 9 - this.data.imageList.length,
      sizeType: ['compressed'],
      sourceType: ['album', 'camera'],
      success: async (res) => {
        for (const tempPath of res.tempFilePaths) {
          try {
            const uploadRes = await uploadFile(tempPath)
            const url = uploadRes.data
            const imageList = this.data.imageList.concat(url)
            this.setData({ imageList, 'form.images': imageList.join(',') })
          } catch (e) { /* handled */ }
        }
      }
    })
  },

  removeImage(e) {
    const idx = e.currentTarget.dataset.idx
    const imageList = this.data.imageList.filter((_, i) => i !== idx)
    this.setData({ imageList, 'form.images': imageList.join(',') })
  },

  previewImage(e) {
    const idx = e.currentTarget.dataset.idx
    wx.previewImage({ current: this.data.imageList[idx], urls: this.data.imageList })
  },

  chooseFile() {
    wx.chooseMessageFile({
      count: 1,
      success: async (res) => {
        const tempPath = res.tempFiles[0].path
        const fileName = res.tempFiles[0].name
        try {
          const uploadRes = await uploadFile(tempPath)
          this.setData({ 'form.fileUrl': uploadRes.data, 'form.fileName': fileName })
          wx.showToast({ title: '上传成功', icon: 'success' })
        } catch (e) { /* handled */ }
      }
    })
  },

  removeFile() {
    this.setData({ 'form.fileUrl': '', 'form.fileName': '' })
  },

  async handleSubmit() {
    if (this.data.submitting) return
    this.setData({ submitting: true })
    try {
      await submitHomework({
        homeworkId: Number(this.data.homeworkId),
        content: this.data.form.content,
        images: this.data.form.images,
        fileUrl: this.data.form.fileUrl,
        fileName: this.data.form.fileName
      })
      wx.showToast({ title: '提交成功', icon: 'success' })
      wx.vibrateShort()
      this.loadData()
    } catch (e) { /* handled */ }
    this.setData({ submitting: false })
  }
})