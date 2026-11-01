import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  createPersonalTransactionCategory,
  editTransaction,
  getAssignedTransactionTags,
  getTransactionCategories,
  getTransactionHistory,
  getTransactionTags,
  getTransactionVersions,
  updateTransactionCategory,
} from '../api/transactions'
import type { TransactionHistoryPage as TransactionHistoryResult } from '../types/transaction'
import { TransactionHistoryPage } from './TransactionHistoryPage'

vi.mock('../api/transactions', () => ({
  createPersonalTransactionCategory: vi.fn(),
  editTransaction: vi.fn(),
  getAssignedTransactionTags: vi.fn(),
  getTransactionCategories: vi.fn(),
  getTransactionHistory: vi.fn(),
  getTransactionTags: vi.fn(),
  getTransactionVersions: vi.fn(),
  updateTransactionCategory: vi.fn(),
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
    vi.mocked(getTransactionCategories).mockResolvedValue(['Food', 'Housing'])
    vi.mocked(getTransactionTags).mockResolvedValue([])
    vi.mocked(getAssignedTransactionTags).mockResolvedValue([])
    vi.mocked(editTransaction).mockResolvedValue({ transaction: { ...transaction, category: 'Food' }, tags: [] })
    vi.mocked(getTransactionVersions).mockResolvedValue([
      { versionNumber: 1, transaction, createdAt: null },
    ])
    vi.mocked(updateTransactionCategory).mockResolvedValue({ ...transaction, category: 'Food' })
    vi.mocked(createPersonalTransactionCategory).mockResolvedValue({
      name: 'Travel',
      transaction: { ...transaction, category: 'Travel' },
    })
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

  it('normalizes padded text and whitespace-only categories the same in rows and details', async () => {
    const user = userEvent.setup()
    const paddedTransaction = { ...transaction, counterparty: '  ACME GmbH  ', description: ' Monthly rent ', category: '   ' }
    vi.mocked(getTransactionHistory).mockResolvedValue(
      result({ content: [paddedTransaction] }),
    )
    vi.mocked(getTransactionVersions).mockResolvedValue([{ versionNumber: 1, transaction: paddedTransaction, createdAt: null }])
    renderPage()

    const row = await screen.findByRole('row', { name: /Monthly rent/ })
    expect(within(row).getByText('ACME GmbH')).toBeInTheDocument()
    expect(within(row).getByText('Monthly rent')).toBeInTheDocument()
    expect(within(row).getByText('Uncategorized')).toBeInTheDocument()

    await user.click(within(row).getByText('Monthly rent'))
    const dialog = screen.getByRole('dialog', { name: 'Transaction details' })
    expect(within(dialog).getAllByText('ACME GmbH')).toHaveLength(1)
    expect(within(dialog).getAllByText('Monthly rent')).toHaveLength(1)
    expect(within(dialog).getByText('Uncategorized')).toBeInTheDocument()
  })

  it('opens details for the keyboard-activated history transaction, including its booking date', async () => {
    const selected = { ...transaction, id: 8, counterparty: 'Example Bank', description: 'Savings deposit' }
    vi.mocked(getTransactionHistory).mockResolvedValue(result({ content: [transaction, selected], totalElements: 2 }))
    vi.mocked(getTransactionVersions).mockResolvedValue([{ versionNumber: 1, transaction: selected, createdAt: null }])
    const user = userEvent.setup()
    renderPage()

    await screen.findByRole('table', { name: 'Transaction history' })
    const action = screen.getByRole('button', { name: 'View transaction details: Savings deposit' })
    action.focus()
    await user.keyboard('{Enter}')

    const dialog = screen.getByRole('dialog', { name: 'Transaction details' })
    expect(dialog).toHaveTextContent('Jan 31, 2024')
    expect(dialog).toHaveTextContent('Feb 1, 2024')
    expect(dialog).toHaveTextContent('Example Bank')
    expect(dialog).toHaveTextContent('Savings deposit')
    expect(dialog).toHaveTextContent('123.45')
    expect(dialog).toHaveTextContent('EUR')
    expect(dialog).toHaveTextContent('Housing')
  })

  it('preserves history filters, sorting, pagination, scroll position, and loaded results after closing details', async () => {
    const user = userEvent.setup()
    vi.mocked(getTransactionHistory).mockResolvedValue(result({ totalElements: 200, totalPages: 10 }))
    Object.defineProperty(window, 'scrollY', { configurable: true, value: 640 })
    renderPage()

    await screen.findByRole('table', { name: 'Transaction history' })
    await user.type(screen.getByRole('textbox', { name: 'Search description or counterparty' }), 'rent')
    await waitFor(() => expect(getTransactionHistory).toHaveBeenLastCalledWith(expect.objectContaining({ query: 'rent' })))
    await user.selectOptions(screen.getByRole('combobox', { name: 'Filter by category' }), 'Housing')
    await waitFor(() => expect(getTransactionHistory).toHaveBeenLastCalledWith(expect.objectContaining({ category: 'Housing' })))
    await user.click(screen.getByRole('button', { name: /Counterparty/ }))
    await waitFor(() => expect(getTransactionHistory).toHaveBeenLastCalledWith(expect.objectContaining({
      sortField: 'counterparty',
      sortDirection: 'ASC',
    })))
    await user.click(screen.getByRole('button', { name: 'Page 2' }))
    await waitFor(() => expect(getTransactionHistory).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1 })))

    const requestCount = vi.mocked(getTransactionHistory).mock.calls.length
    const selectedRow = screen.getByRole('row', { name: /Monthly rent/ })
    await user.click(within(selectedRow).getByText('Monthly rent'))
    expect(selectedRow).toHaveClass('transaction-row-selected')
    await user.click(screen.getByRole('button', { name: 'Close transaction details' }))

    expect(getTransactionHistory).toHaveBeenCalledTimes(requestCount)
    expect(selectedRow).not.toHaveClass('transaction-row-selected')
    expect(screen.getByRole('textbox', { name: 'Search description or counterparty' })).toHaveValue('rent')
    expect(screen.getByRole('combobox', { name: 'Filter by category' })).toHaveValue('Housing')
    expect(screen.getByRole('columnheader', { name: /Counterparty/ })).toHaveAttribute('aria-sort', 'ascending')
    expect(screen.getByRole('button', { name: 'Page 2' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByText('Showing 21–40 of 200 transactions')).toBeInTheDocument()
    expect(window.scrollY).toBe(640)
  })

  it('refreshes the edited row without resetting history filters, page, sort, or scroll', async () => {
    const user = userEvent.setup()
    const updated = { ...transaction, category: 'Food' }
    vi.mocked(getTransactionHistory)
      .mockResolvedValueOnce(result({ totalElements: 200, totalPages: 10 }))
      .mockResolvedValue(result({ content: [updated], totalElements: 200, totalPages: 10 }))
    vi.mocked(updateTransactionCategory).mockResolvedValue(updated)
    Object.defineProperty(window, 'scrollY', { configurable: true, value: 480 })
    renderPage()

    await screen.findByRole('table', { name: 'Transaction history' })
    await user.type(screen.getByRole('textbox', { name: 'Search description or counterparty' }), 'rent')
    await waitFor(() => expect(getTransactionHistory).toHaveBeenLastCalledWith(expect.objectContaining({ query: 'rent' })))
    await user.click(screen.getByRole('button', { name: 'Page 2' }))
    await waitFor(() => expect(getTransactionHistory).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1 })))
    await user.click(screen.getByRole('button', { name: /Counterparty/ }))
    await waitFor(() => expect(getTransactionHistory).toHaveBeenLastCalledWith(expect.objectContaining({
      page: 0,
      sortField: 'counterparty',
      sortDirection: 'ASC',
    })))
    await user.click(screen.getByRole('button', { name: 'Page 2' }))
    await waitFor(() => expect(getTransactionHistory).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1 })))

    const row = screen.getByRole('row', { name: /Monthly rent/ })
    await user.click(within(row).getByText('Monthly rent'))
    await user.click(await screen.findByRole('button', { name: 'Edit transaction' }))
    await user.selectOptions(screen.getByRole('combobox', { name: 'Transaction category' }), 'Food')
    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    await waitFor(() => expect(getTransactionHistory).toHaveBeenLastCalledWith(expect.objectContaining({
      page: 1,
      query: 'rent',
      sortField: 'counterparty',
      sortDirection: 'ASC',
    })))
    expect(await screen.findByRole('row', { name: /Food/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Page 2' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('textbox', { name: 'Search description or counterparty' })).toHaveValue('rent')
    expect(window.scrollY).toBe(480)
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
        tagMode: 'AND',
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
      tagMode: 'AND',
    }))

    await user.selectOptions(screen.getByRole('combobox', { name: 'Filter by category' }), 'Housing')
    await waitFor(() => expect(getTransactionHistory).toHaveBeenLastCalledWith({
      page: 0,
      size: 20,
      category: 'Housing',
      sortField: 'valueDate',
      sortDirection: 'DESC',
      tagMode: 'AND',
    }))

    await user.click(screen.getByRole('button', { name: 'Clear filters' }))
    await waitFor(() => expect(getTransactionHistory).toHaveBeenLastCalledWith({
      page: 0,
      size: 20,
      sortField: 'valueDate',
      sortDirection: 'DESC',
      tagMode: 'AND',
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
      tagMode: 'AND',
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
      tagMode: 'AND',
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

  it('filters by selected tags and mode without adding tag cells to history rows', async () => {
    const user = userEvent.setup()
    vi.mocked(getTransactionTags).mockResolvedValue([{ id: 4, name: 'Travel' }, { id: 5, name: 'Work' }])
    renderPage()
    await screen.findByRole('table', { name: 'Transaction history' })
    await user.click(screen.getByRole('button', { name: 'Filter by tags, 0 selected' }))
    await user.click(await screen.findByRole('checkbox', { name: 'Travel' }))
    await user.click(screen.getByRole('checkbox', { name: 'Work' }))
    await waitFor(() => expect(getTransactionHistory).toHaveBeenLastCalledWith(expect.objectContaining({
      page: 0, tagIds: [4, 5], tagMode: 'AND',
    })))
    await user.click(screen.getByRole('radio', { name: 'Any selected (OR)' }))
    await waitFor(() => expect(getTransactionHistory).toHaveBeenLastCalledWith(expect.objectContaining({
      page: 0, tagIds: [4, 5], tagMode: 'OR',
    })))
    expect(screen.getAllByRole('columnheader')).toHaveLength(6)
    expect(screen.getByRole('row', { name: /Monthly rent/ }).querySelectorAll('[role="cell"]')).toHaveLength(6)
    await user.click(screen.getByRole('button', { name: 'Clear filters' }))
    await waitFor(() => expect(getTransactionHistory).toHaveBeenLastCalledWith(expect.objectContaining({ tagMode: 'AND' })))
    expect(vi.mocked(getTransactionHistory).mock.lastCall?.[0]).not.toHaveProperty('tagIds')
  })

  it('clamps the final page and restores focus when a saved row leaves active tag results', async () => {
    const user = userEvent.setup()
    const travel = { id: 4, name: 'Travel' }
    const other = { ...transaction, id: 8, description: 'Other matching transaction' }
    let removed = false
    vi.mocked(getTransactionTags).mockResolvedValue([travel])
    vi.mocked(getAssignedTransactionTags).mockResolvedValue([travel])
    vi.mocked(getTransactionHistory).mockImplementation(async params => {
      if (removed) return result({ content: params?.page ? [] : [other], totalElements: 1, totalPages: 1 })
      return result({ content: params?.page ? [transaction] : [other], totalElements: 21, totalPages: 2 })
    })
    vi.mocked(editTransaction).mockImplementation(async () => {
      removed = true
      return { transaction, tags: [] }
    })
    renderPage()
    await screen.findByRole('table', { name: 'Transaction history' })
    await user.click(screen.getByRole('button', { name: 'Filter by tags, 0 selected' }))
    await user.click(await screen.findByRole('checkbox', { name: 'Travel' }))
    await user.click(screen.getByRole('button', { name: 'Page 2' }))
    const row = await screen.findByRole('row', { name: /Monthly rent/ })
    await user.click(within(row).getByText('Monthly rent'))
    await user.click(screen.getByRole('button', { name: 'Edit transaction' }))
    await user.click(screen.getByRole('button', { name: 'Remove tag Travel' }))
    const choicesReads = vi.mocked(getTransactionTags).mock.calls.length
    await user.click(screen.getByRole('button', { name: 'Save changes' }))
    await waitFor(() => expect(getTransactionHistory).toHaveBeenLastCalledWith(expect.objectContaining({
      page: 0, tagIds: [4], tagMode: 'AND',
    })))
    expect(screen.queryByRole('row', { name: /Monthly rent/ })).not.toBeInTheDocument()
    expect(screen.getByRole('row', { name: /Other matching transaction/ })).toBeInTheDocument()
    await waitFor(() => expect(vi.mocked(getTransactionTags).mock.calls.length).toBeGreaterThan(choicesReads))
    await user.click(screen.getByRole('button', { name: 'Close transaction details' }))
    expect(screen.getByRole('textbox', { name: 'Search description or counterparty' })).toHaveFocus()
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
