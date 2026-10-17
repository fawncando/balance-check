import { test } from 'node:test'
import assert from 'node:assert/strict'
import { LIMIT_PERCENT, chartScale, deviationSeries, reviewResultText, reviewStateOf } from './review.js'

test('允许偏差为 0.05%', () => {
  assert.equal(LIMIT_PERCENT, 0.05)
})

test('待复核与已复核状态直接映射', () => {
  assert.deepEqual(reviewStateOf('SUBMITTED', null), { text: '待复核', type: 'warning', locked: false })
  assert.deepEqual(reviewStateOf('REVIEWED', 'APPROVED'), { text: '已复核', type: 'success', locked: true })
})

test('草稿按复核结论区分已退回与已解锁', () => {
  assert.equal(reviewStateOf('DRAFT', null).text, '草稿')
  assert.equal(reviewStateOf('DRAFT', 'REJECTED').text, '已退回')
  assert.equal(reviewStateOf('DRAFT', 'REJECTED').type, 'danger')
  assert.equal(reviewStateOf('DRAFT', 'APPROVED').text, '已解锁')
})

test('复核结论文案', () => {
  assert.equal(reviewResultText('APPROVED'), '通过')
  assert.equal(reviewResultText('REJECTED'), '退回')
  assert.equal(reviewResultText(null), '未复核')
})

function near(actual, expected, epsilon = 1e-9) {
  assert.ok(Math.abs(actual - expected) < epsilon, `期望 ${expected}，实际 ${actual}`)
}

test('偏差按标称值换算为百分比', () => {
  const series = deviationSeries([
    { rangeSeq: 1, weightSeq: 1, measureSeq: 1, nominalG: 200, valueG: 199.9, qualified: true },
    { rangeSeq: 1, weightSeq: 1, measureSeq: 2, nominalG: 200, valueG: 200.1, qualified: true },
    { rangeSeq: 1, weightSeq: 1, measureSeq: 3, nominalG: 20, valueG: 20.01, qualified: false }
  ])
  assert.equal(series.length, 3)
  near(series[0].pct, -0.05)
  near(series[1].pct, 0.05)
  near(series[2].pct, 0.05)
  assert.equal(series[2].qualified, false)
  assert.equal(series[0].label, 'R1-1-1')
})

test('示值为空时偏差记为 null', () => {
  const series = deviationSeries([{ rangeSeq: 1, weightSeq: 2, measureSeq: 1, nominalG: 50, valueG: null }])
  assert.equal(series[0].pct, null)
})

test('曲线纵轴至少覆盖允许带，超差时随之放大', () => {
  const inside = deviationSeries([{ rangeSeq: 1, weightSeq: 1, measureSeq: 1, nominalG: 100, valueG: 100.02 }])
  assert.equal(chartScale(inside), 0.0625)
  const outside = deviationSeries([{ rangeSeq: 1, weightSeq: 1, measureSeq: 1, nominalG: 100, valueG: 100.2 }])
  assert.ok(chartScale(outside) > 0.2)
  assert.equal(chartScale([]), 0.0625)
})
