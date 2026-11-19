Component({
  data: {
    visible: false,
    title: '',
    content: '',
    actionText: '',
    showAction: false,
    variant: 'info'
  },

  methods: {
    open(options) {
      const opts = options || {}
      this._onAction = opts.onAction || null
      clearTimeout(this._timer)
      this.setData({
        visible: true,
        title: opts.title || '',
        content: opts.content || '',
        actionText: opts.actionText || '',
        showAction: !!opts.actionText,
        variant: opts.variant || 'info'
      })
      const variant = opts.variant || 'info'
      this._timer = setTimeout(
        () => this.close(),
        this._onAction ? 8000 : (variant === 'error' ? 3600 : 2400)
      )
    },

    close() {
      clearTimeout(this._timer)
      this.setData({ visible: false })
    },

    handleAction() {
      const fn = this._onAction
      this._onAction = null
      this.close()
      if (fn) fn()
    },

    noop() {}
  },

  lifetimes: {
    detached() {
      clearTimeout(this._timer)
    }
  }
})