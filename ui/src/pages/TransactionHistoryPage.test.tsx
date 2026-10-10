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

  it('renders Value date without a Booking date column and available category choices', async () => {
    renderPage()

    expect(await screen.findByRole('table', { name: 'Transaction history' })).toBeInTheDocument()
    const row = screen.getByRole('row', { name: /Monthly rent/ })
    expect(within(row).getByText('Feb 1, 2024')).toBeInTheDocument()
    expect(within(row).queryByText('Jan 31, 2024')).not.toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: /Value date/ })).toBeInTheDocument()
    expect(screen.queryByRole('columnheader', { name: /Booking date/ })).not.toBeInTheDocument()
    expect(within(row).getByText('123.45')).toBeInTheDocument()
    expect(within(row).getByText('EUR')).toBeInTheDocument()
    expect(within(row).getByText('ACME GmbH')).toBeInTheDocument()
    expect(within(row).getByText('Housing')).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'Income' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'Uncategorized' })).toBeInTheDocument()
  })

  it('renders whitespace-only categories as Uncategorized', async () => {
    vi.mocked(getTransactionHistory).mockResolvedValue(
      result({ content: [{ ...transaction, category: '   ' }] }),
    )
    renderPage()

    const row = await screen.findByRole('row', { name: /Monthly rent/ })
    expect(within(row).getByText('Uncategorized')).toBeInTheDocument()
  })

  it('combines text, inclusive date and category filters in the aggregate request', async () => {
    const user = userEvent.setup()
    renderPage()

    await screen.findByRole('table', { name: 'Transaction history' })
    await user.type(screen.getByRole('textbox', { name: 'Search description or counterparty' }), 'rent')
    fireEvent.change(screen.getByLabelText('Value date from'), { target: { value: '2024-01-01' } })
    fireEvent.change(screen.getByLabelText('Value date to'), { target: { value: '2024-01-31' } })
    await user.selectOptions(screen.getByRole('combobox', { name: 'Filter by category' }), 'Housing')

    await waitFor(() => {
      expect(getTransactionHistory).toHaveBeenLastCalledWith({
        page: 0,
        size: 20,
        query: 'rent',
        from: '2024-01-01',
        to: '2024-01-31',
        category: 'Housing',
        sortField: 'valueDate',
        sortDirection: 'DESC',
      })
    })
  })

  it('resets to the first page when filters change and clears all active filters', async () => {
    const user = userEvent.setup()
    vi.mocked(getTransactionHistory).mockResolvedValue(result({ totalElements: 200, totalPages: 10 }))
    renderPage()

    await screen.findByRole('table', { name: 'Transaction history' })
    await user.click(screen.getByRole('button', { name: 'Page 2' }))
    await waitFor(() => expect(getTransactionHistory).toHaveBeenLastCalledWith({
      page: 1,
      size: 20,
      sortField: 'valueDate',
      sortDirection: 'DESC',
    }))

    await user.selectOptions(screen.getByRole('combobox', { name: 'Filter by category' }), 'Housing')
    await waitFor(() => expect(getTransactionHistory).toHaveBeenLastCalledWith({
      page: 0,
      size: 20,
      category: 'Housing',
      sortField: 'valueDate',
      sortDirection: 'DESC',
    }))

    await user.click(screen.getByRole('button', { name: 'Clear filters' }))
    await waitFor(() => expect(getTransactionHistory).toHaveBeenLastCalledWith({
      page: 0,
      size: 20,
      sortField: 'valueDate',
      sortDirection: 'DESC',
    }))
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
    await waitFor(() => expect(getTransactionHistory).toHaveBeenLastCalledWith({
      page: 9,
      size: 20,
      sortField: 'valueDate',
      sortDirection: 'DESC',
    }))
    expect(screen.getByRole('button', { name: 'Page 10' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('button', { name: 'Previous page' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Next page' })).toBeDisabled()

    await user.click(screen.getByRole('button', { name: 'Page 1' }))
    await waitFor(() => expect(getTransactionHistory).toHaveBeenLastCalledWith({
      page: 0,
      size: 20,
      sortField: 'valueDate',
      sortDirection: 'DESC',
    }))
    expect(screen.getByRole('button', { name: 'Previous page' })).toBeDisabled()
  })

  it('sorts columns accessibly and resets pagination when sorting changes', async () => {
    const user = userEvent.setup()
    vi.mocked(getTransactionHistory).mockResolvedValue(result({ totalElements: 200, totalPages: 10 }))
    renderPage()

    await screen.findByRole('table', { name: 'Transaction history' })
    expect(screen.getByRole('columnheader', { name: /Value date/ })).toHaveAttribute('aria-sort', 'descending')
    await user.click(screen.getByRole('button', { name: 'Page 2' }))
    await waitFor(() => expect(getTransactionHistory).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1 })))

    await user.click(screen.getByRole('button', { name: /Counterparty/ }))
    await waitFor(() => expect(getTransactionHistory).toHaveBeenLastCalledWith(expect.objectContaining({
      page: 0,
      sortField: 'counterparty',
      sortDirection: 'ASC',
    })))
    expect(screen.getByRole('columnheader', { name: /Counterparty/ })).toHaveAttribute('aria-sort', 'ascending')

    await user.click(screen.getByRole('button', { name: /Counterparty/ }))
    await waitFor(() => expect(getTransactionHistory).toHaveBeenLastCalledWith(expect.objectContaining({
      page: 0,
      sortField: 'counterparty',
      sortDirection: 'DESC',
    })))
    expect(screen.getByRole('columnheader', { name: /Counterparty/ })).toHaveAttribute('aria-sort', 'descending')
  })

  it('styles signed amounts semantically while preserving their text', async () => {
    vi.mocked(getTransactionHistory).mockResolvedValue(result({
      content: [
        transaction,
        { ...transaction, id: 8, amount: '-9.50', description: 'Debit' },
        { ...transaction, id: 9, amount: '0', description: 'Zero' },
        { ...transaction, id: 10, amount: null, description: 'Missing' },
      ],
      totalElements: 4,
    }))
    renderPage()

    expect(await screen.findByRole('row', { name: /Monthly rent/ })).toHaveTextContent('123.45')
    expect(screen.getByRole('row', { name: /Monthly rent/ }).querySelector('.transaction-amount'))
      .toHaveClass('amount-positive')
    expect(screen.getByRole('row', { name: /Debit/ }).querySelector('.transaction-amount'))
      .toHaveClass('amount-negative')
    expect(screen.getByRole('row', { name: /Zero/ }).querySelector('.transaction-amount'))
      .toHaveClass('amount-neutral')
    expect(screen.getByRole('row', { name: /Missing/ }).querySelector('.transaction-amount'))
      .toHaveClass('amount-neutral')
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
