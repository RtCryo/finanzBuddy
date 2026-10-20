import { useEffect, useRef, type RefObject } from 'react'
import { formatDateOnly, formatTransactionCategory, formatTransactionText } from '../format'
import type { TransactionRecord } from '../types/transaction'

type TransactionDetailsDialogProps = {
  transaction: TransactionRecord | null
  openerRef: RefObject<HTMLElement | null>
  onClose: () => void
}

const fields = [
  ['Booking date', (transaction: TransactionRecord) => formatDateOnly(transaction.bookingDate)],
  ['Value date', (transaction: TransactionRecord) => formatDateOnly(transaction.valueDate)],
  ['Amount', (transaction: TransactionRecord) => formatTransactionAmount(transaction.amount)],
  ['Currency', (transaction: TransactionRecord) => transaction.currency || '—'],
  ['Counterparty', (transaction: TransactionRecord) => formatTransactionText(transaction.counterparty)],
  ['Description', (transaction: TransactionRecord) => formatTransactionText(transaction.description)],
  ['Category', (transaction: TransactionRecord) => formatTransactionCategory(transaction.category)],
] as const

export function TransactionDetailsDialog({ transaction, openerRef, onClose }: TransactionDetailsDialogProps) {
  function closeDialog() {
    const dialog = dialogRef.current
    if (dialog?.open) dialog.close()
  }

  const dialogRef = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    const dialog = dialogRef.current
    if (transaction && dialog && !dialog.open) dialog.showModal()
  }, [transaction])

  function handleClose() {
    onClose()
    openerRef.current?.focus()
  }

  return (
    <dialog
      ref={dialogRef}
      className="transaction-details-dialog"
      aria-labelledby="transaction-details-title"
      onClick={(event) => {
        if (event.target === event.currentTarget) closeDialog()
      }}
      onClose={handleClose}
      onKeyDown={(event) => {
        if (event.key === 'Escape') {
          event.preventDefault()
          closeDialog()
        }
      }}
      onCancel={(event) => {
        event.preventDefault()
        closeDialog()
      }}
    >
      <div className="transaction-details-content">
        <div className="transaction-details-heading">
          <div>
            <p>Transaction details</p>
            <h2 id="transaction-details-title">Selected transaction</h2>
          </div>
          <button className="transaction-details-close" type="button" aria-label="Close transaction details" onClick={closeDialog}>
            <svg viewBox="0 0 20 20" aria-hidden="true"><path d="m5 5 10 10M15 5 5 15" /></svg>
          </button>
        </div>
        {transaction && (
          <dl className="transaction-details-fields">
            {fields.map(([label, getValue]) => (
              <div key={label}>
                <dt>{label}</dt>
                <dd>{getValue(transaction)}</dd>
              </div>
            ))}
          </dl>
        )}
      </div>
    </dialog>
  )
}

function formatTransactionAmount(amount: number | string | null) {
  if (amount === null || amount === '') return '—'
  const value = Number(amount)
  if (!Number.isFinite(value)) return `${amount}`
  return new Intl.NumberFormat('en-US', { maximumFractionDigits: 4 }).format(value)
}
