import { act, cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from '../api/client'
import { getDocument, getDocumentTransactions } from '../api/documents'
import {
  createPersonalTransactionCategory,
  getTransactionCategories,
  getTransactionVersions,
  getRelatedExpenses,
  updateTransactionCategory,
} from '../api/transactions'
import { DocumentDetailPage } from './DocumentDetailPage'

vi.mock('../api/documents', () => ({
  getDocument: vi.fn(),
  getDocumentTransactions: vi.fn(),
}))

vi.mock('../api/transactions', () => ({
  createPersonalTransactionCategory: vi.fn(),
  getTransactionCategories: vi.fn(),
  getTransactionVersions: vi.fn(),
  getRelatedExpenses: vi.fn(),
  updateTransactionCategory: vi.fn(),
}))

const processingDocument = {
  id: 7,
  originalFilename: 'statement.csv',
  type: 'CSV',
  status: 'PROCESSING' as const,
  createdAt: '2026-09-28T12:00:00Z',
  errorMessage: null,
}

const processedDocument = { ...processingDocument, status: 'PROCESSED' as const }
const transaction = {
  id: 12,
  bookingDate: '2024-03-14',
  valueDate: '2024-03-15',
  amount: '42.75',
  currency: 'USD',
  counterparty: 'Example Books',
  description: 'Book purchase',
  category: 'Shopping',
}

function renderDetail() {
  return render(
    <MemoryRouter initialEntries={['/documents/7']}>
      <Routes>
        <Route path="/documents/:id" element={<DocumentDetailPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('DocumentDetailPage', () => {
  beforeEach(() => {
    vi.mocked(getRelatedExpenses).mockResolvedValue({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
    vi.mocked(getDocument).mockReset()
    vi.mocked(getDocumentTransactions).mockReset()
    vi.mocked(getTransactionCategories).mockResolvedValue(['Food', 'Shopping'])
    vi.mocked(getTransactionVersions).mockResolvedValue([
      { versionNumber: 1, transaction, createdAt: null },
    ])
    vi.mocked(updateTransactionCategory).mockResolvedValue({ ...transaction, category: 'Food' })
    vi.mocked(createPersonalTransactionCategory).mockResolvedValue({
      name: 'Travel',
      transaction: { ...transaction, category: 'Travel' },
    })
    URL.createObjectURL = vi.fn(() => 'blob:mock')
    URL.revokeObjectURL = vi.fn()
  })

  afterEach(() => {
    cleanup()
    vi.useRealTimers()
  })

  it('polls a processing document automatically without a manual refresh control', async () => {
    vi.useFakeTimers()
    vi.mocked(getDocument)
      .mockResolvedValueOnce(processingDocument)
      .mockResolvedValue({ ...processingDocument, status: 'PROCESSED' })
    vi.mocked(getDocumentTransactions).mockResolvedValue([])

    renderDetail()
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0)
    })

    expect(screen.getByText('Your document is being processed')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Refresh' })).not.toBeInTheDocument()
    expect(getDocument).toHaveBeenCalledTimes(1)

    await act(async () => {
      await vi.advanceTimersByTimeAsync(5000)
    })
    expect(getDocument).toHaveBeenCalledTimes(2)
    expect(screen.getByText('No transactions are available for this document yet.')).toBeInTheDocument()
  })

  it('fetches the current document and its transactions again when details are reopened', async () => {
    vi.mocked(getDocument).mockResolvedValue({ ...processingDocument, status: 'PROCESSED' })
    vi.mocked(getDocumentTransactions).mockResolvedValue([])

    const firstVisit = renderDetail()
    await waitFor(() => expect(getDocumentTransactions).toHaveBeenCalledTimes(1))
    expect(screen.queryByRole('button', { name: 'Refresh' })).not.toBeInTheDocument()

    firstVisit.unmount()
    renderDetail()
    await waitFor(() => expect(getDocumentTransactions).toHaveBeenCalledTimes(2))
    expect(getDocument).toHaveBeenCalledTimes(2)
  })

  it('opens details for a keyboard-activated document transaction with every normalized field', async () => {
    vi.mocked(getDocument).mockResolvedValue(processedDocument)
    vi.mocked(getDocumentTransactions).mockResolvedValue([transaction])
    const user = userEvent.setup()
    renderDetail()

    const action = await screen.findByRole('button', { name: 'View transaction details: Book purchase' })
    action.focus()
    await user.keyboard('{Enter}')

    const dialog = screen.getByRole('dialog', { name: 'Transaction details' })
    expect(dialog).toHaveTextContent('Booking date')
    expect(dialog).toHaveTextContent('Mar 14, 2024')
    expect(dialog).toHaveTextContent('Value date')
    expect(dialog).toHaveTextContent('Mar 15, 2024')
    expect(dialog).toHaveTextContent('42.75')
    expect(dialog).toHaveTextContent('USD')
    expect(dialog).toHaveTextContent('Example Books')
    expect(dialog).toHaveTextContent('Book purchase')
    expect(dialog).toHaveTextContent('Shopping')
  })

  it('uses positive, negative, and neutral amount treatments in the document table', async () => {
    vi.mocked(getDocument).mockResolvedValue(processedDocument)
    vi.mocked(getDocumentTransactions).mockResolvedValue([
      transaction,
      { ...transaction, id: 13, amount: '-9.5', description: 'Refund' },
      { ...transaction, id: 14, amount: null, description: 'Unavailable amount' },
    ])
    renderDetail()

    const rows = await screen.findAllByRole('row')
    expect(within(rows[1]).getByText('42.75').closest('.transaction-amount')).toHaveClass('amount-positive')
    expect(within(rows[2]).getByText('-9.5').closest('.transaction-amount')).toHaveClass('amount-negative')
    expect(within(rows[3]).getByText('—').closest('.transaction-amount')).toHaveClass('amount-neutral')
  })

  it('normalizes blank and padded transaction values the same in document rows and details', async () => {
    vi.mocked(getDocument).mockResolvedValue(processedDocument)
    const paddedTransaction = {
      ...transaction,
      counterparty: '   ',
      description: '  Book purchase  ',
      category: '  ',
    }
    vi.mocked(getDocumentTransactions).mockResolvedValue([paddedTransaction])
    vi.mocked(getTransactionVersions).mockResolvedValue([{ versionNumber: 1, transaction: paddedTransaction, createdAt: null }])
    const user = userEvent.setup()
    renderDetail()

    const row = await screen.findByRole('row', { name: /Book purchase/ })
    expect(within(row).getByText('—')).toBeInTheDocument()
    expect(within(row).getByText('Book purchase')).toBeInTheDocument()
    expect(within(row).getByText('Uncategorized')).toBeInTheDocument()

    await user.click(within(row).getByText('Book purchase'))
    const dialog = screen.getByRole('dialog', { name: 'Transaction details' })
    expect(within(dialog).getByText('—')).toBeInTheDocument()
    expect(within(dialog).getAllByText('Book purchase')).toHaveLength(1)
    expect(within(dialog).getByText('Uncategorized')).toBeInTheDocument()
  })

  it('closes document transaction details to the activating row without changing the transaction list', async () => {
    Object.defineProperty(window, 'scrollY', { configurable: true, value: 480 })
    vi.mocked(getDocument).mockResolvedValue(processedDocument)
    vi.mocked(getDocumentTransactions).mockResolvedValue([transaction])
    const user = userEvent.setup()
    renderDetail()

    const action = await screen.findByRole('button', { name: 'View transaction details: Book purchase' })
    const row = screen.getByRole('row', { name: /Book purchase/ })
    await user.click(within(row).getByText('Book purchase'))
    expect(row).toHaveClass('transaction-row-selected')
    expect(await screen.findByText('No other expenses with this counterparty.')).toBeInTheDocument()
    expect(getRelatedExpenses).toHaveBeenCalledWith(transaction.id, 0)
    await user.click(screen.getByRole('button', { name: 'Close transaction details' }))

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(row).not.toHaveClass('transaction-row-selected')
    expect(action).toHaveFocus()
    expect(window.scrollY).toBe(480)
    expect(screen.getByRole('table', { name: 'Document transactions' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'View transaction details: Book purchase' })).toBeInTheDocument()
    expect(getDocumentTransactions).toHaveBeenCalledTimes(1)
  })

  it('refreshes a saved category in the source document without duplicating its row', async () => {
    vi.mocked(getDocument).mockResolvedValue(processedDocument)
    const updated = { ...transaction, category: 'Food' }
    vi.mocked(getDocumentTransactions)
      .mockResolvedValueOnce([transaction])
      .mockResolvedValue([updated])
    vi.mocked(updateTransactionCategory).mockResolvedValue(updated)
    const user = userEvent.setup()
    renderDetail()

    const row = await screen.findByRole('row', { name: /Book purchase/ })
    await user.click(within(row).getByText('Book purchase'))
    await user.click(await screen.findByRole('button', { name: 'Edit transaction' }))
    await user.selectOptions(screen.getByRole('combobox', { name: 'Transaction category' }), 'Food')
    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    const updatedRow = await screen.findByRole('row', { name: /Food/ })
    expect(updatedRow).toHaveTextContent('Food')
    expect(screen.getAllByRole('row')).toHaveLength(2)
    await waitFor(() => expect(getDocumentTransactions).toHaveBeenCalledTimes(2))
  })

  it('requests the original file as a download and attaches the link', async () => {
    vi.mocked(getDocument).mockResolvedValue({ ...processingDocument, status: 'PROCESSED' })
    vi.mocked(getDocumentTransactions).mockResolvedValue([])
    const get = vi.spyOn(api, 'get').mockResolvedValue({ data: new Blob(['csv']), headers: {} })
    const append = vi.spyOn(document.body, 'appendChild')
    const user = userEvent.setup()

    renderDetail()
    await user.click(await screen.findByRole('button', { name: 'Download original file statement.csv' }))

    expect(get).toHaveBeenCalledWith('/documents/7/download', expect.objectContaining({
      responseType: 'blob',
      headers: { Accept: 'application/octet-stream, application/problem+json, application/json' },
    }))
    expect(append).toHaveBeenCalled()
    append.mockRestore()
    get.mockRestore()
  })
})
