import { useEffect, useLayoutEffect, useRef, useState, type CSSProperties, type RefObject } from 'react'
import {
  createPersonalTransactionCategory,
  getTransactionCategories,
  getTransactionVersions,
  getRelatedExpenses,
  updateTransactionCategory,
} from '../api/transactions'
import { getErrorMessage } from '../api/errors'
import { formatDateOnly, formatInstant, formatTransactionCategory, formatTransactionText } from '../format'
import type { RelatedExpense, RelatedExpensePage, TransactionRecord, TransactionVersion } from '../types/transaction'

type PreparedTransaction = {
  transaction: TransactionRecord
  versions: TransactionVersion[]
  related: RelatedExpensePage
  categories: string[]
  scrollTop: number
}

type TransactionDetailsDialogProps = {
  transaction: TransactionRecord | null
  openerRef: RefObject<HTMLElement | null>
  onClose: () => void
  onSaved?: (transaction: TransactionRecord) => void
}

const fields = [
  ['Booking date', (transaction: TransactionRecord) => formatDateOnly(transaction.bookingDate)],
  ['Value date', (transaction: TransactionRecord) => formatDateOnly(transaction.valueDate)],
  ['Description', (transaction: TransactionRecord) => formatTransactionText(transaction.description)],
] as const

const comparisonFields = [
  ...fields,
  ['Counterparty', (transaction: TransactionRecord) => formatTransactionText(transaction.counterparty)],
  ['Amount', (transaction: TransactionRecord) => formatTransactionAmount(transaction.amount)],
  ['Currency', (transaction: TransactionRecord) => transaction.currency || '—'],
  ['Category', (transaction: TransactionRecord) => formatTransactionCategory(transaction.category)],
] as const

export function TransactionDetailsDialog({ transaction, openerRef, onClose, onSaved }: TransactionDetailsDialogProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)

  function closeDialog() {
    const dialog = dialogRef.current
    if (dialog?.open) dialog.close()
  }

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
      {transaction && (
        <TransactionDetailsSession
          key={transaction.id}
          transaction={transaction}
          onSaved={onSaved}
          closeDialog={closeDialog}
        />
      )}
    </dialog>
  )
}

function TransactionDetailsSession(props: {
  transaction: TransactionRecord
  onSaved?: (transaction: TransactionRecord) => void
  closeDialog: () => void
}) {
  const [prepared, setPrepared] = useState<PreparedTransaction | null>(null)
  const current = prepared?.transaction ?? props.transaction
  return <TransactionDetailsContent
    key={current.id}
    transaction={current}
    prepared={prepared}
    onNavigate={setPrepared}
    onSaved={props.onSaved}
    closeDialog={props.closeDialog}
  />
}

