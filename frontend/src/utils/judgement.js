const RATE = 0.0005

export function scaleForDivision(divisionMg) {
  let grams = Number(divisionMg) / 1000
  let scale = 0
  while (scale < 8 && Math.abs(grams * 10 ** scale - Math.round(grams * 10 ** scale)) > 1e-9) {
    scale += 1
  }
  return scale
}

export function limits(nominalG, scale) {
  const nominal = Number(nominalG)
  return {
    lower: Number((nominal * (1 - RATE)).toFixed(scale)),
    upper: Number((nominal * (1 + RATE)).toFixed(scale))
  }
}

export function toGram(value, unit) {
  const number = Number(value)
  return unit === 'mg' ? number / 1000 : number
}

export function qualified(valueG, nominalG, scale) {
  if (valueG === null || valueG === undefined || Number.isNaN(valueG)) return null
  const { lower, upper } = limits(nominalG, scale)
  return valueG >= lower - 1e-9 && valueG <= upper + 1e-9
}

export function formatG(value, scale) {
  if (value === null || value === undefined) return '-'
  return Number(value).toFixed(scale)
}
