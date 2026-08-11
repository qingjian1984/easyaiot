(() => {
  const marker = '__TD005_AUTH_ALLOWLIST__'
  if (window[marker] === true)
    return

  const allowedApiPaths = new Set([
    '/dev-api/system/tenant/get-id-by-name',
    '/dev-api/system/captcha/get',
    '/dev-api/system/captcha/check',
    '/dev-api/system/auth/login',
    '/dev-api/system/auth/get-permission-info',
  ])
  const allowedStaticPaths = new Set(['/_app.config.js', '/favicon.ico'])
  const allowedStaticPrefixes = ['/assets/', '/resource/']
  const allowedOrigin = 'http://localhost:8888'

  const originalFetch = window.fetch.bind(window)
  const originalOpen = XMLHttpRequest.prototype.open
  const originalSend = XMLHttpRequest.prototype.send
  const originalSendBeacon = typeof navigator.sendBeacon === 'function'
    ? navigator.sendBeacon.bind(navigator)
    : null

  const replaceValue = (target, key, value) => {
    try {
      target[key] = value
      if (target[key] === value)
        return true
    }
    catch {}
    try {
      Object.defineProperty(target, key, {
        configurable: true,
        value,
        writable: true,
      })
      return target[key] === value
    }
    catch {
      return false
    }
  }

  const blockedError = code => new TypeError(code)
  const denyFetch = () => Promise.reject(blockedError('TD005_AUTH_ALLOWLIST_INSTALL_FAILED'))
  const denyOpen = function () {
    this.__td005RequestAllowed = false
  }
  const denySend = function () {
    queueMicrotask(() => this.dispatchEvent(new ProgressEvent('error')))
  }
  const denySocket = class TD005BlockedSocket {
    constructor() {
      throw blockedError('TD005_AUTH_WINDOW_BLOCKED')
    }
  }

  // Fail closed first. If any following surface cannot be patched, fetch/XHR stay fully blocked.
  if (!replaceValue(window, 'fetch', denyFetch)
    || !replaceValue(XMLHttpRequest.prototype, 'open', denyOpen)
    || !replaceValue(XMLHttpRequest.prototype, 'send', denySend)
    || (originalSendBeacon && !replaceValue(navigator, 'sendBeacon', () => false))
    || !replaceValue(window, 'WebSocket', denySocket)
    || !replaceValue(window, 'EventSource', denySocket)) {
    throw blockedError('TD005_AUTH_ALLOWLIST_SURFACE_UNPATCHABLE')
  }

  const isAllowedRequest = (rawUrl) => {
    try {
      const url = new URL(String(rawUrl), window.location.href)
      return url.origin === allowedOrigin
        && (allowedApiPaths.has(url.pathname)
          || allowedStaticPaths.has(url.pathname)
          || allowedStaticPrefixes.some(prefix => url.pathname.startsWith(prefix)))
    }
    catch {
      return false
    }
  }

  window.fetch = (input, init) => {
    const rawUrl = typeof input === 'string' || input instanceof URL ? input : input?.url
    if (!isAllowedRequest(rawUrl))
      return Promise.reject(blockedError('TD005_AUTH_WINDOW_BLOCKED'))
    return originalFetch(input, init)
  }
  XMLHttpRequest.prototype.open = function (method, url, ...rest) {
    this.__td005RequestAllowed = isAllowedRequest(url)
    if (!this.__td005RequestAllowed)
      return undefined
    return originalOpen.call(this, method, url, ...rest)
  }
  XMLHttpRequest.prototype.send = function (...args) {
    if (!this.__td005RequestAllowed) {
      queueMicrotask(() => this.dispatchEvent(new ProgressEvent('error')))
      return undefined
    }
    return originalSend.apply(this, args)
  }
  if (originalSendBeacon) {
    navigator.sendBeacon = (url, data) => isAllowedRequest(url)
      ? originalSendBeacon(url, data)
      : false
  }

  Object.defineProperty(window, marker, {
    configurable: false,
    enumerable: false,
    value: true,
    writable: false,
  })
})()
