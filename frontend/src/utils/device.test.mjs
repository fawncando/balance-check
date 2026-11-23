import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  canAccessRoles,
  isMobileUserAgent,
  landingPath,
  safeLocalPath
} from './device.js'

test('手机登录默认进入移动上传页', () => {
  assert.equal(landingPath(null, true), '/mobile')
})

test('电脑登录默认进入工作台', () => {
  assert.equal(landingPath(null, false), '/workbench')
})

test('扫码时保留原本地跳转路径', () => {
  assert.equal(landingPath('/mobile', false), '/mobile')
  assert.equal(landingPath('/records', true), '/records')
})

test('拒绝外部跳转地址', () => {
  assert.equal(safeLocalPath('https://example.com'), null)
  assert.equal(safeLocalPath('//example.com'), null)
  assert.equal(landingPath('https://example.com', true), '/mobile')
})

test('窄窗口的桌面浏览器不按手机处理', () => {
  assert.equal(isMobileUserAgent('Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/158.0'), false)
  assert.equal(isMobileUserAgent('Mozilla/5.0 (Linux; Android 15; Mobile) Chrome/158.0'), true)
  assert.equal(isMobileUserAgent('Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X)'), true)
})

test('路由角色保护', () => {
  assert.equal(canAccessRoles('ADMIN', ['ADMIN']), true)
  assert.equal(canAccessRoles('INSPECTOR', ['ADMIN']), false)
  assert.equal(canAccessRoles('INSPECTOR', ['INSPECTOR', 'ADMIN']), true)
  assert.equal(canAccessRoles('REVIEWER', null), true)
})