function TransactionDetailsContent({
  transaction,
  onSaved,
  closeDialog,
  onNavigate,
  prepared,
}: {
  transaction: TransactionRecord
  onSaved?: (transaction: TransactionRecord) => void
  closeDialog: () => void
  onNavigate: (transaction: PreparedTransaction) => void
  prepared: PreparedTransaction | null
}) {
  const transactionId = transaction.id
  const headingRef = useRef<HTMLHeadingElement>(null)
  const scrollRef = useRef<HTMLDivElement>(null)
  useLayoutEffect(() => {
    if (scrollRef.current) scrollRef.current.scrollTop = prepared?.scrollTop ?? 0
    if (headingRef.current?.closest('dialog')?.open) headingRef.current.focus({ preventScroll: true })
  }, [transactionId, prepared?.scrollTop])
  const createCategoryOption = '__create_category__'
  const [categories, setCategories] = useState<string[]>(prepared?.categories ?? [])
  const [versions, setVersions] = useState<TransactionVersion[]>(prepared?.versions ?? [])
  const [selectedVersionIndex, setSelectedVersionIndex] = useState((prepared?.versions.length ?? 0) - 1)
  const [isEditing, setIsEditing] = useState(false)
  const [isOpeningTarget, setIsOpeningTarget] = useState(false)
  const [category, setCategory] = useState(transaction.category ?? '')
  const [isCreatingCategory, setIsCreatingCategory] = useState(false)
  const [newCategory, setNewCategory] = useState('')
  const [isLoading, setIsLoading] = useState(!prepared)
  const [isSaving, setIsSaving] = useState(false)
  const [error, setError] = useState('')
  const versionTabsRef = useRef<HTMLDivElement>(null)
  const [versionIndicator, setVersionIndicator] = useState({ offset: 0, width: 0 })

  useEffect(() => {
    if (prepared) return
    let isCurrent = true
    Promise.all([getTransactionCategories(), getTransactionVersions(transactionId)])
      .then(([availableCategories, history]) => {
        if (!isCurrent) return
        setCategories(availableCategories)
        const orderedHistory = [...history].sort((left, right) => left.versionNumber - right.versionNumber)
        setVersions(orderedHistory)
        setSelectedVersionIndex(orderedHistory.length - 1)
      })
      .catch((requestError) => {
        if (isCurrent) {
          setError(getErrorMessage(requestError, 'We could not load category choices or transaction history.'))
        }
      })
      .finally(() => {
        if (isCurrent) setIsLoading(false)
      })
    return () => {
      isCurrent = false
    }
  }, [transactionId, prepared])

  const latestVersionIndex = versions.length - 1
  const displayedTransaction = versions[selectedVersionIndex]?.transaction ?? transaction
  const isLatestVersion = selectedVersionIndex === latestVersionIndex || versions.length === 0
  const previousVersion = versions[selectedVersionIndex - 1]
  const changes = previousVersion
    ? comparisonFields.flatMap(([label, getValue]) => {
      const previousValue = getValue(previousVersion.transaction)
      const currentValue = getValue(displayedTransaction)
      return previousValue === currentValue ? [] : [{ label, previousValue, currentValue }]
    })
    : []

  useLayoutEffect(() => {
    if (versions.length < 2 || selectedVersionIndex < 0) return
    const tabs = versionTabsRef.current
    if (!tabs) return
    const versionTabs: HTMLDivElement = tabs
    const selectedTab = versionTabs.querySelector<HTMLButtonElement>('[role="tab"][aria-selected="true"]')
    if (!selectedTab) return

    function updateIndicator() {
      const activeTab = versionTabs.querySelector<HTMLButtonElement>('[role="tab"][aria-selected="true"]')
      if (!activeTab) return
      const tabsBounds = versionTabs.getBoundingClientRect()
      const activeTabBounds = activeTab.getBoundingClientRect()
      const nextIndicator = {
        offset: activeTabBounds.left - tabsBounds.left + versionTabs.scrollLeft,
        width: activeTabBounds.width,
      }
      setVersionIndicator((current) => current.offset === nextIndicator.offset && current.width === nextIndicator.width
        ? current
        : nextIndicator)
    }

    updateIndicator()
    const resizeObserver = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(updateIndicator)
    resizeObserver?.observe(versionTabs)
    resizeObserver?.observe(selectedTab)
    window.addEventListener('resize', updateIndicator)
    return () => {
      resizeObserver?.disconnect()
      window.removeEventListener('resize', updateIndicator)
    }
  }, [selectedVersionIndex, versions.length])

  function cancelEditing() {
    setCategory(displayedTransaction.category ?? '')
    setNewCategory('')
    setIsCreatingCategory(false)
    setIsEditing(false)
    setError('')
  }

  async function saveChanges() {
    const categoryToSave = isCreatingCategory ? newCategory.trim() : category
    if (!categoryToSave) return
    setError('')
    setIsSaving(true)
    try {
      const updated = isCreatingCategory
        ? (await createPersonalTransactionCategory(transactionId, categoryToSave)).transaction
        : await updateTransactionCategory(transactionId, categoryToSave)
      setCategories((current) => current.includes(updated.category ?? '') || !updated.category
        ? current
        : [...current, updated.category].sort((left, right) => left.localeCompare(right)))
      setCategory(updated.category ?? '')
      setNewCategory('')
      setIsCreatingCategory(false)
      setIsEditing(false)
      try {
        const history = await getTransactionVersions(transactionId)
        const orderedHistory = [...history].sort((left, right) => left.versionNumber - right.versionNumber)
        setVersions(orderedHistory)
        setSelectedVersionIndex(orderedHistory.length - 1)
      } catch (requestError) {
        setError(getErrorMessage(requestError, 'The category was saved, but version history could not be refreshed.'))
      }
      onSaved?.(updated)
    } catch (requestError) {
      setError(getErrorMessage(requestError, isCreatingCategory
        ? 'We could not create or assign this category. Please try again.'
        : 'We could not save this category. Please try again.'))
    } finally {
      setIsSaving(false)
    }
  }

  function changeVersion(index: number) {
    if (index < 0 || index >= versions.length) return
    setSelectedVersionIndex(index)
    setIsEditing(false)
    setCategory(versions[index].transaction.category ?? '')
    setIsCreatingCategory(false)
    setNewCategory('')
    setError('')
  }

  return (
    <div className="transaction-details-content">
      <div className="transaction-details-heading">
        <h2 ref={headingRef} tabIndex={-1} id="transaction-details-title">{isEditing ? 'Edit transaction' : 'Transaction details'}</h2>
        {!isEditing && isLatestVersion && (
          <button className="button button-secondary" type="button" onClick={() => {
            setCategory(displayedTransaction.category ?? '')
            setIsEditing(true)
          }} disabled={isLoading || isOpeningTarget}>
            Edit transaction
          </button>
        )}
        <button className="transaction-details-close" type="button" aria-label="Close transaction details" onClick={closeDialog}>
          <svg viewBox="0 0 20 20" aria-hidden="true"><path d="m5 5 10 10M15 5 5 15" /></svg>
        </button>
      </div>
      <div ref={scrollRef} className="transaction-details-scroll">
        <div className="transaction-details-summary">
          <div>
            <p>Counterparty</p>
            <h3>{formatTransactionText(displayedTransaction.counterparty)}</h3>
            <span>{formatDateOnly(displayedTransaction.valueDate)}</span>
          </div>
          <div className="transaction-details-amount">
            <strong>{formatTransactionAmount(displayedTransaction.amount)}</strong>
            <span>{displayedTransaction.currency || '—'}</span>
          </div>
        </div>
        {versions.length > 1 && (
          <section className="transaction-version-selector" aria-label="Transaction version history">
            <div className="transaction-version-selector-heading">
              <span>Version history</span>
              <span>{versions.length} versions</span>
            </div>
            <div
              ref={versionTabsRef}
              className="transaction-version-tabs"
              role="tablist"
              aria-label="Transaction versions"
              style={{
                '--selected-version-offset': `${versionIndicator.offset}px`,
                '--selected-version-width': `${versionIndicator.width}px`,
              } as CSSProperties}
            >
              <span className="transaction-version-indicator" aria-hidden="true" />
              {versions.map((version, index) => (
                <button
                  key={version.versionNumber}
                  className={`transaction-version-tab${index === selectedVersionIndex ? ' selected' : ''}`}
                  type="button"
                  role="tab"
                  aria-selected={index === selectedVersionIndex}
                  aria-label={index === 0 ? 'Original version' : `Version ${version.versionNumber}${index === latestVersionIndex ? ', current' : ''}`}
                  onClick={() => changeVersion(index)}
                  disabled={isEditing}
                >
                  {index === 0 ? 'Original' : `Version ${version.versionNumber}`}
                  {index === latestVersionIndex && <span>Current</span>}
                </button>
              ))}
            </div>
            <p className="transaction-version-date">
              {versions[selectedVersionIndex]?.createdAt
                ? `Selected snapshot · ${formatInstant(versions[selectedVersionIndex].createdAt!)}`
                : selectedVersionIndex === 0 ? 'Original snapshot · imported from statement' : 'Selected transaction snapshot'}
            </p>
          </section>
        )}
        {versions.length > 1 && selectedVersionIndex === 0 && (
          <div className="transaction-version-original-note">This is the original snapshot imported from the statement.</div>
        )}
        {versions.length > 1 && selectedVersionIndex > 0 && previousVersion && (
          <section className="transaction-version-diff" aria-label={`Changes since version ${previousVersion.versionNumber}`}>
            <h3>Changes since version {previousVersion.versionNumber}</h3>
            {changes.length > 0 ? (
              <dl>
                {changes.map(({ label, previousValue, currentValue }) => (
                  <div key={label}>
                    <dt>{label}</dt>
                    <dd><span>{previousValue}</span><span aria-hidden="true">→</span><strong>{currentValue}</strong></dd>
                  </div>
                ))}
              </dl>
            ) : <p>No transaction fields changed in this version.</p>}
          </section>
        )}
        <section className="transaction-details-section" aria-label="Transaction details">
          <h3>Transaction</h3>
          <dl className="transaction-details-fields">
            {fields.map(([label, getValue]) => (
              <div key={label}>
                <dt>{label}</dt>
                <dd>{getValue(displayedTransaction)}</dd>
              </div>
            ))}
            <div>
              <dt>Category</dt>
              <dd>
                {isEditing ? (
                  <div className="transaction-category-edit-control">
                    <select
                      aria-label="Transaction category"
                      value={isCreatingCategory ? createCategoryOption : category}
                      onChange={(event) => {
                        const value = event.target.value
                        setIsCreatingCategory(value === createCategoryOption)
                        if (value !== createCategoryOption) setCategory(value)
                      }}
                      disabled={isLoading || isSaving}
                    >
                      <option value="" disabled>Select a category</option>
                      {category && !categories.includes(category) && <option value={category}>{category}</option>}
                      {categories.map((value) => <option key={value} value={value}>{value}</option>)}
                      <option value={createCategoryOption}>+ Create category…</option>
                    </select>
                    {isCreatingCategory && (
                      <label>
                        <span>New category name</span>
                        <input
                          aria-label="New personal category"
                          value={newCategory}
                          onChange={(event) => setNewCategory(event.target.value)}
                          maxLength={255}
                          disabled={isSaving}
                        />
                      </label>
                    )}
                  </div>
                ) : formatTransactionCategory(displayedTransaction.category)}
              </dd>
            </div>
          </dl>
        </section>
        <RelatedExpenses
          onNavigate={(target) => onNavigate({ ...target, categories, scrollTop: scrollRef.current?.scrollTop ?? 0 })}
          initialPage={prepared?.related}
          navigationDisabled={isEditing}
          onOpeningChange={setIsOpeningTarget}
          transactionId={transactionId}
          counterparty={(versions[latestVersionIndex]?.transaction ?? transaction).counterparty}
        />
        {isLoading && <p className="transaction-history-loading" role="status">Loading categories and version history…</p>}
        {error && <p className="notice notice-error transaction-details-error" role="alert">{error}</p>}
      </div>
      {isEditing && (
        <div className="transaction-details-actions">
          <button className="button button-secondary" type="button" onClick={cancelEditing} disabled={isSaving}>Cancel</button>
          <button
            className="button button-primary"
            type="button"
            onClick={() => void saveChanges()}
            disabled={isLoading || isSaving || !(isCreatingCategory ? newCategory.trim() : category) || (!isCreatingCategory && category === displayedTransaction.category)}
          >
            {isSaving ? 'Saving…' : 'Save changes'}
          </button>
        </div>
      )}
    </div>
  )
}

