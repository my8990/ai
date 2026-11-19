const { getCoursePosts, createPost, deletePost, commentPost, deletePostComment, likePost, uploadFile } = require('../../utils/request')

Page({
  data: {
    courseId: '',
    role: '',
    list: [],
    loading: true,
    showCreate: false,
    createForm: { content: '', images: [] },
    commentInputs: {},
    authorFilter: 'all',
    timeFilter: 'all',
    authorOptions: [
      { key: 'all', label: '全部' },
      { key: 'teacher', label: '老师' },
      { key: 'student', label: '学生' }
    ],
    timeOptions: [
      { key: 'all', label: '全部时间' },
      { key: 'today', label: '今天' },
      { key: 'week', label: '近一周' },
      { key: 'month', label: '近一月' }
    ]
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
      const params = {}
      if (this.data.authorFilter !== 'all') params.authorRole = this.data.authorFilter
      if (this.data.timeFilter !== 'all') params.timeRange = this.data.timeFilter
      const res = await getCoursePosts(this.data.courseId, params)
      const list = (res.data || []).map(item => {
        item.timeText = (item.createTime || '').replace('T', ' ').slice(0, 16)
        item.letter = (item.authorName || '?').charAt(0).toUpperCase()
        item.isTeacher = item.authorRole === 'teacher'
        item.comments = (item.comments || []).map(c => {
          c.timeText = (c.createTime || '').replace('T', ' ').slice(0, 16)
          c.letter = (c.authorName || '?').charAt(0).toUpperCase()
          c.isTeacher = c.authorRole === 'teacher'
          return c
        })
        return item
      })
      const commentInputs = {}
      list.forEach(item => { commentInputs[item.id] = '' })
      this.setData({ list, commentInputs })
    } catch (e) { /* handled */ } finally {
      this.setData({ loading: false })
    }
  },

  changeAuthorFilter(e) {
    const key = e.currentTarget.dataset.key
    if (key === this.data.authorFilter) return
    this.setData({ authorFilter: key })
    this.loadList()
  },

  changeTimeFilter(e) {
    const key = e.currentTarget.dataset.key
    if (key === this.data.timeFilter) return
    this.setData({ timeFilter: key })
    this.loadList()
  },

  showCreate() {
    this.setData({ showCreate: true })
  },

  hideCreate() {
    this.setData({ showCreate: false })
  },

  onContentInput(e) {
    this.setData({ 'createForm.content': e.detail.value })
  },

  chooseImages() {
    const remain = 9 - this.data.createForm.images.length
    if (remain <= 0) {
      wx.showToast({ title: '最多9张图片', icon: 'none' })
      return
    }
    wx.chooseMedia({
      count: remain,
      mediaType: ['image'],
      success: async (res) => {
        wx.showLoading({ title: '上传中...' })
        try {
          const urls = []
          for (const f of res.tempFiles) {
            const up = await uploadFile(f.tempFilePath)
            urls.push(up.data)
          }
          this.setData({ 'createForm.images': this.data.createForm.images.concat(urls) })
          wx.hideLoading()
        } catch (e) {
          wx.hideLoading()
        }
      }
    })
  },

  removeImage(e) {
    const idx = e.currentTarget.dataset.index
    const images = this.data.createForm.images.slice()
    images.splice(idx, 1)
    this.setData({ 'createForm.images': images })
  },

  previewImage(e) {
    const { index, urls } = e.currentTarget.dataset
    wx.previewImage({ urls, current: urls[index] })
  },

  async handleCreate() {
    const content = this.data.createForm.content.trim()
    if (!content) {
      wx.showToast({ title: '请输入内容', icon: 'none' })
      return
    }
    try {
      await createPost({
        courseId: Number(this.data.courseId),
        content,
        images: this.data.createForm.images
      })
      wx.showToast({ title: '发布成功', icon: 'success' })
      this.setData({ showCreate: false, createForm: { content: '', images: [] } })
      this.loadList()
    } catch (e) { /* handled */ }
  },

  onCommentInput(e) {
    const id = e.currentTarget.dataset.id
    this.setData({ [`commentInputs.${id}`]: e.detail.value })
  },

  async handleComment(e) {
    const id = e.currentTarget.dataset.id
    const content = (this.data.commentInputs[id] || '').trim()
    if (!content) {
      wx.showToast({ title: '请输入评论', icon: 'none' })
      return
    }
    try {
      await commentPost({ postId: id, content })
      this.setData({ [`commentInputs.${id}`]: '' })
      this.loadList()
    } catch (e) { /* handled */ }
  },

  async handleLike(e) {
    const id = e.currentTarget.dataset.id
    try {
      await likePost(id)
      this.loadList()
    } catch (e) { /* handled */ }
  },

  handleDelete(e) {
    const id = e.currentTarget.dataset.id
    wx.showModal({
      title: '删除帖子',
      content: '确认删除该帖子？',
      success: async (res) => {
        if (!res.confirm) return
        try {
          await deletePost(id)
          wx.showToast({ title: '已删除', icon: 'success' })
          this.loadList()
        } catch (e) { /* handled */ }
      }
    })
  },

  handleDeleteComment(e) {
    const id = e.currentTarget.dataset.id
    wx.showModal({
      title: '删除评论',
      content: '确认删除该评论？',
      success: async (res) => {
        if (!res.confirm) return
        try {
          await deletePostComment(id)
          wx.showToast({ title: '已删除', icon: 'success' })
          this.loadList()
        } catch (e) { /* handled */ }
      }
    })
  },

  preventBubble() {}
})