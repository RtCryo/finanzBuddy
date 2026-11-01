import { useEffect, useLayoutEffect, useRef, useState, type CSSProperties, type RefObject } from 'react'
import {
  editTransaction,
  getTransactionTags,
  getAssignedTransactionTags,
  getTransactionCategories,
  getTransactionVersions,
} from '../api/transactions'
import { getErrorMessage } from '../api/errors'
import { formatDateOnly, formatInstant, formatTransactionCategory, formatTransactionText } from '../format'
import type { TransactionRecord, TransactionVersion } from '../types/transaction'
import type { TagDraft, TransactionTag } from '../types/transaction'
import { TagSelection } from './TagSelection'

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
    if (openerRef.current?.isConnected) openerRef.current.focus()
    else document.querySelector<HTMLElement>('[aria-label="Search description or counterparty"]')?.focus()
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
        <TransactionDetailsContent
          key={transaction.id}
          transaction={transaction}
          onSaved={onSaved}
          closeDialog={closeDialog}
        />
      )}
    </dialog>
  )
}

function TransactionDetailsContent({
  transaction,
  onSaved,
  closeDialog,
}: {
  transaction: TransactionRecord
  onSaved?: (transaction: TransactionRecord) => void
  closeDialog: () => void
}) {
  const transactionId = transaction.id
  const createCategoryOption = '__create_category__'
  const [categories, setCategories] = useState<string[]>([])
  const [tagChoices, setTagChoices] = useState<TransactionTag[]>([])
  const [tags, setTags] = useState<TransactionTag[]>([])
  const [tagDraft, setTagDraft] = useState<TagDraft[]>([])
  const [versions, setVersions] = useState<TransactionVersion[]>([])
  const [selectedVersionIndex, setSelectedVersionIndex] = useState(-1)
  const [isEditing, setIsEditing] = useState(false)
  const [category, setCategory] = useState(transaction.category ?? '')
  const [isCreatingCategory, setIsCreatingCategory] = useState(false)
  const [newCategory, setNewCategory] = useState('')
  const [isLoading, setIsLoading] = useState(true)
  const [isSaving, setIsSaving] = useState(false)
  const [error, setError] = useState('')
  const versionTabsRef = useRef<HTMLDivElement>(null)
  const [versionIndicator, setVersionIndicator] = useState({ offset: 0, width: 0 })

  useEffect(() => {
    let isCurrent = true
    Promise.all([getTransactionCategories(), getTransactionVersions(transactionId), getTransactionTags(), getAssignedTransactionTags(transactionId)])
      .then(([availableCategories, history, choices, assigned]) => {
        if (!isCurrent) return
        setCategories(availableCategories)
        setTagChoices(choices)
        setTags(assigned)
        setTagDraft(assigned)
        const orderedHistory = [...history].sort((left, right) => left.versionNumber - right.versionNumber)
        setVersions(orderedHistory)
        setSelectedVersionIndex(orderedHistory.length - 1)
      })
      .catch((requestError) => {
        if (isCurrent) {
          setError(getErrorMessage(requestError, 'We could not load category choices, tags or transaction history.'))
        }
      })
      .finally(() => {
        if (isCurrent) setIsLoading(false)
      })
    return () => {
      isCurrent = false
    }
  }, [transactionId])

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
    setTagDraft(tags)
    setCategory(displayedTransaction.category ?? '')
    setNewCategory('')
    setIsCreatingCategory(false)
    setIsEditing(false)
    setError('')
  }

  async function saveChanges() {
    const categoryToSave = isCreatingCategory ? newCategory.trim() : category
    if (isCreatingCategory && !categoryToSave) return
    setError('')
    setIsSaving(true)
    try {
      const saved = await editTransaction(transactionId, {
        ...(isCreatingCategory ? { newCategoryName: categoryToSave }
          : categoryToSave !== (displayedTransaction.category ?? '') ? { category: categoryToSave } : {}),
        tags: tagDraft.map(tag => tag.id === undefined ? { name: tag.name } : { id: tag.id }),
      })
      const updated = saved.transaction
      setTags(saved.tags)
      setTagDraft(saved.tags)
      setTagChoices(current => [...current.filter(tag => !saved.tags.some(item => item.id === tag.id)), ...saved.tags])
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
        ? 'We could not save these changes. Please try again.'
        : 'We could not save these changes. Please try again.'))
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
        <h2 id="transaction-details-title">{isEditing ? 'Edit transaction' : 'Transaction details'}</h2>
        {!isEditing && isLatestVersion && (
          <button className="button button-secondary" type="button" onClick={() => {
            setTagDraft(tags)
            setCategory(displayedTransaction.category ?? '')
            setIsEditing(true)
          }} disabled={isLoading}>
            Edit transaction
          </button>
        )}
        <button className="transaction-details-close" type="button" aria-label="Close transaction details" onClick={closeDialog}>
          <svg viewBox="0 0 20 20" aria-hidden="true"><path d="m5 5 10 10M15 5 5 15" /></svg>
        </button>
      </div>
      <div className="transaction-details-scroll">
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
        <section className="transaction-details-section" aria-label={isLatestVersion ? 'Tags' : 'Current tags'}>
          {isEditing ? <TagSelection choices={tagChoices} selected={tagDraft} onChange={setTagDraft} allowCreate disabled={isSaving} />
            : <><h3>{isLatestVersion ? 'Tags' : 'Current tags'}</h3><div className="tag-chips">
              {tags.map(tag => <span className="tag-chip" key={tag.id}>{tag.name}</span>)}
            </div></>}
        </section>
        {isLoading && <p className="transaction-history-loading" role="status">Loading categories, tags and version history…</p>}
        {error && <p className="notice notice-error transaction-details-error" role="alert">{error}</p>}
      </div>
      {isEditing && (
        <div className="transaction-details-actions">
          <button className="button button-secondary" type="button" onClick={cancelEditing} disabled={isSaving}>Cancel</button>
          <button
            className="button button-primary"
            type="button"
            onClick={() => void saveChanges()}
            disabled={isLoading || isSaving || (isCreatingCategory && !newCategory.trim()) ||
              (!isCreatingCategory && category === (displayedTransaction.category ?? '') &&
                tagDraft.length === tags.length && tagDraft.every(tag => tag.id !== undefined && tags.some(saved => saved.id === tag.id)))}
          >
            {isSaving ? 'Saving…' : 'Save changes'}
          </button>
        </div>
      )}
    </div>
  )
}

function formatTransactionAmount(amount: number | string | null) {
  if (amount === null || amount === '') return '—'
  const value = Number(amount)
  if (!Number.isFinite(value)) return `${amount}`
  return new Intl.NumberFormat('en-US', { maximumFractionDigits: 4 }).format(value)
}
