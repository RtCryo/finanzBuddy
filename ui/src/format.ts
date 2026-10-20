const dateFormat = new Intl.DateTimeFormat('en-US', {
  month: 'short',
  day: 'numeric',
  year: 'numeric',
})

export function formatInstant(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.valueOf()) ? '—' : dateFormat.format(date)
}

export function formatDateOnly(value: string | null | undefined) {
  if (!value) return '—'
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(value)
  if (!match) return formatInstant(value)
  const year = Number(match[1])
  const month = Number(match[2])
  const day = Number(match[3])
  const date = new Date(year, month - 1, day)
  // new Date() rolls out-of-range parts over into a valid date (e.g. 2024-13-45 -> Jan 2025), so
  // verify the components round-trip and reject invalid input instead of rendering a wrong date.
  if (date.getFullYear() !== year || date.getMonth() !== month - 1 || date.getDate() !== day) {
    return '—'
  }
  return dateFormat.format(date)
}

export function normalizeTransactionText(value: string | null | undefined) {
  return value?.trim() || ''
}

export function formatTransactionText(value: string | null | undefined) {
  return normalizeTransactionText(value) || '—'
}

export function formatTransactionCategory(value: string | null | undefined) {
  return normalizeTransactionText(value) || 'Uncategorized'
}

export function transactionAmountTone(amount: number | string | null) {
  if (amount === null || amount === '') return 'amount-neutral'
  const value = Number(amount)
  if (!Number.isFinite(value) || value === 0) return 'amount-neutral'
  return value > 0 ? 'amount-positive' : 'amount-negative'
}
