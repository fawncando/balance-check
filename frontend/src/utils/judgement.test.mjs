import { test } from 'node:test'
import assert from 'node:assert/strict'
import { LIMIT_RATE, formatG, limits, qualified, scaleForDivision, toGram } from './judgement.js'

test('分度值对应的小数位数', () => {
  assert.equal(LIMIT_RATE, 0.0005)
  assert.equal(scaleForDivision(0.1), 4)
  assert.equal(scaleForDivision(0.01), 5)
  assert.equal(scaleForDivision(1), 3)
})

test('限度按标称值 ±0.05% 计算', () => {
  assert.deepEqual(limits(200, 4), { lower: 199.9, upper: 200.1 })
  assert.deepEqual(limits(20, 5), { lower: 19.99, upper: 20.01 })
  assert.deepEqual(limits(2, 5), { lower: 1.999, upper: 2.001 })
})

test('mg 换算为 g', () => {
  assert.equal(toGram(500, 'mg'), 0.5)
  assert.equal(toGram('0.0001', 'g'), 0.0001)
})

test('边界值算合格，超差算不合格', () => {
  assert.equal(qualified(199.9, 200, 4), true)
  assert.equal(qualified(200.1, 200, 4), true)
  assert.equal(qualified(200.2, 200, 4), false)
  assert.equal(qualified(null, 200, 4), null)
})

test('示值格式化补足小数位', () => {
  assert.equal(formatG(19.99, 5), '19.99000')
  assert.equal(formatG(null, 4), '-')
})
