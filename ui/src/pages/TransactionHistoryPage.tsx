import { useCallback, useEffect, useRef, useState } from 'react'
import { getTransactionHistory, getTransactionTags, UNCATEGORIZED_CATEGORY } from '../api/transactions'
import { HistoryTagFilter } from '../components/HistoryTagFilter'
import type { TransactionTag } from '../types/transaction'
import { getErrorMessage } from '../api/errors'
import { AppShell } from '../components/AppShell'
import { PageHeading } from '../components/PageHeading'
import { TransactionDetailsDialog } from '../components/TransactionDetailsDialog'
import { formatDateOnly, formatTransactionCategory, formatTransactionText, normalizeTransactionText, transactionAmountTone } from '../format'
import type {
  TransactionHistoryPage as TransactionHistoryResult,
  TransactionHistorySortDirection,
  TransactionHistorySortField,
  TransactionRecord,
} from '../types/transaction'

const PAGE_SIZE = 20

type PaginationItem = number | 'left-ellipsis' | 'right-ellipsis'

export function TransactionHistoryPage() {
  const [transactions, setTransactions] = useState<TransactionRecord[]>([])
  const [categories, setCategories] = useState<string[]>([])
  const [tagChoices, setTagChoices] = useState<TransactionTag[]>([])
  const [selectedTags, setSelectedTags] = useState<TransactionTag[]>([])
  const [tagMode, setTagMode] = useState<'AND' | 'OR'>('AND')
  const [tagError, setTagError] = useState('')
  const [hasUncategorized, setHasUncategorized] = useState(false)
  const [totalElements, setTotalElements] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [page, setPage] = useState(0)
  const [query, setQuery] = useState('')
  const [appliedQuery, setAppliedQuery] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [category, setCategory] = useState('')
  const [sortField, setSortField] = useState<TransactionHistorySortField>('valueDate')
  const [sortDirection, setSortDirection] = useState<TransactionHistorySortDirection>('DESC')
  const [error, setError] = useState('')
  const [isLoading, setIsLoading] = useState(true)
  const [selectedTransaction, setSelectedTransaction] = useState<TransactionRecord | null>(null)
  const transactionOpenerRef = useRef<HTMLElement | null>(null)
  const requestId = useRef(0)

  const loadTransactions = useCallback(async () => {
    const currentRequest = ++requestId.current
    try {
      const result: TransactionHistoryResult = await getTransactionHistory({
        page,
        size: PAGE_SIZE,
        ...(appliedQuery ? { query: appliedQuery } : {}),
        ...(from ? { from } : {}),
        ...(to ? { to } : {}),
        ...(category ? { category } : {}),
        ...(selectedTags.length ? { tagIds: selectedTags.map(tag => tag.id) } : {}),
        tagMode,
        sortField,
        sortDirection,
      })
      if (currentRequest !== requestId.current) return
      if (page > 0 && page >= result.totalPages) {
        setPage(Math.max(0, result.totalPages - 1))
        return
      }
      setTransactions(result.content)
      setTotalElements(result.totalElements)
      setTotalPages(result.totalPages)
      setCategories(result.categories)
      setHasUncategorized(result.hasUncategorized)
      setError('')
    } catch (requestError) {
      if (currentRequest !== requestId.current) return
      setError(getErrorMessage(requestError, 'We could not load your transaction history.'))
    } finally {
      if (currentRequest === requestId.current) setIsLoading(false)
    }
  }, [appliedQuery, category, from, page, sortDirection, sortField, to, selectedTags, tagMode])

  const loadTags = useCallback(async () => {
    try { setTagChoices(await getTransactionTags()); setTagError('') }
    catch (error) { setTagError(getErrorMessage(error, 'We could not load tag choices.')) }
  }, [])

  useEffect(() => {
    const initialLoad = window.setTimeout(() => void loadTags(), 0)
    return () => window.clearTimeout(initialLoad)
  }, [loadTags])

  useEffect(() => {
    const initialLoad = window.setTimeout(() => void loadTransactions(), 0)
    return () => window.clearTimeout(initialLoad)
  }, [loadTransactions])

  useEffect(() => {
    const timer = window.setTimeout(() => {
      const nextQuery = query.trim()
      if (nextQuery !== appliedQuery) {
        setIsLoading(true)
        setAppliedQuery(nextQuery)
        setPage(0)
      }
    }, 300)
    return () => window.clearTimeout(timer)
  }, [appliedQuery, query])

  function clearFilters() {
    setIsLoading(true)
    setQuery('')
    setAppliedQuery('')
    setFrom('')
    setTo('')
    setCategory('')
    setSelectedTags([])
    setTagMode('AND')
    setPage(0)
  }

  function changeSort(field: TransactionHistorySortField) {
    setIsLoading(true)
    if (field === sortField) {
      setSortDirection((current) => current === 'ASC' ? 'DESC' : 'ASC')
    } else {
      setSortField(field)
      setSortDirection('ASC')
    }
    setPage(0)
  }

  function changePage(nextPage: number) {
    if (nextPage === page) return
    setIsLoading(true)
    setPage(nextPage)
  }

  function changeFrom(value: string) {
    if (value === from) return
    setIsLoading(true)
    setFrom(value)
    setPage(0)
  }

  function changeTo(value: string) {
    if (value === to) return
    setIsLoading(true)
    setTo(value)
    setPage(0)
  }

  function changeCategory(value: string) {
    if (value === category) return
    setIsLoading(true)
    setCategory(value)
    setPage(0)
  }

  function handleTransactionSaved(updated: TransactionRecord) {
    setSelectedTransaction(updated)
    setTransactions((current) => current.map((transaction) => transaction.id === updated.id ? updated : transaction))
    // Refresh the current page and category choices without resetting pagination or filters.
    void loadTransactions()
    void loadTags()
  }

  const hasFilters = Boolean(query || from || to || category || selectedTags.length || tagMode !== 'AND')
  const firstResult = totalElements === 0 ? 0 : page * PAGE_SIZE + 1
  const lastResult = Math.min((page + 1) * PAGE_SIZE, totalElements)
  const paginationItems = getPaginationItems(totalPages, page)

  return (
    <AppShell>
      <PageHeading
        className="transaction-history-heading"
        title="Transaction history"
        description="Review transactions from all of your processed statements."
      />

      <section className="surface transactions-card history-card" aria-labelledby="history-transactions-heading">
        <div className="documents-toolbar history-toolbar">
          <div className="history-title-row">
            <div>
              <h2 id="history-transactions-heading">All transactions</h2>
              <p>{totalElements} {totalElements === 1 ? 'transaction' : 'transactions'}</p>
            </div>
            <button className="button button-secondary clear-filters-button" type="button" onClick={clearFilters} disabled={!hasFilters}>
              Clear filters
            </button>
          </div>
          <div className="history-filters" aria-label="Transaction filters">
            <label className="search-field history-search-field">
              <svg viewBox="0 0 20 20" fill="none" aria-hidden="true"><circle cx="8.8" cy="8.8" r="5.8" stroke="currentColor" strokeWidth="1.5" /><path d="m13.2 13.2 4 4" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" /></svg>
              <span className="visually-hidden">Search description or counterparty</span>
              <input
                aria-label="Search description or counterparty"
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder="Search transactions"
              />
            </label>
            <label className="history-date-field">
              <span>From</span>
              <input aria-label="Value date from" type="date" value={from} onChange={(event) => changeFrom(event.target.value)} />
            </label>
            <label className="history-date-field">
              <span>To</span>
              <input aria-label="Value date to" type="date" value={to} onChange={(event) => changeTo(event.target.value)} />
            </label>
            <label className="history-category-field">
              <span className="visually-hidden">Filter by category</span>
              <select aria-label="Filter by category" value={category} onChange={(event) => changeCategory(event.target.value)}>
                <option value="">All categories</option>
                {categories.map((value) => <option key={value} value={value}>{value}</option>)}
                {hasUncategorized && <option value={UNCATEGORIZED_CATEGORY}>Uncategorized</option>}
              </select>
              <svg className="history-select-chevron" viewBox="0 0 20 20" aria-hidden="true"><path d="m5 7 5 5 5-5" /></svg>
            </label>
            <HistoryTagFilter choices={tagChoices} selected={selectedTags} mode={tagMode} error={tagError}
              onRetry={() => void loadTags()}
              onSelectionChange={tags => {
                setSelectedTags(tags)
                setPage(0)
              }}
              onModeChange={mode => { setTagMode(mode); setPage(0) }} />
          </div>
        </div>

        {error && <div className="notice notice-error history-notice" role="alert">{error}</div>}
        {isLoading ? (
          <div className="list-state" role="status">Loading transactions…</div>
        ) : error ? null : totalElements === 0 ? (
          <div className="list-state empty-list" role="status">
            <span className="empty-icon" aria-hidden="true">↔</span>
            <strong>{hasFilters ? 'No matching transactions' : 'No transactions available'}</strong>
            <span>{hasFilters ? 'Try changing or clearing your filters.' : 'Transactions from processed statements will appear here.'}</span>
            {hasFilters && <button className="button button-secondary" type="button" onClick={clearFilters}>Clear filters</button>}
          </div>
        ) : (
          <div className="transaction-table-wrap">
            <div className="transaction-table history-transaction-table" role="table" aria-label="Transaction history">
              <div className="transaction-row transaction-header" role="row">
                <SortHeader field="valueDate" label="Value date" activeField={sortField} direction={sortDirection} onSort={changeSort} />
                <SortHeader field="amount" label="Amount" activeField={sortField} direction={sortDirection} onSort={changeSort} />
                <SortHeader field="currency" label="Currency" activeField={sortField} direction={sortDirection} onSort={changeSort} />
                <SortHeader field="counterparty" label="Counterparty" activeField={sortField} direction={sortDirection} onSort={changeSort} />
                <SortHeader field="description" label="Description" activeField={sortField} direction={sortDirection} onSort={changeSort} />
                <SortHeader field="category" label="Category" activeField={sortField} direction={sortDirection} onSort={changeSort} />
              </div>
              {transactions.map((transaction) => (
                <HistoryRow
                  key={transaction.id}
                  transaction={transaction}
                  isSelected={selectedTransaction?.id === transaction.id}
                  onSelect={(selected, opener) => {
                    transactionOpenerRef.current = opener
                    setSelectedTransaction(selected)
                  }}
                />
              ))}
            </div>
          </div>
        )}

        {!isLoading && !error && totalPages > 0 && (
          <nav className="history-pagination" aria-label="Transaction history pages">
            <span className="history-result-range">Showing {firstResult}–{lastResult} of {totalElements} transactions</span>
            <div className="history-page-controls">
              <button
                className="page-arrow"
                type="button"
                aria-label="Previous page"
                onClick={() => changePage(Math.max(0, page - 1))}
                disabled={page === 0}
              >
                <svg viewBox="0 0 20 20" aria-hidden="true"><path d="m12 4-6 6 6 6" /></svg>
              </button>
              {paginationItems.map((item) => typeof item === 'number' ? (
                <button
                  className={`page-number${item === page ? ' current' : ''}`}
                  type="button"
                  key={item}
                  aria-label={`Page ${item + 1}`}
                  aria-current={item === page ? 'page' : undefined}
                  onClick={() => changePage(item)}
                >
                  {item + 1}
                </button>
              ) : (
                <span className="page-ellipsis" key={item} aria-hidden="true">…</span>
              ))}
              <button
                className="page-arrow"
                type="button"
                aria-label="Next page"
                onClick={() => changePage(Math.min(totalPages - 1, page + 1))}
                disabled={page >= totalPages - 1}
              >
                <svg viewBox="0 0 20 20" aria-hidden="true"><path d="m8 4 6 6-6 6" /></svg>
              </button>
            </div>
          </nav>
        )}
      </section>
      <TransactionDetailsDialog
        transaction={selectedTransaction}
        openerRef={transactionOpenerRef}
        onClose={() => setSelectedTransaction(null)}
        onSaved={handleTransactionSaved}
      />
    </AppShell>
  )
}

