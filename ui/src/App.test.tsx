import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearSession, setSession } from './auth/storage'
import { getTransactionHistory } from './api/transactions'
import type { TransactionHistoryPage } from './types/transaction'
import App from './App'

vi.mock('./api/transactions', () => ({
  getTransactionHistory: vi.fn(),
  UNCATEGORIZED_CATEGORY: '__uncategorized__',
}))

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
})