function RelatedExpenses({ transactionId, counterparty, onNavigate, navigationDisabled, onOpeningChange, initialPage }: {
  transactionId: number
  counterparty: string | null
  onNavigate: (transaction: Omit<PreparedTransaction, 'categories' | 'scrollTop'>) => void
  initialPage?: RelatedExpensePage
  navigationDisabled: boolean
  onOpeningChange: (opening: boolean) => void
}) {
  const [rows, setRows] = useState<RelatedExpense[]>(initialPage?.items ?? [])
  const [nextPage, setNextPage] = useState(initialPage ? initialPage.page + 1 : 0)
  const [hasMore, setHasMore] = useState(initialPage ? initialPage.page + 1 < initialPage.totalPages : false)
  const [loading, setLoading] = useState(!initialPage)
  const [error, setError] = useState('')
  const lifecycle = useRef<AbortController | null>(null)
  const pending = useRef(false)
  const navigating = useRef(false)
  const [openingId, setOpeningId] = useState<number | null>(null)
  const [navigationError, setNavigationError] = useState('')

  async function openExpense(id: number) {
    if (navigating.current || navigationDisabled) return
    const signal = lifecycle.current?.signal
    navigating.current = true
    onOpeningChange(true)
    setOpeningId(id)
    setNavigationError('')
    try {
      const [history, related] = await Promise.all([getTransactionVersions(id), getRelatedExpenses(id, 0)])
      if (signal?.aborted) return
      const ordered = [...history].sort((a, b) => a.versionNumber - b.versionNumber)
      const latest = ordered[ordered.length - 1]
      if (!latest) throw new Error('This transaction is unavailable. Please try again.')
      onNavigate({ transaction: latest.transaction, versions: ordered, related })
    } catch (requestError) {
      if (!signal?.aborted) setNavigationError(getErrorMessage(requestError, 'We could not open this transaction. Select it again to retry.'))
    } finally {
      if (!signal?.aborted) {
        navigating.current = false
        onOpeningChange(false)
        setOpeningId(null)
      }
    }
  }

  async function load(page: number, signal = lifecycle.current?.signal) {
    if (pending.current) return
    pending.current = true
    setLoading(true)
    setError('')
    try {
      const result = await getRelatedExpenses(transactionId, page)
      if (signal?.aborted) return
      setRows((current) => page === 0 ? result.items : [...current, ...result.items])
      setNextPage(result.page + 1)
      setHasMore(result.page + 1 < result.totalPages)
    } catch (requestError) {
      if (!signal?.aborted) {
        setError(getErrorMessage(requestError, 'We could not load related expenses. Please try again.'))
      }
    } finally {
      if (!signal?.aborted) {
        pending.current = false
        setLoading(false)
      }
    }
  }

  useEffect(() => {
    const controller = new AbortController()
    lifecycle.current = controller
    pending.current = false
    if (!initialPage) void load(0, controller.signal)
    return () => { controller.abort() }
    // The enclosing content is keyed by source ID; snapshot selection never reloads this set.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [transactionId])

  return (
    <section className="transaction-details-section related-expenses" aria-label="Other expenses with this counterparty">
      <h3>Other expenses with this counterparty</h3>
      {!counterparty?.trim() ? <p>Related expenses are unavailable because this transaction has no counterparty.</p> : (
        <>
          {rows.length > 0 && (
            <ul className="related-expense-list">
              {rows.map((row) => (
                <li key={row.id}>
                  <button className="related-expense-row" type="button"
                    aria-label={`Open transaction: ${formatDateOnly(row.valueDate)}, ${formatTransactionAmount(row.amount)} ${row.currency || '—'}`}
                    disabled={navigationDisabled}
                    aria-disabled={openingId !== null || navigationDisabled}
                    aria-busy={openingId === row.id}
                    onClick={() => void openExpense(row.id)}>
                    <span>{formatDateOnly(row.valueDate)}</span>
                    <strong>{formatTransactionAmount(row.amount)} {row.currency || '—'}</strong>
                  </button>
                </li>
              ))}
            </ul>
          )}
          {loading && <p role="status">{rows.length ? 'Loading more expenses…' : 'Loading related expenses…'}</p>}
          {!loading && !error && rows.length === 0 && <p>No other expenses with this counterparty.</p>}
          {error && <p className="notice notice-error" role="alert">{error}</p>}
          {openingId !== null && <span className="visually-hidden" role="status">Opening transaction…</span>}
          {navigationError && <p className="notice notice-error" role="alert">{navigationError}</p>}
          {navigationDisabled && rows.length > 0 && <p>Save or cancel your changes before opening another transaction.</p>}
          {error
            ? <button className="button button-secondary" type="button" onClick={() => void load(nextPage)} disabled={loading}>Retry related expenses</button>
            : hasMore && <button className="button button-secondary" type="button" onClick={() => void load(nextPage)} disabled={loading}>Load more</button>}
        </>
      )}
    </section>
  )
}

function formatTransactionAmount(amount: number | string | null) {
  if (amount === null || amount === '') return '—'
  const value = Number(amount)
  if (!Number.isFinite(value)) return `${amount}`
  return new Intl.NumberFormat('en-US', { maximumFractionDigits: 4 }).format(value)
}