function HistoryRow({
  transaction,
  isSelected,
  onSelect,
}: {
  transaction: TransactionRecord
  isSelected: boolean
  onSelect: (transaction: TransactionRecord, opener: HTMLElement) => void
}) {
  const category = formatTransactionCategory(transaction.category)
  const actionName = normalizeTransactionText(transaction.description) || normalizeTransactionText(transaction.counterparty) || 'transaction'

  return (
    <div
      className={`transaction-row${isSelected ? ' transaction-row-selected' : ''}`}
      role="row"
      onClick={(event) => {
        const opener = event.currentTarget.querySelector<HTMLButtonElement>('.transaction-row-action')
        if (opener) onSelect(transaction, opener)
      }}
    >
      <span className="transaction-action-cell" role="cell">
        <button
          className="transaction-row-action"
          type="button"
          aria-label={`View transaction details: ${actionName}`}
        />
        {formatDateOnly(transaction.valueDate)}
      </span>
      <span className={`transaction-amount ${transactionAmountTone(transaction.amount)}`} role="cell">{formatAmount(transaction.amount)}</span>
      <span role="cell">{transaction.currency || '—'}</span>
      <span role="cell">{formatTransactionText(transaction.counterparty)}</span>
      <span role="cell">{formatTransactionText(transaction.description)}</span>
      <span role="cell"><span className="category-chip">{category}</span></span>
    </div>
  )
}

