import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useRef, useState } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  createPersonalTransactionCategory,
  getTransactionCategories,
  getTransactionVersions,
  getRelatedExpenses,
  updateTransactionCategory,
} from '../api/transactions'
import type { TransactionRecord, TransactionVersion } from '../types/transaction'
import { TransactionDetailsDialog } from './TransactionDetailsDialog'

vi.mock('../api/transactions', () => ({
  createPersonalTransactionCategory: vi.fn(),
  getTransactionCategories: vi.fn(),
  getTransactionVersions: vi.fn(),
  getRelatedExpenses: vi.fn(),
  updateTransactionCategory: vi.fn(),
}))

const fullTransaction: TransactionRecord = {
  id: 7,
  bookingDate: '2024-01-31',
  valueDate: '2024-02-01',
  amount: '1234.5',
  currency: 'EUR',
  counterparty: 'ACME GmbH',
  description: 'Monthly rent',
  category: 'Housing',
}

const firstVersion: TransactionVersion = {
  versionNumber: 1,
  transaction: fullTransaction,
  createdAt: null,
}

function DialogHarness({ transaction = fullTransaction, onSaved = vi.fn() }: {
  transaction?: TransactionRecord
  onSaved?: (transaction: TransactionRecord) => void
}) {
  const [selected, setSelected] = useState<TransactionRecord | null>(null)
  const openerRef = useRef<HTMLButtonElement>(null)

  return (
    <>
      <button ref={openerRef} type="button" onClick={() => setSelected(transaction)}>Open transaction</button>
      <TransactionDetailsDialog transaction={selected} openerRef={openerRef} onClose={() => setSelected(null)} onSaved={onSaved} />
    </>
  )
}

