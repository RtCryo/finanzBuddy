import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useRef, useState } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  editTransaction,
  getAssignedTransactionTags,
  getTransactionCategories,
  getTransactionTags,
  getTransactionVersions,
} from '../api/transactions'
import type { TransactionRecord, TransactionVersion } from '../types/transaction'
import { TransactionDetailsDialog } from './TransactionDetailsDialog'

vi.mock('../api/transactions', () => ({
  editTransaction: vi.fn(),
  getAssignedTransactionTags: vi.fn(),
  getTransactionCategories: vi.fn(),
  getTransactionTags: vi.fn(),
  getTransactionVersions: vi.fn(),
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
    vi.clearAllMocks()
    vi.mocked(getTransactionCategories).mockResolvedValue(['Food', 'Housing'])
    vi.mocked(getTransactionTags).mockResolvedValue([])
    vi.mocked(getAssignedTransactionTags).mockResolvedValue([])
    vi.mocked(getTransactionVersions).mockResolvedValue([firstVersion])
    vi.mocked(editTransaction).mockResolvedValue({ transaction: { ...fullTransaction, category: 'Food' }, tags: [] })
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
    expect(screen.getByRole('region', { name: 'Tags' }).querySelectorAll('.tag-chip')).toHaveLength(0)
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
    expect(editTransaction).not.toHaveBeenCalled()
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
    vi.mocked(editTransaction).mockResolvedValue({ transaction: secondVersion.transaction, tags: [] })
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
    expect(editTransaction).toHaveBeenCalledWith(7, { category: 'Food', tags: [] })
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
    vi.mocked(editTransaction).mockResolvedValue({ transaction: { ...fullTransaction, category: 'Travel' }, tags: [] })
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

    expect(editTransaction).toHaveBeenCalledWith(7, { newCategoryName: 'Travel', tags: [] })
    expect(onSaved).toHaveBeenCalledWith({ ...fullTransaction, category: 'Travel' })
    expect(await screen.findByText('Travel', { selector: '.transaction-details-fields dd' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Create and assign' })).not.toBeInTheDocument()
  })

  it('shows a save error while retaining the current transaction details', async () => {
    const user = userEvent.setup()
    vi.mocked(editTransaction).mockRejectedValue(
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

  it('adds and removes tags with one tag-only save and no new version', async () => {
    const user = userEvent.setup()
    const travel = { id: 4, name: 'Travel' }
    const work = { id: 5, name: 'Work' }
    vi.mocked(getTransactionTags).mockResolvedValue([travel, work])
    vi.mocked(getAssignedTransactionTags).mockResolvedValue([travel])
    vi.mocked(editTransaction).mockResolvedValue({ transaction: fullTransaction, tags: [work] })
    render(<DialogHarness />)

    await user.click(screen.getByRole('button', { name: 'Open transaction' }))
    expect(screen.getByRole('region', { name: 'Tags' })).toHaveTextContent('Travel')
    expect(screen.queryByRole('button', { name: 'Remove tag Travel' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Edit transaction' }))
    await user.click(screen.getByRole('button', { name: 'Remove tag Travel' }))
    const search = screen.getByRole('textbox', { name: 'Search or create tags' })
    expect(search).toHaveFocus()
    await user.click(search)
    await user.click(screen.getByRole('option', { name: /Work/ }))
    await user.click(search)
    expect(screen.getByRole('option', { name: /Travel/ })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    await waitFor(() => expect(editTransaction).toHaveBeenCalledWith(7, { tags: [{ id: 5 }] }))
    expect(screen.getByRole('region', { name: 'Tags' })).toHaveTextContent('Work')
    expect(screen.queryByRole('tablist', { name: 'Transaction versions' })).not.toBeInTheDocument()
    expect(getTransactionVersions).toHaveBeenCalledTimes(2)
  })

  it('keeps a new tag draft after a failed save and discards it on cancel', async () => {
    const user = userEvent.setup()
    vi.mocked(editTransaction).mockRejectedValue(new Error('offline'))
    render(<DialogHarness />)
    await user.click(screen.getByRole('button', { name: 'Open transaction' }))
    await user.click(screen.getByRole('button', { name: 'Edit transaction' }))
    const search = screen.getByRole('textbox', { name: 'Search or create tags' })
    await user.type(search, 'Personal')
    await user.keyboard('{Enter}')
    expect(screen.getByRole('button', { name: 'Remove tag Personal' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Save changes' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('We could not save these changes')
    expect(screen.getByRole('button', { name: 'Remove tag Personal' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Cancel' }))
    expect(screen.getByRole('region', { name: 'Tags' })).not.toHaveTextContent('Personal')
  })

  it('shows current tags as read-only metadata beside an older snapshot', async () => {
    const user = userEvent.setup()
    const current = { ...fullTransaction, category: 'Food' }
    vi.mocked(getTransactionVersions).mockResolvedValue([
      firstVersion, { versionNumber: 2, transaction: current, createdAt: '2024-02-02T10:00:00Z' },
    ])
    vi.mocked(getAssignedTransactionTags).mockResolvedValue([{ id: 4, name: 'Travel' }])
    render(<DialogHarness />)
    await user.click(screen.getByRole('button', { name: 'Open transaction' }))
    await user.click(await screen.findByRole('tab', { name: 'Original version' }))
    expect(screen.getByRole('region', { name: 'Current tags' })).toHaveTextContent('Travel')
    expect(screen.queryByRole('button', { name: 'Remove tag Travel' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Edit transaction' })).not.toBeInTheDocument()
    expect(screen.getByText('Travel').closest('.transaction-version-diff')).toBeNull()
  })

  it('discards an unsaved tag when the editor is closed and reopened', async () => {
    const user = userEvent.setup()
    render(<DialogHarness />)
    await user.click(screen.getByRole('button', { name: 'Open transaction' }))
    await user.click(screen.getByRole('button', { name: 'Edit transaction' }))
    await user.type(screen.getByRole('textbox', { name: 'Search or create tags' }), 'Draft')
    await user.keyboard('{Enter}')
    expect(screen.getByRole('button', { name: 'Remove tag Draft' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Close transaction details' }))
    await user.click(screen.getByRole('button', { name: 'Open transaction' }))
    expect(screen.getByRole('region', { name: 'Tags' })).not.toHaveTextContent('Draft')
    expect(editTransaction).not.toHaveBeenCalled()
  })
})
