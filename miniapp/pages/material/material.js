const { getCourseMaterials, deleteMaterial, toggleMaterial, createMaterial, uploadFile } = require('../../utils/request')

Page({
  data: {
    courseId: '',
    list: [],
    role: '',
    loading: true,
    uploading: false
  },

  onLoad(options) {
    const userInfo = wx.getStorageSync('userInfo')
    this.setData({ courseId: options.id, role: userInfo ? userInfo.role : '' })
  },

  onShow() {
    this.loadList()
  },

  async loadList() {
    this.setData({ loading: true })
    try {
      const res = await getCourseMaterials(this.data.courseId)
      const list = (res.data || []).map(item => {
        item.fileIcon = this.fileIcon(item.fileType, item.fileName)
        item.sizeText = this.formatSize(item.fileSize)
        item.timeText = (item.createTime || '').replace('T', ' ').slice(0, 16)
        item.isImage = this.isImage(item.fileType, item.fileName)
        return item
      })
      this.setData({ list })
    } catch (e) { /* handled */ } finally {
      this.setData({ loading: false })
    }
  },

  isImage(fileType, fileName) {
    const type = (fileType || '').toLowerCase()
    const name = (fileName || '').toLowerCase()
    return type.indexOf('image') >= 0 || /\.(jpg|jpeg|png|gif|webp)$/.test(name)
  },

  fileIcon(fileType, fileName) {
    const name = (fileName || '').toLowerCase()
    if (this.isImage(fileType, fileName)) return 'IMG'
    if (name.endsWith('.pdf')) return 'PDF'
    if (name.endsWith('.doc') || name.endsWith('.docx')) return 'DOC'
    if (name.endsWith('.ppt') || name.endsWith('.pptx')) return 'PPT'
    if (name.endsWith('.xls') || name.endsWith('.xlsx')) return 'XLS'
    return 'FILE'
  },

  formatSize(size) {
    if (!size) return ''
    if (size < 1024) return size + ' B'
    if (size < 1024 * 1024) return (size / 1024).toFixed(1) + ' KB'
    return (size / 1024 / 1024).toFixed(1) + ' MB'
  },

  openFile(e) {
    const item = this.data.list[e.currentTarget.dataset.index]
    if (!item || !item.fileUrl) return
    if (item.isImage) {
      wx.previewImage({ urls: [item.fileUrl], current: item.fileUrl })
      return
    }
    wx.showLoading({ title: '打开中...' })
    wx.downloadFile({
      url: item.fileUrl,
      success: (res) => {
        if (res.statusCode !== 200) {
          wx.hideLoading()
          wx.showToast({ title: '文件下载失败', icon: 'none' })
          return
        }
        const name = (item.fileName || item.fileUrl || '').toLowerCase()
        let fileType = 'pdf'
        if (name.endsWith('.doc') || name.endsWith('.docx')) fileType = 'doc'
        else if (name.endsWith('.ppt') || name.endsWith('.pptx')) fileType = 'ppt'
        else if (name.endsWith('.xls') || name.endsWith('.xlsx')) fileType = 'xls'
        wx.openDocument({
          filePath: res.tempFilePath,
          fileType,
          showMenu: true,
          complete: () => wx.hideLoading()
        })
      },
      fail: () => {
        wx.hideLoading()
        wx.showToast({ title: '文件下载失败', icon: 'none' })
      }
    })
  },

  chooseFile() {
    if (this.data.role !== 'teacher') return
    wx.chooseMessageFile({
      count: 1,
      type: 'file',
      extension: ['pdf', 'jpg', 'jpeg', 'png', 'ppt', 'pptx', 'doc', 'docx'],
      success: async (res) => {
        const file = res.tempFiles[0]
        wx.showLoading({ title: '上传中...' })
        try {
          const up = await uploadFile(file.path)
          wx.hideLoading()
          this.pendingFile = { fileUrl: up.data, fileName: file.name, fileSize: file.size }
          this.setData({ showCreate: true, createForm: { title: file.name.replace(/\.[^.]+$/, '') } })
        } catch (e) {
          wx.hideLoading()
        }
      }
    })
  },

  hideCreate() {
    this.setData({ showCreate: false })
  },

  onTitleInput(e) {
    this.setData({ 'createForm.title': e.detail.value })
  },

  async handleCreate() {
    const title = this.data.createForm.title
    if (!title) {
      wx.showToast({ title: '请输入课件标题', icon: 'none' })
      return
    }
    if (!this.pendingFile) {
      wx.showToast({ title: '请先选择文件', icon: 'none' })
      return
    }
    const name = (this.pendingFile.fileName || '').toLowerCase()
    let fileType = 'application/octet-stream'
    if (/\.pdf$/.test(name)) fileType = 'application/pdf'
    else if (/\.(jpg|jpeg|png|gif|webp)$/.test(name)) fileType = 'image/*'
    try {
      await createMaterial({
        courseId: Number(this.data.courseId),
        title,
        fileUrl: this.pendingFile.fileUrl,
        fileName: this.pendingFile.fileName,
        fileSize: this.pendingFile.fileSize,
        fileType
      })
      wx.showToast({ title: '上传成功', icon: 'success' })
      this.pendingFile = null
      this.setData({ showCreate: false, createForm: { title: '' } })
      this.loadList()
    } catch (e) { /* handled */ }
  },

  handleDelete(e) {
    const id = e.currentTarget.dataset.id
    wx.showModal({
      title: '删除课件',
      content: '确认删除该课件？',
      success: async (res) => {
        if (!res.confirm) return
        try {
          await deleteMaterial(id)
          wx.showToast({ title: '已删除', icon: 'success' })
          this.loadList()
        } catch (e) { /* handled */ }
      }
    })
  },

  handleToggle(e) {
    const id = e.currentTarget.dataset.id
    toggleMaterial(id).then(() => {
      wx.showToast({ title: '操作成功', icon: 'success' })
      this.loadList()
    }).catch(() => {})
  },

  preventBubble() {}
})