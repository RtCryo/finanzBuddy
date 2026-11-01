import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from './client'
import {
  createPersonalTransactionCategory,
  editTransaction,
  getAssignedTransactionTags,
  getTransactionCategories,
  getTransactionHistory,
  getTransactionTags,
  getTransactionVersions,
  getRelatedExpenses,
  updateTransactionCategory,
} from './transactions'
import type { TransactionHistoryPage, TransactionRecord, TransactionVersion } from '../types/transaction'

vi.mock('./client', () => ({
  api: { get: vi.fn(), post: vi.fn(), put: vi.fn() },
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
      sortField: 'amount',
      sortDirection: 'ASC',
      tagIds: [5, 8],
      tagMode: 'OR',
    })

    expect(api.get).toHaveBeenCalledWith('/transactions', {
      params: {
        page: 2,
        size: 10,
        query: 'market',
        from: '2024-01-01',
        to: '2024-01-31',
        category: 'Food',
        sortField: 'amount',
        sortDirection: 'ASC',
        tagIds: [5, 8],
        tagMode: 'OR',
      },
      paramsSerializer: { indexes: null },
    })
    expect(result).toEqual(response)
  })

  it('uses default pagination and omits empty filters', async () => {
    await getTransactionHistory({ query: '', from: '', to: '', category: '' })

    expect(api.get).toHaveBeenCalledWith('/transactions', {
      params: { page: 0, size: 20 }, paramsSerializer: { indexes: null },
    })
  })

  it('encodes the related source route and maps bounded page responses', async () => {
    const page = { items: [{ id: 8, valueDate: null, amount: '-42.5', currency: 'EUR' }], page: 2, size: 10, totalElements: 21, totalPages: 3 }
    vi.mocked(api.get).mockResolvedValue({ data: page } as never)
    await expect(getRelatedExpenses('7/8' as unknown as number, 2, 10)).resolves.toEqual(page)
    expect(api.get).toHaveBeenCalledWith('/transactions/7%2F8/related-expenses', { params: { page: 2, size: 10 } })
    await getRelatedExpenses(7)
    expect(api.get).toHaveBeenLastCalledWith('/transactions/7/related-expenses', { params: { page: 0, size: 20 } })
  })

  it('lists categories and reads chronological transaction versions', async () => {
    const versions: TransactionVersion[] = [
      { versionNumber: 1, transaction: { ...transaction, category: 'Food' }, createdAt: null },
    ]
    vi.mocked(api.get)
      .mockResolvedValueOnce({ data: ['Food', 'Housing'] } as never)
      .mockResolvedValueOnce({ data: versions } as never)

    await expect(getTransactionCategories()).resolves.toEqual(['Food', 'Housing'])
    await expect(getTransactionVersions(14)).resolves.toEqual(versions)

    expect(api.get).toHaveBeenNthCalledWith(1, '/transactions/categories')
    expect(api.get).toHaveBeenNthCalledWith(2, '/transactions/14/versions')
  })

  it('creates and assigns a personal category using one request', async () => {
    const created: TransactionRecord = { ...transaction, category: 'Travel' }
    vi.mocked(api.post).mockResolvedValue({ data: { name: 'Travel', transaction: created } } as never)

    await expect(createPersonalTransactionCategory(14, 'Travel')).resolves.toEqual({
      name: 'Travel',
      transaction: created,
    })

    expect(api.post).toHaveBeenCalledWith('/transactions/categories', {
      transactionId: 14,
      name: 'Travel',
    })
  })

  it('updates an existing category and returns the current transaction', async () => {
    const updated: TransactionRecord = { ...transaction, category: 'Housing' }
    vi.mocked(api.put).mockResolvedValue({ data: updated } as never)

    await expect(updateTransactionCategory(14, 'Housing')).resolves.toEqual(updated)

    expect(api.put).toHaveBeenCalledWith('/transactions/14/category', { category: 'Housing' })
  })

  it('reads reusable and assigned tags and saves category plus the complete tag set together', async () => {
    const tags = [{ id: 4, name: 'Travel' }]
    vi.mocked(api.get).mockResolvedValue({ data: tags } as never)
    vi.mocked(api.put).mockResolvedValue({ data: { transaction, tags } } as never)
    await expect(getTransactionTags()).resolves.toEqual(tags)
    await expect(getAssignedTransactionTags(14)).resolves.toEqual(tags)
    await expect(editTransaction(14, { category: 'Food', tags: [{ id: 4 }] })).resolves.toEqual({ transaction, tags })
    expect(api.get).toHaveBeenNthCalledWith(1, '/transactions/tags')
    expect(api.get).toHaveBeenNthCalledWith(2, '/transactions/14/tags')
    expect(api.put).toHaveBeenLastCalledWith('/transactions/14', { category: 'Food', tags: [{ id: 4 }] })
  })

  it('sends tag-only edits without category fields and propagates tag request failures', async () => {
    const failure = new Error('offline')
    vi.mocked(api.put).mockRejectedValueOnce(failure)
    await expect(editTransaction(14, { tags: [{ name: 'New tag' }] })).rejects.toBe(failure)
    expect(api.put).toHaveBeenCalledWith('/transactions/14', { tags: [{ name: 'New tag' }] })

    vi.mocked(api.get).mockRejectedValueOnce(failure).mockRejectedValueOnce(failure)
    await expect(getTransactionTags()).rejects.toBe(failure)
    await expect(getAssignedTransactionTags(14)).rejects.toBe(failure)
  })
})

const transaction: TransactionRecord = {
  id: 14,
  bookingDate: '2024-01-01',
  valueDate: '2024-01-02',
  amount: '12.50',
  currency: 'EUR',
  counterparty: 'Shop',
  description: 'Purchase',
  category: 'Food',
}
