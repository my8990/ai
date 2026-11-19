Component({
  data: {
    visible: false,
    value: '',
    focused: false,
    submitting: false,
    errorText: '',
    digits: ['', '', '', '', '', '']
  },

  methods: {
    open(options) {
      const opts = options || {}
      this._onSubmit = opts.onSubmit || null
      this._onCancel = opts.onCancel || null
      this.setData({
        visible: true,
        value: '',
        errorText: '',
        submitting: false,
        focused: false,
        digits: ['', '', '', '', '', '']
      })
      setTimeout(() => this.setData({ focused: true }), 120)
    },

    close() {
      this.setData({ visible: false, focused: false })
      const fn = this._onCancel
      this._onCancel = null
      if (fn) fn()
    },

    onInput(e) {
      const raw = (e.detail.value || '').replace(/\D/g, '').slice(0, 6)
      const digits = []
      for (let i = 0; i < 6; i++) digits.push(raw.charAt(i) || '')
      this.setData({ value: raw, digits, errorText: '' })
      if (raw.length === 6) this.submit()
    },

    focusInput() {
      this.setData({ focused: true })
    },

    async submit() {
      if (this.data.submitting) return
      if (this.data.value.length !== 6) {
        this.setData({ errorText: '请输入 6 位数字签到码' })
        return
      }
      if (!this._onSubmit) {
        this.setData({ visible: false })
        return
      }
      this.setData({ submitting: true, errorText: '' })
      try {
        await this._onSubmit(this.data.value)
        this.setData({ submitting: false, visible: false, focused: false })
      } catch (err) {
        this.setData({
          submitting: false,
          errorText: (err && err.message) || '签到失败，请重试'
        })
      }
    },

    noop() {}
  },

  lifetimes: {
    detached() {
      this._onSubmit = null
      this._onCancel = null
    }
  }
})