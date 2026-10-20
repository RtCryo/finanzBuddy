import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useRef, useState } from 'react'
import { afterEach, describe, expect, it } from 'vitest'
import type { TransactionRecord } from '../types/transaction'
import { TransactionDetailsDialog } from './TransactionDetailsDialog'

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

function DialogHarness({ transaction = fullTransaction }: { transaction?: TransactionRecord }) {
  const [selected, setSelected] = useState<TransactionRecord | null>(null)
  const openerRef = useRef<HTMLButtonElement>(null)

  return (
    <>
      <button ref={openerRef} type="button" onClick={() => setSelected(transaction)}>Open transaction</button>
      <TransactionDetailsDialog transaction={selected} openerRef={openerRef} onClose={() => setSelected(null)} />
    </>
  )
}

describe('TransactionDetailsDialog', () => {
  afterEach(cleanup)

  it('shows every normalized field with formatted dates and amount in an accessible dialog', async () => {
    const user = userEvent.setup()
    render(<DialogHarness />)

    await user.click(screen.getByRole('button', { name: 'Open transaction' }))

    const dialog = screen.getByRole('dialog', { name: 'Selected transaction' })
    expect(dialog).toBeInTheDocument()
    expect(dialog).toHaveTextContent('Booking date')
    expect(dialog).toHaveTextContent('Jan 31, 2024')
    expect(dialog).toHaveTextContent('Value date')
    expect(dialog).toHaveTextContent('Feb 1, 2024')
    expect(dialog).toHaveTextContent('Amount')
    expect(dialog).toHaveTextContent('1,234.5')
    expect(dialog).toHaveTextContent('Currency')
    expect(dialog).toHaveTextContent('EUR')
    expect(dialog).toHaveTextContent('Counterparty')
    expect(dialog).toHaveTextContent('ACME GmbH')
    expect(dialog).toHaveTextContent('Description')
    expect(dialog).toHaveTextContent('Monthly rent')
    expect(dialog).toHaveTextContent('Category')
    expect(dialog).toHaveTextContent('Housing')
  })

  it('uses existing missing-value labels and dismisses with Escape', async () => {
    const user = userEvent.setup()
    render(<DialogHarness transaction={{ ...fullTransaction, bookingDate: null, valueDate: null, amount: null, currency: null, counterparty: ' ', description: '', category: '  ' }} />)

    await user.click(screen.getByRole('button', { name: 'Open transaction' }))
    const dialog = screen.getByRole('dialog', { name: 'Selected transaction' })
    const values = Array.from(dialog.querySelectorAll('dd'), (value) => value.textContent)
    expect(values).toEqual(['—', '—', '—', '—', '—', '—', 'Uncategorized'])

    await user.keyboard('{Escape}')
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
    const dialog = screen.getByRole('dialog', { name: 'Selected transaction' })

    fireEvent.click(dialog.querySelector('.transaction-details-content')!)
    expect(dialog).toBeInTheDocument()

    fireEvent.click(dialog)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(opener).toHaveFocus()
  })
})
