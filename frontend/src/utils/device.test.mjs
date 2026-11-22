import { test } from 'node:test'
import assert from 'node:assert/strict'
import { landingPath, safeLocalPath } from './device.js'

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
