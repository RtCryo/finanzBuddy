import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useRef, useState } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  createPersonalTransactionCategory,
  getTransactionCategories,
  getTransactionVersions,
  updateTransactionCategory,
} from '../api/transactions'
import type { TransactionRecord, TransactionVersion } from '../types/transaction'
import { TransactionDetailsDialog } from './TransactionDetailsDialog'

vi.mock('../api/transactions', () => ({
  createPersonalTransactionCategory: vi.fn(),
  getTransactionCategories: vi.fn(),
  getTransactionVersions: vi.fn(),
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
