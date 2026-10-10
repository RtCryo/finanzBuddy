import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { getTransactionHistory } from '../api/transactions'
import type { TransactionHistoryPage as TransactionHistoryResult } from '../types/transaction'
import { TransactionHistoryPage } from './TransactionHistoryPage'

vi.mock('../api/transactions', () => ({
  getTransactionHistory: vi.fn(),
  UNCATEGORIZED_CATEGORY: '__uncategorized__',
}))

const transaction = {
  id: 7,
  bookingDate: '2024-01-31',
  valueDate: '2024-02-01',
  amount: '123.45',
  currency: 'EUR',
  counterparty: 'ACME GmbH',
  description: 'Monthly rent',
  category: 'Housing',
}

function result(overrides: Partial<TransactionHistoryResult> = {}): TransactionHistoryResult {
  return {
    content: [transaction],
    totalElements: 1,
    totalPages: 1,
    number: 0,
    size: 20,
    categories: ['Housing', 'Income'],
    hasUncategorized: true,
    ...overrides,
  }
}

function renderPage() {
  return render(<MemoryRouter initialEntries={['/transactions']}><TransactionHistoryPage /></MemoryRouter>)
}

describe('TransactionHistoryPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(getTransactionHistory).mockResolvedValue(result())
  })

  afterEach(cleanup)

  it('renders normalized transaction fields and available category choices', async () => {
    renderPage()

    expect(await screen.findByRole('table', { name: 'Transaction history' })).toBeInTheDocument()
    const row = screen.getByRole('row', { name: /Monthly rent/ })
    expect(within(row).getByText('Jan 31, 2024')).toBeInTheDocument()
    expect(within(row).getByText('Feb 1, 2024')).toBeInTheDocument()
    expect(within(row).getByText('123.45')).toBeInTheDocument()
    expect(within(row).getByText('EUR')).toBeInTheDocument()
    expect(within(row).getByText('ACME GmbH')).toBeInTheDocument()
    expect(within(row).getByText('Housing')).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'Income' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'Uncategorized' })).toBeInTheDocument()
  })

  it('combines text, inclusive date and category filters in the aggregate request', async () => {
    const user = userEvent.setup()
    renderPage()

    await screen.findByRole('table', { name: 'Transaction history' })
    await user.type(screen.getByRole('textbox', { name: 'Search description or counterparty' }), 'rent')
    fireEvent.change(screen.getByLabelText('Booking date from'), { target: { value: '2024-01-01' } })
    fireEvent.change(screen.getByLabelText('Booking date to'), { target: { value: '2024-01-31' } })
    await user.selectOptions(screen.getByRole('combobox', { name: 'Filter by category' }), 'Housing')

    await waitFor(() => {
      expect(getTransactionHistory).toHaveBeenLastCalledWith({
        page: 0,
        size: 20,
        query: 'rent',
        from: '2024-01-01',
        to: '2024-01-31',
        category: 'Housing',
      })
    })
  })

  it('resets to the first page when filters change and clears all active filters', async () => {
    const user = userEvent.setup()
    vi.mocked(getTransactionHistory).mockResolvedValue(result({ totalElements: 200, totalPages: 10 }))
    renderPage()

    await screen.findByRole('table', { name: 'Transaction history' })
    await user.click(screen.getByRole('button', { name: 'Page 2' }))
    await waitFor(() => expect(getTransactionHistory).toHaveBeenLastCalledWith({ page: 1, size: 20 }))

    await user.selectOptions(screen.getByRole('combobox', { name: 'Filter by category' }), 'Housing')
    await waitFor(() => expect(getTransactionHistory).toHaveBeenLastCalledWith({ page: 0, size: 20, category: 'Housing' }))

    await user.click(screen.getByRole('button', { name: 'Clear filters' }))
    await waitFor(() => expect(getTransactionHistory).toHaveBeenLastCalledWith({ page: 0, size: 20 }))
    expect(screen.getByRole('combobox', { name: 'Filter by category' })).toHaveValue('')
  })

  it('jumps directly to first and last pages with condensed numbers and disabled boundary arrows', async () => {
    const user = userEvent.setup()
    vi.mocked(getTransactionHistory).mockResolvedValue(result({ totalElements: 200, totalPages: 10 }))
    renderPage()

    await screen.findByRole('table', { name: 'Transaction history' })
    expect(screen.getByText('…')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Previous page' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Next page' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Page 1' })).toHaveAttribute('aria-current', 'page')

    await user.click(screen.getByRole('button', { name: 'Page 10' }))
    await waitFor(() => expect(getTransactionHistory).toHaveBeenLastCalledWith({ page: 9, size: 20 }))
    expect(screen.getByRole('button', { name: 'Page 10' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('button', { name: 'Previous page' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Next page' })).toBeDisabled()

    await user.click(screen.getByRole('button', { name: 'Page 1' }))
    await waitFor(() => expect(getTransactionHistory).toHaveBeenLastCalledWith({ page: 0, size: 20 }))
    expect(screen.getByRole('button', { name: 'Previous page' })).toBeDisabled()
  })

  it('shows empty, loading and error states', async () => {
    let resolvePending!: (value: TransactionHistoryResult) => void
    vi.mocked(getTransactionHistory).mockReturnValueOnce(
      new Promise((resolve) => { resolvePending = resolve }),
    )
    renderPage()
    expect(screen.getByRole('status')).toHaveTextContent('Loading transactions…')
    resolvePending(result({ content: [], totalElements: 0, totalPages: 0 }))
    expect(await screen.findByText('No transactions available')).toBeInTheDocument()

    cleanup()
    vi.mocked(getTransactionHistory).mockResolvedValue(result({ content: [], totalElements: 0, totalPages: 0 }))
    renderPage()
    expect(await screen.findByText('No transactions available')).toBeInTheDocument()

    cleanup()
    vi.mocked(getTransactionHistory).mockRejectedValueOnce(new Error('offline'))
    renderPage()
    expect(await screen.findByRole('alert')).toHaveTextContent('We could not load your transaction history.')
  })
})
