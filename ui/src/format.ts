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
  const date = new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]))
  return Number.isNaN(date.valueOf()) ? '—' : dateFormat.format(date)
}
