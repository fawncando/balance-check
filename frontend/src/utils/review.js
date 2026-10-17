import { LIMIT_RATE } from './judgement.js'

/** 允许偏差：标称值的 ±0.05%，曲线图按百分比绘制 */
export const LIMIT_PERCENT = LIMIT_RATE * 100

const STATUS = {
  DRAFT: { text: '草稿', type: 'info', locked: false },
  SUBMITTED: { text: '待复核', type: 'warning', locked: false },
  REVIEWED: { text: '已复核', type: 'success', locked: true }
}

/**
 * 复核状态：记录状态配合复核结论才能区分“草稿、已退回、已解锁、待复核、已复核”。
 * 退回后记录回到草稿但保留 REJECTED 结论；已复核被解锁后保留 APPROVED 结论。
 */
export function reviewStateOf(status, reviewResult) {
  if (status === 'REVIEWED') return { ...STATUS.REVIEWED }
  if (status === 'SUBMITTED') return { ...STATUS.SUBMITTED }
  if (reviewResult === 'REJECTED') return { text: '已退回', type: 'danger', locked: false }
  if (reviewResult === 'APPROVED') return { text: '已解锁', type: 'info', locked: false }
  return { ...STATUS.DRAFT }
}

export function reviewResultText(reviewResult) {
  if (reviewResult === 'APPROVED') return '通过'
  if (reviewResult === 'REJECTED') return '退回'
  return '未复核'
}

/** 九次称量相对标称值的偏差（%），用于详情页的偏差曲线 */
export function deviationSeries(measurements) {
  return (measurements || []).map((row, index) => {
    const nominal = Number(row.nominalG)
    const raw = row.valueG
    const value = raw === null || raw === undefined ? null : Number(raw)
    const pct = value === null || !Number.isFinite(value) || !Number.isFinite(nominal) || nominal === 0
      ? null
      : ((value - nominal) / nominal) * 100
    return {
      index,
      label: `R${row.rangeSeq}-${row.weightSeq}-${row.measureSeq}`,
      nominalG: nominal,
      pct,
      qualified: row.qualified === true
    }
  })
}

/** 曲线纵轴范围：至少覆盖允许带，超差时按最大偏差放大 */
export function chartScale(series) {
  const values = (series || [])
    .filter((item) => item.pct !== null && Number.isFinite(item.pct))
    .map((item) => Math.abs(item.pct))
  return Math.max(...values, LIMIT_PERCENT) * 1.25
}
