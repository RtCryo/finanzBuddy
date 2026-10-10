import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from './client'
import { getTransactionHistory } from './transactions'
import type { TransactionHistoryPage } from '../types/transaction'

vi.mock('./client', () => ({
  api: { get: vi.fn() },
}))

const response: TransactionHistoryPage = {
  content: [],
  totalElements: 0,
  totalPages: 0,
  number: 0,
  size: 20,
  categories: ['Food'],
  hasUncategorized: true,
}

describe('getTransactionHistory', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(api.get).mockResolvedValue({ data: response } as never)
  })

  it('sends aggregate filters and pagination parameters and returns the page response', async () => {
    const result = await getTransactionHistory({
      page: 2,
      size: 10,
      query: 'market',
      from: '2024-01-01',
      to: '2024-01-31',
      category: 'Food',
    })

    expect(api.get).toHaveBeenCalledWith('/transactions', {
      params: {
        page: 2,
        size: 10,
        query: 'market',
        from: '2024-01-01',
        to: '2024-01-31',
        category: 'Food',
      },
    })
    expect(result).toEqual(response)
  })

  it('uses default pagination and omits empty filters', async () => {
    await getTransactionHistory({ query: '', from: '', to: '', category: '' })

    expect(api.get).toHaveBeenCalledWith('/transactions', { params: { page: 0, size: 20 } })
  })
})
