import { describe, expect, it } from 'vitest'
import { formatDateOnly, formatInstant, transactionAmountTone } from './format'

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

describe('transactionAmountTone', () => {
  it('keeps positive and negative amounts semantic and unavailable values neutral', () => {
    expect(transactionAmountTone(12)).toBe('amount-positive')
    expect(transactionAmountTone('-12')).toBe('amount-negative')
    expect(transactionAmountTone(0)).toBe('amount-neutral')
    expect(transactionAmountTone('invalid')).toBe('amount-neutral')
    expect(transactionAmountTone(null)).toBe('amount-neutral')
  })
})
