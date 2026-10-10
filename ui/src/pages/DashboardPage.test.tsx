import { act, cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { getDashboard } from '../api/dashboard'
import type { DashboardMetrics } from '../types/dashboard'
import { DashboardPage } from './DashboardPage'

vi.mock('../api/dashboard', () => ({
  getDashboard: vi.fn(),
}))

const zeroMetrics: DashboardMetrics = {
  totalDocuments: 0,
  processedDocuments: 0,
  inProgressDocuments: 0,
  savedTransactions: 0,
  uniqueCategories: 0,
  uniqueCounterparties: 0,
}

const populatedMetrics: DashboardMetrics = {
  totalDocuments: 8,
  processedDocuments: 5,
  inProgressDocuments: 2,
  savedTransactions: 24,
  uniqueCategories: 4,
  uniqueCounterparties: 11,
}

describe('DashboardPage', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.mocked(getDashboard).mockReset()
  })

  afterEach(() => {
    cleanup()
    vi.useRealTimers()
  })

  it('renders all six metrics, including zero values', async () => {
    vi.mocked(getDashboard).mockResolvedValue(zeroMetrics)
    render(<MemoryRouter><DashboardPage /></MemoryRouter>)

    expect(screen.getByRole('status')).toHaveTextContent('Loading your dashboard')
    await act(async () => { await vi.advanceTimersByTimeAsync(0) })

    expect(screen.getByRole('heading', { name: 'Dashboard' })).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'Dashboard summary' })).toBeInTheDocument()
    for (const label of [
      'Total documents',
      'Processed documents',
      'Documents in progress',
      'Saved transactions',
      'Unique categories',
      'Unique counterparties',
    ]) {
      expect(screen.getByText(label)).toBeInTheDocument()
    }
    expect(screen.getAllByText('0')).toHaveLength(6)
  })

  it('shows a recoverable error without displaying empty counts as valid data', async () => {
    vi.mocked(getDashboard).mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValueOnce(populatedMetrics)
    render(<MemoryRouter><DashboardPage /></MemoryRouter>)

    await act(async () => { await vi.advanceTimersByTimeAsync(0) })
    expect(screen.getByRole('alert')).toHaveTextContent('We could not load your dashboard.')
    expect(screen.queryByRole('region', { name: 'Dashboard summary' })).not.toBeInTheDocument()

    await act(async () => { await vi.advanceTimersByTimeAsync(5000) })
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(screen.getByText('24')).toBeInTheDocument()
  })

  it('polls every five seconds and cancels polling on unmount', async () => {
    vi.mocked(getDashboard).mockResolvedValue(populatedMetrics)
    const { unmount } = render(<MemoryRouter><DashboardPage /></MemoryRouter>)
    await act(async () => { await vi.advanceTimersByTimeAsync(0) })
    expect(getDashboard).toHaveBeenCalledTimes(1)

    await act(async () => { await vi.advanceTimersByTimeAsync(5000) })
    expect(getDashboard).toHaveBeenCalledTimes(2)

    unmount()
    await act(async () => { await vi.advanceTimersByTimeAsync(10000) })
    expect(getDashboard).toHaveBeenCalledTimes(2)
  })

  it('ignores an older response when a newer poll has completed', async () => {
    let resolveFirst!: (metrics: DashboardMetrics) => void
    vi.mocked(getDashboard)
      .mockImplementationOnce(() => new Promise((resolve) => { resolveFirst = resolve }))
      .mockResolvedValueOnce(populatedMetrics)
    render(<MemoryRouter><DashboardPage /></MemoryRouter>)

    await act(async () => { await vi.advanceTimersByTimeAsync(0) })
    await act(async () => { await vi.advanceTimersByTimeAsync(5000) })
    expect(screen.getByText('24')).toBeInTheDocument()

    await act(async () => { resolveFirst(zeroMetrics) })
    expect(screen.getByText('24')).toBeInTheDocument()
  })
})