describe('TransactionDetailsDialog', () => {
  afterEach(cleanup)

  beforeEach(() => {
    vi.mocked(getRelatedExpenses).mockResolvedValue({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
    vi.clearAllMocks()
    vi.mocked(getTransactionCategories).mockResolvedValue(['Food', 'Housing'])
    vi.mocked(getTransactionVersions).mockResolvedValue([firstVersion])
    vi.mocked(updateTransactionCategory).mockResolvedValue({ ...fullTransaction, category: 'Food' })
    vi.mocked(createPersonalTransactionCategory).mockResolvedValue({
      name: 'Travel',
      transaction: { ...fullTransaction, category: 'Travel' },
    })
  })

  it('shows the transaction in a read-only side-panel dialog with plain-text category', async () => {
    const user = userEvent.setup()
    render(<DialogHarness />)

    await user.click(screen.getByRole('button', { name: 'Open transaction' }))

    const dialog = screen.getByRole('dialog', { name: 'Transaction details' })
    expect(dialog).toBeInTheDocument()
    expect(dialog).toHaveTextContent('Booking date')
    expect(dialog).toHaveTextContent('Jan 31, 2024')
    expect(dialog).toHaveTextContent('Value date')
    expect(dialog).toHaveTextContent('Feb 1, 2024')
    expect(dialog).toHaveTextContent('1,234.5')
    expect(dialog).toHaveTextContent('EUR')
    expect(dialog).toHaveTextContent('Counterparty')
    expect(dialog).toHaveTextContent('ACME GmbH')
    expect(dialog).toHaveTextContent('Description')
    expect(dialog).toHaveTextContent('Monthly rent')
    expect(dialog).toHaveTextContent('Category')
    expect(dialog).toHaveTextContent('Housing')
    expect(screen.getByRole('button', { name: 'Edit transaction' })).toBeInTheDocument()
    expect(screen.queryByRole('combobox', { name: 'Transaction category' })).not.toBeInTheDocument()
    expect(screen.queryByRole('tablist', { name: 'Transaction versions' })).not.toBeInTheDocument()
    expect(dialog.querySelector('.category-chip')).not.toBeInTheDocument()
  })

  it('enters generic edit mode and cancels changes without saving', async () => {
    const user = userEvent.setup()
    render(<DialogHarness />)

    await user.click(screen.getByRole('button', { name: 'Open transaction' }))
    await user.click(await screen.findByRole('button', { name: 'Edit transaction' }))
    await user.selectOptions(screen.getByRole('combobox', { name: 'Transaction category' }), 'Food')

    expect(screen.getByText('Monthly rent')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Save changes' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Save category' })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Cancel' }))
    expect(screen.getByText('Housing', { selector: '.transaction-details-fields dd' })).toBeInTheDocument()
    expect(screen.queryByRole('combobox', { name: 'Transaction category' })).not.toBeInTheDocument()
    expect(updateTransactionCategory).not.toHaveBeenCalled()
  })

  it('uses existing missing-value labels and dismisses with Escape', async () => {
    const user = userEvent.setup()
    const transaction = { ...fullTransaction, bookingDate: null, valueDate: null, amount: null, currency: null, counterparty: ' ', description: '', category: '  ' }
    vi.mocked(getTransactionVersions).mockResolvedValue([{ ...firstVersion, transaction }])
    render(<DialogHarness transaction={transaction} />)

    await user.click(screen.getByRole('button', { name: 'Open transaction' }))
    const dialog = screen.getByRole('dialog', { name: 'Transaction details' })
    const values = Array.from(dialog.querySelectorAll('.transaction-details-fields dd'), (value) => value.textContent)
    expect(values).toEqual(['—', '—', '—', 'Uncategorized'])

    fireEvent.keyDown(dialog, { key: 'Escape' })
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Open transaction' })).toHaveFocus()
  })

  it('closes with its explicit close control and restores focus to the opener', async () => {
    const user = userEvent.setup()
    render(<DialogHarness />)

    const opener = screen.getByRole('button', { name: 'Open transaction' })
    await user.click(opener)
    await user.click(screen.getByRole('button', { name: 'Close transaction details' }))

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(opener).toHaveFocus()
  })

  it('closes when the backdrop is clicked but stays open when its content is clicked', async () => {
    const user = userEvent.setup()
    render(<DialogHarness />)

    const opener = screen.getByRole('button', { name: 'Open transaction' })
    await user.click(opener)
    const dialog = screen.getByRole('dialog', { name: 'Transaction details' })

    fireEvent.click(dialog.querySelector('.transaction-details-content')!)
    expect(dialog).toBeInTheDocument()

    fireEvent.click(dialog)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(opener).toHaveFocus()
  })

  it('saves a category and switches snapshots using the chronological version tabs', async () => {
    const user = userEvent.setup()
    const onSaved = vi.fn()
    const secondVersion: TransactionVersion = {
      versionNumber: 2,
      transaction: { ...fullTransaction, category: 'Food' },
      createdAt: '2024-02-02T10:00:00Z',
    }
    vi.mocked(getTransactionVersions)
      .mockResolvedValueOnce([firstVersion])
      .mockResolvedValueOnce([firstVersion, secondVersion])
    vi.mocked(updateTransactionCategory).mockResolvedValue(secondVersion.transaction)
    render(<DialogHarness onSaved={onSaved} />)

    await user.click(screen.getByRole('button', { name: 'Open transaction' }))
    await user.click(await screen.findByRole('button', { name: 'Edit transaction' }))
    await user.selectOptions(screen.getByRole('combobox', { name: 'Transaction category' }), 'Food')
    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    const currentVersion = await screen.findByRole('tab', { name: 'Version 2, current' })
    expect(currentVersion).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('heading', { name: 'Changes since version 1' })).toBeInTheDocument()
    expect(screen.getByText('Housing', { selector: '.transaction-version-diff dd span' })).toBeInTheDocument()
    expect(screen.getByText('Food', { selector: '.transaction-version-diff dd strong' })).toBeInTheDocument()
    expect(updateTransactionCategory).toHaveBeenCalledWith(7, 'Food')
    expect(onSaved).toHaveBeenCalledWith(secondVersion.transaction)

    await user.click(screen.getByRole('tab', { name: 'Original version' }))
    expect(screen.getByRole('tab', { name: 'Original version' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByText('Housing', { selector: '.transaction-details-fields dd' })).toBeInTheDocument()
    expect(screen.getByText('This is the original snapshot imported from the statement.')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Changes since version 1' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Edit transaction' })).not.toBeInTheDocument()
    await user.click(currentVersion)
    expect(currentVersion).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('button', { name: 'Edit transaction' })).toBeInTheDocument()
  })

  it('compares all changed fields with the immediately older version', async () => {
    const user = userEvent.setup()
    const secondVersion: TransactionVersion = {
      versionNumber: 2,
      transaction: { ...fullTransaction, category: 'Food' },
      createdAt: '2024-02-02T10:00:00Z',
    }
    const thirdVersion: TransactionVersion = {
      versionNumber: 3,
      transaction: { ...secondVersion.transaction, description: 'Groceries and supplies', category: 'Shopping' },
      createdAt: '2024-02-03T10:00:00Z',
    }
    vi.mocked(getTransactionVersions).mockResolvedValue([firstVersion, secondVersion, thirdVersion])
    render(<DialogHarness />)

    await user.click(screen.getByRole('button', { name: 'Open transaction' }))

    const versionTabs = screen.getByRole('tablist', { name: 'Transaction versions' })
    versionTabs.style.paddingLeft = '3px'
    const rect = (left: number, width: number): DOMRect => ({
      x: left,
      y: 0,
      left,
      top: 0,
      right: left + width,
      bottom: 39,
      width,
      height: 39,
      toJSON: () => ({}),
    })
    vi.spyOn(versionTabs, 'getBoundingClientRect').mockReturnValue(rect(100, 496))
    within(versionTabs).getAllByRole('tab').forEach((tab, index) => {
      vi.spyOn(tab, 'getBoundingClientRect').mockReturnValue(rect(103 + index * 154, 150))
    })
    fireEvent(window, new Event('resize'))
    expect(versionTabs.style.getPropertyValue('--selected-version-offset')).toBe('311px')
    expect(versionTabs.style.getPropertyValue('--selected-version-width')).toBe('150px')
    expect(screen.getByRole('tab', { name: 'Version 3, current' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('heading', { name: 'Changes since version 2' })).toBeInTheDocument()
    expect(screen.getByText('Groceries and supplies', { selector: '.transaction-details-fields dd' })).toBeInTheDocument()
    expect(screen.getByText('Food', { selector: '.transaction-version-diff dd span' })).toBeInTheDocument()
    expect(screen.getByText('Shopping', { selector: '.transaction-version-diff dd strong' })).toBeInTheDocument()

    await user.click(screen.getByRole('tab', { name: 'Version 2' }))
    expect(versionTabs.style.getPropertyValue('--selected-version-offset')).toBe('157px')
    expect(screen.getByRole('heading', { name: 'Changes since version 1' })).toBeInTheDocument()
    expect(screen.getByText('Housing', { selector: '.transaction-version-diff dd span' })).toBeInTheDocument()
    expect(screen.getByText('Food', { selector: '.transaction-version-diff dd strong' })).toBeInTheDocument()
  })

  it('creates and assigns a personal category through the shared save action', async () => {
    const user = userEvent.setup()
    const onSaved = vi.fn()
    const savedVersion: TransactionVersion = {
      versionNumber: 2,
      transaction: { ...fullTransaction, category: 'Travel' },
      createdAt: '2024-02-02T10:00:00Z',
    }
    vi.mocked(getTransactionVersions)
      .mockResolvedValueOnce([firstVersion])
      .mockResolvedValueOnce([firstVersion, savedVersion])
    render(<DialogHarness onSaved={onSaved} />)

    await user.click(screen.getByRole('button', { name: 'Open transaction' }))
    await user.click(await screen.findByRole('button', { name: 'Edit transaction' }))
    await user.selectOptions(screen.getByRole('combobox', { name: 'Transaction category' }), '__create_category__')
    await user.type(screen.getByRole('textbox', { name: 'New personal category' }), 'Travel')
    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    expect(createPersonalTransactionCategory).toHaveBeenCalledWith(7, 'Travel')
    expect(onSaved).toHaveBeenCalledWith({ ...fullTransaction, category: 'Travel' })
    expect(await screen.findByText('Travel', { selector: '.transaction-details-fields dd' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Create and assign' })).not.toBeInTheDocument()
  })

  it('loads compact expenses independently and preserves rows while retrying an append', async () => {
    const user = userEvent.setup()
    const row = { id: 8, valueDate: '2024-03-01', amount: '-1234.5', currency: 'USD' }
    vi.mocked(getRelatedExpenses)
      .mockResolvedValueOnce({ items: [row], page: 0, size: 20, totalElements: 21, totalPages: 2 })
      .mockRejectedValueOnce(new Error('Related offline'))
      .mockResolvedValueOnce({ items: [{ ...row, id: 9, valueDate: null, amount: '-2', currency: 'EUR' }], page: 1, size: 20, totalElements: 21, totalPages: 2 })
    render(<DialogHarness />)
    await user.click(screen.getByRole('button', { name: 'Open transaction' }))
    const section = screen.getByRole('region', { name: 'Other expenses with this counterparty' })
    expect(await within(section).findByText('-1,234.5 USD')).toBeInTheDocument()
    expect(section).toHaveTextContent('Mar 1, 2024')
    await user.click(screen.getByRole('button', { name: 'Edit transaction' }))
    await user.click(within(section).getByRole('button', { name: 'Load more' }))
    expect(await within(section).findByRole('alert')).toBeInTheDocument()
    expect(section).toHaveTextContent('-1,234.5 USD')
    expect(screen.getByRole('combobox', { name: 'Transaction category' })).toBeEnabled()
    await user.click(within(section).getByRole('button', { name: 'Retry related expenses' }))
    expect(await within(section).findByText('-2 EUR')).toBeInTheDocument()
    expect(section).toHaveTextContent('—')
    expect(getRelatedExpenses).toHaveBeenNthCalledWith(2, 7, 1)
    expect(getRelatedExpenses).toHaveBeenNthCalledWith(3, 7, 1)
    expect(within(section).queryByRole('button', { name: 'Load more' })).not.toBeInTheDocument()
  })

  it('recovers an initial failure without blocking details', async () => {
    const user = userEvent.setup()
    vi.mocked(getRelatedExpenses).mockRejectedValueOnce(new Error('Offline'))
    render(<DialogHarness />)
    await user.click(screen.getByRole('button', { name: 'Open transaction' }))
    await user.click(await screen.findByRole('button', { name: 'Retry related expenses' }))
    expect(await screen.findByText('No other expenses with this counterparty.')).toBeInTheDocument()
    expect(getRelatedExpenses).toHaveBeenLastCalledWith(7, 0)
  })

  it('uses the latest source for missing-counterparty presentation when browsing old snapshots', async () => {
    const user = userEvent.setup()
    vi.mocked(getTransactionVersions).mockResolvedValue([
      firstVersion,
      { versionNumber: 2, transaction: { ...fullTransaction, counterparty: ' \t' }, createdAt: null },
    ])
    render(<DialogHarness />)
    await user.click(screen.getByRole('button', { name: 'Open transaction' }))
    await user.click(await screen.findByRole('tab', { name: 'Original version' }))
    expect(screen.getByText('Related expenses are unavailable because this transaction has no counterparty.')).toBeInTheDocument()
    expect(getRelatedExpenses).toHaveBeenCalledTimes(1)
  })

  it('ignores closed and switched source requests and prevents duplicate loads', async () => {
    const user = userEvent.setup()
    let resolveFirst!: (value: Awaited<ReturnType<typeof getRelatedExpenses>>) => void
    vi.mocked(getRelatedExpenses).mockImplementationOnce(() => new Promise((resolve) => { resolveFirst = resolve }))
    function SwitchHarness() {
      const [selected, setSelected] = useState<TransactionRecord | null>(fullTransaction)
      const ref = useRef<HTMLButtonElement>(null)
      return <><button ref={ref} onClick={() => setSelected({ ...fullTransaction, id: 99 })}>Switch source</button><TransactionDetailsDialog transaction={selected} openerRef={ref} onClose={() => setSelected(null)} /></>
    }
    render(<SwitchHarness />)
    expect(screen.getByText('Loading related expenses…')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Switch source' }))
    expect(await screen.findByText('No other expenses with this counterparty.')).toBeInTheDocument()
    resolveFirst({ items: [{ id: 1, valueDate: null, amount: '-9999', currency: 'USD' }], page: 0, size: 20, totalElements: 1, totalPages: 1 })
    await user.click(screen.getByRole('button', { name: 'Close transaction details' }))
    await user.click(screen.getByRole('button', { name: 'Switch source' }))
    expect(await screen.findByText('No other expenses with this counterparty.')).toBeInTheDocument()
    expect(screen.queryByText('-9,999 USD')).not.toBeInTheDocument()
  })

  it('ignores an in-flight response after closing and reopening the same source', async () => {
    const user = userEvent.setup()
    let finish!: (value: Awaited<ReturnType<typeof getRelatedExpenses>>) => void
    vi.mocked(getRelatedExpenses).mockImplementationOnce(() => new Promise((resolve) => { finish = resolve }))
    render(<DialogHarness />)
    await user.click(screen.getByRole('button', { name: 'Open transaction' }))
    expect(screen.getByText('Loading related expenses…')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Close transaction details' }))
    await user.click(screen.getByRole('button', { name: 'Open transaction' }))
    expect(await screen.findByText('No other expenses with this counterparty.')).toBeInTheDocument()
    finish({ items: [{ id: 100, valueDate: null, amount: '-888', currency: 'EUR' }], page: 0, size: 20, totalElements: 1, totalPages: 1 })
    await user.click(screen.getByRole('button', { name: 'Edit transaction' }))
    expect(screen.queryByText('-888 EUR')).not.toBeInTheDocument()
  })

  it('keeps rows visible and prevents duplicate requests during append loading', async () => {
    const user = userEvent.setup()
    const row = { id: 8, valueDate: null, amount: '-10', currency: 'EUR' }
    let finish!: (value: Awaited<ReturnType<typeof getRelatedExpenses>>) => void
    vi.mocked(getRelatedExpenses)
      .mockResolvedValueOnce({ items: [row], page: 0, size: 20, totalElements: 21, totalPages: 2 })
      .mockImplementationOnce(() => new Promise((resolve) => { finish = resolve }))
    render(<DialogHarness />)
    await user.click(screen.getByRole('button', { name: 'Open transaction' }))
    const more = await screen.findByRole('button', { name: 'Load more' })
    more.focus()
    await user.keyboard('{Enter}')
    expect(more).toBeDisabled()
    expect(screen.getByText('Loading more expenses…')).toBeInTheDocument()
    expect(screen.getByText('-10 EUR')).toBeInTheDocument()
    fireEvent.click(more)
    expect(getRelatedExpenses).toHaveBeenCalledTimes(2)
    finish({ items: [], page: 1, size: 20, totalElements: 21, totalPages: 2 })
    expect(await screen.findByText('-10 EUR')).toBeInTheDocument()
  })

  it('opens the latest related transaction in the same panel and retains the original opener', async () => {
    const user = userEvent.setup()
    const target = { ...fullTransaction, id: 8, description: 'Target expense', amount: '-42', category: 'Food' }
    vi.mocked(getRelatedExpenses).mockImplementation(async (id) => ({
      items: id === 7 ? [{ id: 8, valueDate: null, amount: '-42', currency: 'EUR' }] : [],
      page: 0, size: 20, totalElements: id === 7 ? 1 : 0, totalPages: id === 7 ? 1 : 0,
    }))
    vi.mocked(getTransactionVersions).mockImplementation(async (id) => id === 7 ? [firstVersion] : [
      { versionNumber: 2, transaction: target, createdAt: null },
      { versionNumber: 1, transaction: { ...target, description: 'Old target' }, createdAt: null },
    ])
    render(<DialogHarness />)
    const opener = screen.getByRole('button', { name: 'Open transaction' })
    await user.click(opener)
    const row = await screen.findByRole('button', { name: 'Open transaction: —, -42 EUR' })
    const scroll = screen.getByRole('dialog').querySelector('.transaction-details-scroll')!
    scroll.scrollTop = 240
    const focus = vi.spyOn(HTMLElement.prototype, 'focus')
    row.focus()
    await user.keyboard('{Enter}')
    expect(await screen.findByText('Target expense', { selector: '.transaction-details-fields dd' })).toBeInTheDocument()
    expect(getRelatedExpenses).toHaveBeenLastCalledWith(8, 0)
    expect(getTransactionVersions).toHaveBeenCalledTimes(2)
    expect(getTransactionCategories).toHaveBeenCalledTimes(1)
    expect(screen.getByRole('dialog').querySelector('.transaction-details-scroll')!.scrollTop).toBe(240)
    expect(screen.queryByText('Loading categories and version history…')).not.toBeInTheDocument()
    expect(screen.queryByText('Loading related expenses…')).not.toBeInTheDocument()
    expect(focus).toHaveBeenCalledWith({ preventScroll: true })
    focus.mockRestore()
    expect(screen.getByRole('heading', { name: 'Transaction details' })).toHaveFocus()
    expect(screen.queryByText('Monthly rent')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Close transaction details' }))
    expect(opener).toHaveFocus()
    await user.click(opener)
    expect(await screen.findByText('Monthly rent')).toBeInTheDocument()
  })

  it('retains the source on navigation failure, supports retry, and blocks navigation during editing', async () => {
    const user = userEvent.setup()
    const target = { ...fullTransaction, id: 8, description: 'Target expense' }
    vi.mocked(getRelatedExpenses).mockResolvedValue({ items: [{ id: 8, valueDate: null, amount: '-42', currency: 'EUR' }], page: 0, size: 20, totalElements: 1, totalPages: 1 })
    vi.mocked(getTransactionVersions).mockResolvedValueOnce([firstVersion]).mockRejectedValueOnce(new Error('Cannot open target')).mockResolvedValue([{ versionNumber: 1, transaction: target, createdAt: null }])
    render(<DialogHarness />)
    await user.click(screen.getByRole('button', { name: 'Open transaction' }))
    const row = await screen.findByRole('button', { name: 'Open transaction: —, -42 EUR' })
    await user.click(screen.getByRole('button', { name: 'Edit transaction' }))
    expect(row).toBeDisabled()
    expect(screen.getByText('Save or cancel your changes before opening another transaction.')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Cancel' }))
    await user.click(row)
    expect(await screen.findByRole('alert')).toHaveTextContent('We could not open this transaction. Select it again to retry.')
    expect(screen.getByText('Monthly rent')).toBeInTheDocument()
    await user.click(row)
    expect(await screen.findByText('Target expense')).toBeInTheDocument()
  })

  it('ignores a target-opening response after closing the panel', async () => {
    const user = userEvent.setup()
    let finish!: (history: TransactionVersion[]) => void
    vi.mocked(getRelatedExpenses).mockResolvedValue({ items: [{ id: 8, valueDate: null, amount: '-42', currency: 'EUR' }], page: 0, size: 20, totalElements: 1, totalPages: 1 })
    vi.mocked(getTransactionVersions).mockResolvedValueOnce([firstVersion]).mockImplementationOnce(() => new Promise((resolve) => { finish = resolve })).mockResolvedValue([firstVersion])
    render(<DialogHarness />)
    await user.click(screen.getByRole('button', { name: 'Open transaction' }))
    const row = await screen.findByRole('button', { name: 'Open transaction: —, -42 EUR' })
    await user.click(row)
    expect(screen.getByText('Opening transaction…')).toBeInTheDocument()
    expect(row).toHaveAttribute('aria-disabled', 'true')
    await user.click(screen.getByRole('button', { name: 'Close transaction details' }))
    await user.click(screen.getByRole('button', { name: 'Open transaction' }))
    finish([{ versionNumber: 1, transaction: { ...fullTransaction, id: 8, description: 'Stale target' }, createdAt: null }])
    await user.click(screen.getByRole('button', { name: 'Edit transaction' }))
    expect(screen.queryByText('Stale target')).not.toBeInTheDocument()
    expect(screen.getByText('Monthly rent')).toBeInTheDocument()
  })

  it('shows a save error while retaining the current transaction details', async () => {
    const user = userEvent.setup()
    vi.mocked(updateTransactionCategory).mockRejectedValue(
      Object.assign(new Error('network failure'), { isAxiosError: true }),
    )
    render(<DialogHarness />)

    await user.click(screen.getByRole('button', { name: 'Open transaction' }))
    await user.click(await screen.findByRole('button', { name: 'Edit transaction' }))
    await user.selectOptions(screen.getByRole('combobox', { name: 'Transaction category' }), 'Food')
    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('network failure')
    expect(screen.getByRole('combobox', { name: 'Transaction category' })).toHaveValue('Food')
    expect(getTransactionVersions).toHaveBeenCalledTimes(1)
  })
})
