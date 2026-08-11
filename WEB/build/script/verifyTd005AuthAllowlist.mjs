import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import vm from 'node:vm'

const source = await readFile(new URL('../../src/views/base/td005-auth-harness/td005AuthAllowlist.js', import.meta.url), 'utf8')

function createBrowser({ unpatchableBeacon = false } = {}) {
  const calls = { fetch: [], open: [], send: 0, beacon: [], errors: 0 }
  class FakeProgressEvent {
    constructor(type) { this.type = type }
  }
  class FakeXhr {
    open(method, url) { calls.open.push([method, url]) }
    send() { calls.send++ }
    dispatchEvent(event) { if (event.type === 'error') calls.errors++ }
  }
  const browser = {
    EventSource: class {},
    ProgressEvent: FakeProgressEvent,
    URL,
    WebSocket: class {},
    XMLHttpRequest: FakeXhr,
    fetch: async (input) => { calls.fetch.push(String(input)); return { ok: true } },
    location: { href: 'http://localhost:8888/td005-auth-harness' },
    navigator: {},
    queueMicrotask,
  }
  Object.defineProperty(browser.navigator, 'sendBeacon', {
    configurable: !unpatchableBeacon,
    value: (url) => { calls.beacon.push(String(url)); return true },
    writable: !unpatchableBeacon,
  })
  browser.window = browser
  browser.globalThis = browser
  vm.createContext(browser)
  return { browser, calls }
}

{
  const { browser, calls } = createBrowser()
  vm.runInContext(source, browser)
  assert.equal(browser.__TD005_AUTH_ALLOWLIST__, true)

  await browser.fetch('/dev-api/system/captcha/get')
  assert.deepEqual(calls.fetch, ['/dev-api/system/captcha/get'])
  await assert.rejects(browser.fetch('/dev-api/system/auth/logout'), /TD005_AUTH_WINDOW_BLOCKED/)
  await assert.rejects(browser.fetch('/dev-api/system/auth/refresh-token'), /TD005_AUTH_WINDOW_BLOCKED/)
  await assert.rejects(browser.fetch('https://example.com/'), /TD005_AUTH_WINDOW_BLOCKED/)

  const allowed = new browser.XMLHttpRequest()
  allowed.open('POST', '/dev-api/system/auth/login')
  allowed.send()
  assert.deepEqual(calls.open, [['POST', '/dev-api/system/auth/login']])
  assert.equal(calls.send, 1)

  const blocked = new browser.XMLHttpRequest()
  blocked.open('DELETE', '/dev-api/system/auth/logout')
  blocked.send()
  await Promise.resolve()
  assert.equal(calls.open.length, 1)
  assert.equal(calls.send, 1)
  assert.equal(calls.errors, 1)

  assert.equal(browser.navigator.sendBeacon('/dev-api/system/captcha/check'), true)
  assert.equal(browser.navigator.sendBeacon('/dev-api/system/auth/logout'), false)
  assert.deepEqual(calls.beacon, ['/dev-api/system/captcha/check'])
  assert.throws(() => new browser.WebSocket(), /TD005_AUTH_WINDOW_BLOCKED/)
  assert.throws(() => new browser.EventSource(), /TD005_AUTH_WINDOW_BLOCKED/)

  vm.runInContext(source, browser)
  await browser.fetch('/dev-api/system/auth/get-permission-info')
  assert.equal(calls.fetch.length, 2)
}

{
  const { browser, calls } = createBrowser({ unpatchableBeacon: true })
  assert.throws(() => vm.runInContext(source, browser), /TD005_AUTH_ALLOWLIST_SURFACE_UNPATCHABLE/)
  assert.notEqual(browser.__TD005_AUTH_ALLOWLIST__, true)
  await assert.rejects(browser.fetch('/dev-api/system/captcha/get'), /TD005_AUTH_ALLOWLIST_INSTALL_FAILED/)
  assert.equal(calls.fetch.length, 0)
}

console.log('TD005_AUTH_ALLOWLIST_CONTRACT=PASS allowed=5 logout=blocked refresh=blocked failClosed=true')
