import { describe, expect, it } from 'vitest'
import { formatDateOnly, formatInstant } from './format'

describe('formatDateOnly', () => {
  it('keeps a date-only value on that calendar day', () => {
    expect(formatDateOnly('2026-09-28')).toBe('Sep 28, 2026')
    expect(formatDateOnly('2026-09-28T00:00:00')).toBe('Sep 28, 2026')
  })

  it('renders an empty date as a dash', () => {
    expect(formatDateOnly(null)).toBe('—')
    expect(formatDateOnly('')).toBe('—')
  })

  it('rejects an out-of-range date instead of rolling it over', () => {
    expect(formatDateOnly('2024-13-45')).toBe('—')
    expect(formatDateOnly('2024-02-30')).toBe('—')
  })
})

describe('formatInstant', () => {
  it('rejects an unparseable timestamp', () => {
    expect(formatInstant('not-a-date')).toBe('—')
  })
})
