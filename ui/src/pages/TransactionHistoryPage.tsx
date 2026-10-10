import { useCallback, useEffect, useRef, useState } from 'react'
import { getTransactionHistory, UNCATEGORIZED_CATEGORY } from '../api/transactions'
import { getErrorMessage } from '../api/errors'
import { AppShell } from '../components/AppShell'
import { formatDateOnly } from '../format'
import type { TransactionHistoryPage as TransactionHistoryResult, TransactionRecord } from '../types/transaction'

const PAGE_SIZE = 20

type PaginationItem = number | 'left-ellipsis' | 'right-ellipsis'

export function TransactionHistoryPage() {
  const [transactions, setTransactions] = useState<TransactionRecord[]>([])
  const [categories, setCategories] = useState<string[]>([])
  const [hasUncategorized, setHasUncategorized] = useState(false)
  const [totalElements, setTotalElements] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [page, setPage] = useState(0)
  const [query, setQuery] = useState('')
  const [appliedQuery, setAppliedQuery] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [category, setCategory] = useState('')
  const [error, setError] = useState('')
  const [isLoading, setIsLoading] = useState(true)
  const requestId = useRef(0)

  const loadTransactions = useCallback(async () => {
    const currentRequest = ++requestId.current
    setIsLoading(true)
    try {
      const result: TransactionHistoryResult = await getTransactionHistory({
        page,
        size: PAGE_SIZE,
        ...(appliedQuery ? { query: appliedQuery } : {}),
        ...(from ? { from } : {}),
        ...(to ? { to } : {}),
        ...(category ? { category } : {}),
      })
      if (currentRequest !== requestId.current) return
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
  }, [appliedQuery, category, from, page, to])

  useEffect(() => {
    void loadTransactions()
  }, [loadTransactions])

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setAppliedQuery(query.trim())
      setPage(0)
    }, 300)
    return () => window.clearTimeout(timer)
  }, [query])

  function clearFilters() {
    setQuery('')
    setAppliedQuery('')
    setFrom('')
    setTo('')
    setCategory('')
    setPage(0)
  }

  const hasFilters = Boolean(query || from || to || category)
  const firstResult = totalElements === 0 ? 0 : page * PAGE_SIZE + 1
  const lastResult = Math.min((page + 1) * PAGE_SIZE, totalElements)
  const paginationItems = getPaginationItems(totalPages, page)

  return (
    <AppShell>
      <section className="page-heading transaction-history-heading">
        <div>
          <h1>Transaction history</h1>
          <p>Review transactions from all of your processed statements.</p>
        </div>
      </section>

      <section className="surface transactions-card history-card" aria-labelledby="history-transactions-heading">
        <div className="documents-toolbar history-toolbar">
          <div>
            <h2 id="history-transactions-heading">All transactions</h2>
            <p>{totalElements} {totalElements === 1 ? 'transaction' : 'transactions'}</p>
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
              <input aria-label="Booking date from" type="date" value={from} onChange={(event) => { setFrom(event.target.value); setPage(0) }} />
            </label>
            <label className="history-date-field">
              <span>To</span>
              <input aria-label="Booking date to" type="date" value={to} onChange={(event) => { setTo(event.target.value); setPage(0) }} />
            </label>
            <label className="history-category-field">
              <span className="visually-hidden">Filter by category</span>
              <select aria-label="Filter by category" value={category} onChange={(event) => { setCategory(event.target.value); setPage(0) }}>
                <option value="">All categories</option>
                {categories.map((value) => <option key={value} value={value}>{value}</option>)}
                {hasUncategorized && <option value={UNCATEGORIZED_CATEGORY}>Uncategorized</option>}
              </select>
            </label>
            <button className="button button-secondary clear-filters-button" type="button" onClick={clearFilters} disabled={!hasFilters}>
              Clear filters
            </button>
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
                <span role="columnheader">Booking date</span>
                <span role="columnheader">Value date</span>
                <span role="columnheader">Amount</span>
                <span role="columnheader">Currency</span>
                <span role="columnheader">Counterparty</span>
                <span role="columnheader">Description</span>
                <span role="columnheader">Category</span>
              </div>
              {transactions.map((transaction) => <HistoryRow key={transaction.id} transaction={transaction} />)}
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
                onClick={() => setPage((current) => Math.max(0, current - 1))}
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
                  onClick={() => setPage(item)}
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
                onClick={() => setPage((current) => Math.min(totalPages - 1, current + 1))}
                disabled={page >= totalPages - 1}
              >
                <svg viewBox="0 0 20 20" aria-hidden="true"><path d="m8 4 6 6-6 6" /></svg>
              </button>
            </div>
          </nav>
        )}
      </section>
    </AppShell>
  )
}

function HistoryRow({ transaction }: { transaction: TransactionRecord }) {
  const category = transaction.category?.trim() || 'Uncategorized'

  return (
    <div className="transaction-row" role="row">
      <span role="cell">{formatDateOnly(transaction.bookingDate)}</span>
      <span role="cell">{formatDateOnly(transaction.valueDate)}</span>
      <span className="transaction-amount" role="cell">{formatAmount(transaction.amount)}</span>
      <span role="cell">{transaction.currency || '—'}</span>
      <span role="cell">{transaction.counterparty || '—'}</span>
      <span role="cell">{transaction.description || '—'}</span>
      <span role="cell"><span className="category-chip">{category}</span></span>
    </div>
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