function SortHeader({
  field,
  label,
  activeField,
  direction,
  onSort,
}: {
  field: TransactionHistorySortField
  label: string
  activeField: TransactionHistorySortField
  direction: TransactionHistorySortDirection
  onSort: (field: TransactionHistorySortField) => void
}) {
  const isActive = activeField === field
  const ariaSort = isActive ? direction === 'ASC' ? 'ascending' : 'descending' : 'none'

  return (
    <span role="columnheader" aria-sort={ariaSort}>
      <button className="history-sort-button" type="button" onClick={() => onSort(field)}>
        {label}<span aria-hidden="true">{isActive ? direction === 'ASC' ? ' ↑' : ' ↓' : ' ↕'}</span>
      </button>
    </span>
  )
}

function getPaginationItems(total: number, current: number): PaginationItem[] {
  if (total <= 7) return Array.from({ length: total }, (_, index) => index)
  if (current <= 2) return [0, 1, 2, 3, 4, 'right-ellipsis', total - 1]
  if (current >= total - 3) {
    return [0, 'left-ellipsis', total - 5, total - 4, total - 3, total - 2, total - 1]
  }
  return [0, 'left-ellipsis', current - 1, current, current + 1, 'right-ellipsis', total - 1]
}

function formatAmount(amount: number | string | null) {
  if (amount === null || amount === '') return '—'
  const value = Number(amount)
  if (!Number.isFinite(value)) return `${amount}`
  return new Intl.NumberFormat('en-US', { maximumFractionDigits: 4 }).format(value)
}
