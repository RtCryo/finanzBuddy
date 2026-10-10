import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from './client'
import { getDashboard } from './dashboard'

describe('getDashboard', () => {
  afterEach(() => vi.restoreAllMocks())

  it('requests the aggregate dashboard endpoint', async () => {
    const metrics = {
      totalDocuments: 1,
      processedDocuments: 1,
      inProgressDocuments: 0,
      savedTransactions: 3,
      uniqueCategories: 2,
      uniqueCounterparties: 2,
    }
    const request = vi.spyOn(api, 'get').mockResolvedValue({ data: metrics })

    await expect(getDashboard()).resolves.toEqual(metrics)
    expect(request).toHaveBeenCalledWith('/dashboard')
  })
})
