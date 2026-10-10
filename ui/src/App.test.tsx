import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearSession, setSession } from './auth/storage'
import { getTransactionHistory } from './api/transactions'
import { getDashboard } from './api/dashboard'
import type { TransactionHistoryPage } from './types/transaction'
import App from './App'

vi.mock('./api/transactions', () => ({
  getTransactionHistory: vi.fn(),
  UNCATEGORIZED_CATEGORY: '__uncategorized__',
}))

vi.mock('./api/dashboard', () => ({
  getDashboard: vi.fn(),
}))

const emptyDashboard = {
  totalDocuments: 0,
  processedDocuments: 0,
  inProgressDocuments: 0,
  savedTransactions: 0,
  uniqueCategories: 0,
  uniqueCounterparties: 0,
}

const emptyHistory: TransactionHistoryPage = {
  content: [],
  totalElements: 0,
  totalPages: 0,
  number: 0,
  size: 20,
  categories: [],
  hasUncategorized: false,
}

describe('transaction history route', () => {
  beforeEach(() => {
    clearSession()
    window.history.pushState({}, '', '/transactions')
    vi.mocked(getTransactionHistory).mockResolvedValue(emptyHistory)
    vi.mocked(getDashboard).mockResolvedValue(emptyDashboard)
  })

  afterEach(() => {
    cleanup()
    clearSession()
  })

  it('requires an authenticated session', async () => {
    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Sign in to your account' })).toBeInTheDocument()
  })

  it('opens the protected route for signed-in users and exposes the matching navigation link', async () => {
    setSession('test-token', 'history@example.com')
    render(<App />)

    expect(await screen.findByText('No transactions available')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Transaction history' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Transaction history' })).toHaveAttribute('href', '/transactions')
  })

  it('sends the authenticated workspace root to Dashboard and keeps the existing destinations', async () => {
    setSession('test-token', 'dashboard@example.com')
    window.history.pushState({}, '', '/')
    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Dashboard' })).toHaveAttribute('href', '/dashboard')
    expect(screen.getByRole('link', { name: 'Documents' })).toHaveAttribute('href', '/documents')
    expect(screen.getByRole('link', { name: 'Transaction history' }))
      .toHaveAttribute('href', '/transactions')
  })
})
