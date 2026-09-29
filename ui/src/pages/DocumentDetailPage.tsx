import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { getDocument, getDocumentTransactions } from '../api/documents'
import { getErrorMessage } from '../api/errors'
import { api } from '../api/client'
import { AppShell } from '../components/AppShell'
import { DocumentStatusBadge } from '../components/DocumentStatusBadge'
import { formatDateOnly, formatInstant } from '../format'
import type { DocumentRecord } from '../types/document'
import type { TransactionRecord } from '../types/transaction'

export function DocumentDetailPage() {
  const { id = '' } = useParams()
  const [document, setDocument] = useState<DocumentRecord | null>(null)
  const [transactions, setTransactions] = useState<TransactionRecord[]>([])
  const [error, setError] = useState('')
  const [transactionsError, setTransactionsError] = useState('')
  const [downloadError, setDownloadError] = useState('')
  const [loadedId, setLoadedId] = useState<string | null>(null)
  const [areTransactionsLoading, setAreTransactionsLoading] = useState(false)
  const [isDownloading, setIsDownloading] = useState(false)
  const [isRefreshing, setIsRefreshing] = useState(false)
  const requestId = useRef(0)
  const isLoading = loadedId !== id
  const isOpen = document?.status === 'PENDING' || document?.status === 'PROCESSING'

  const load = useCallback(async (manual = false) => {
    const current = ++requestId.current
    if (manual) setIsRefreshing(true)

    try {
      const result = await getDocument(id)
      if (current !== requestId.current) return
      setDocument(result)
      setError('')

      if (result.status !== 'PROCESSED') {
        setTransactions([])
        setTransactionsError('')
        setAreTransactionsLoading(false)
        return
      }

      setAreTransactionsLoading(true)
      try {
        const rows = await getDocumentTransactions(id)
        if (current !== requestId.current) return
        setTransactions(rows)
        setTransactionsError('')
      } catch (requestError) {
        if (current !== requestId.current) return
        setTransactionsError(getErrorMessage(requestError, 'We could not load this document’s transactions.'))
      } finally {
        if (current === requestId.current) setAreTransactionsLoading(false)
      }
    } catch (requestError) {
      if (current !== requestId.current) return
      setError(getErrorMessage(requestError, 'We could not load this document.'))
    } finally {
      if (current === requestId.current) {
        setLoadedId(id)
        setIsRefreshing(false)
      }
    }
  }, [id])

  useEffect(() => {
    const initialLoad = window.setTimeout(() => void load(), 0)
    return () => window.clearTimeout(initialLoad)
  }, [load])

  useEffect(() => {
    if (!isOpen) return
    const interval = window.setInterval(() => void load(), 5000)
    return () => window.clearInterval(interval)
  }, [isOpen, load])

  async function downloadOriginalFile() {
    if (!document) return
    setDownloadError('')
    setIsDownloading(true)

    try {
      const response = await api.get<Blob>(`/documents/${encodeURIComponent(id)}/download`, {
        responseType: 'blob',
        headers: {
          Accept: 'application/octet-stream, application/problem+json, application/json',
        },
      })
      const disposition = response.headers['content-disposition'] as string | undefined
      const encodedFilename = disposition?.match(/filename\*=UTF-8''([^;]+)/i)?.[1]
      const quotedFilename = disposition?.match(/filename="?([^";]+)"?/i)?.[1]
      const filename = encodedFilename
        ? decodeURIComponent(encodedFilename)
        : quotedFilename ?? document.originalFilename
      const objectUrl = URL.createObjectURL(response.data)
      const anchor = window.document.createElement('a')
      anchor.href = objectUrl
      anchor.download = filename
      window.document.body.appendChild(anchor)
      anchor.click()
      anchor.remove()
      window.setTimeout(() => URL.revokeObjectURL(objectUrl), 1000)
    } catch (requestError) {
      const responseData = (requestError as { response?: { data?: unknown } }).response?.data
      if (responseData instanceof Blob) {
        try {
          const payload = JSON.parse(await responseData.text()) as { detail?: string; message?: string }
          setDownloadError(payload.detail ?? payload.message ?? 'The original file could not be downloaded.')
        } catch {
          setDownloadError('The original file could not be downloaded.')
        }
      } else {
        setDownloadError(getErrorMessage(requestError, 'The original file could not be downloaded.'))
      }
    } finally {
      setIsDownloading(false)
    }
  }

  return (
    <AppShell>
      <Link className="back-link" to="/documents">← Back to documents</Link>
      {isLoading ? (
        <div className="detail-state" role="status">Loading document…</div>
      ) : error ? (
        <div className="notice notice-error detail-notice" role="alert">{error}</div>
      ) : document ? (
        <>
          <section className="detail-heading">
            <div className="detail-header-row">
              <div className="detail-title-row">
                <h1>{document.originalFilename}</h1>
                <DocumentStatusBadge status={document.status} />
              </div>
              <div className="detail-actions">
                <button className="button button-secondary" type="button" onClick={() => void load(true)} disabled={isRefreshing}>
                  {isRefreshing ? 'Refreshing' : 'Refresh'}
                </button>
                <button
                  className="download-button"
                  type="button"
                  aria-label={`Download original file ${document.originalFilename}`}
                  title="Download original file"
                  onClick={() => void downloadOriginalFile()}
                  disabled={isDownloading}
                >
                  <svg viewBox="0 0 20 20" fill="none" aria-hidden="true">
                    <path d="M10 3.5v9m-3.5-3.5L10 12.5l3.5-3.5M4.5 14.5v1A1.5 1.5 0 0 0 6 17h8a1.5 1.5 0 0 0 1.5-1.5v-1" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
                  </svg>
                </button>
              </div>
            </div>
            <p>Uploaded {formatInstant(document.createdAt)} · {document.type} · Document ID {document.id}</p>
          </section>
          {downloadError && <div className="notice notice-error detail-notice" role="alert">{downloadError}</div>}
          <section className="surface detail-information" aria-labelledby="file-information-heading">
            <h2 id="file-information-heading">File information</h2>
            <dl>
              <div><dt>File name</dt><dd>{document.originalFilename}</dd></div>
              <div><dt>Document type</dt><dd>{document.type}</dd></div>
              <div><dt>Uploaded</dt><dd>{formatInstant(document.createdAt)}</dd></div>
            </dl>
          </section>
          {document.status === 'PENDING' || document.status === 'PROCESSING' ? (
            <section className="surface processing-panel" aria-live="polite">
              <span className="processing-symbol" aria-hidden="true">◷</span>
              <h2>{document.status === 'PENDING' ? 'Your document is queued for processing' : 'Your document is being processed'}</h2>
              <p>We’re extracting transactions from this file. Status refreshes automatically every 5 seconds.</p>
            </section>
          ) : document.status === 'FAILED' ? (
            <section className="surface failed-panel" aria-labelledby="failed-title">
              <div className="failed-panel-heading">
                <span className="failed-symbol" aria-hidden="true">!</span>
                <div>
                  <h2 id="failed-title">We couldn’t process this document</h2>
                  <p>Check the file and try uploading it again.</p>
                </div>
              </div>
              <div className="error-details">
                <strong>Error details</strong>
                <p>{document.errorMessage || 'No additional error details were provided.'}</p>
              </div>
            </section>
          ) : (
            <section className="surface transactions-card" aria-labelledby="transactions-title">
              <div className="transactions-heading">
                <h2 id="transactions-title">Transactions</h2>
                <p>Normalized data extracted from this document</p>
              </div>
              {transactionsError && <div className="notice notice-error detail-notice" role="alert">{transactionsError}</div>}
              {areTransactionsLoading ? (
                <div className="detail-state" role="status">Loading transactions…</div>
              ) : transactions.length === 0 ? (
                <div className="transactions-empty" role="status">No transactions are available for this document yet.</div>
              ) : (
                <div className="transaction-table-wrap">
                  <div className="transaction-table" role="table" aria-label="Document transactions">
                    <div className="transaction-row transaction-header" role="row">
                      <span role="columnheader">Booking date</span>
                      <span role="columnheader">Value date</span>
                      <span role="columnheader">Amount</span>
                      <span role="columnheader">Currency</span>
                      <span role="columnheader">Counterparty</span>
                      <span role="columnheader">Description</span>
                      <span role="columnheader">Category</span>
                    </div>
                    {transactions.map((transaction) => (
                      <div className="transaction-row" role="row" key={transaction.id}>
                        <span role="cell">{formatDateOnly(transaction.bookingDate)}</span>
                        <span role="cell">{formatDateOnly(transaction.valueDate)}</span>
                        <span className="transaction-amount" role="cell">{formatAmount(transaction.amount)}</span>
                        <span role="cell">{transaction.currency || '—'}</span>
                        <span role="cell">{transaction.counterparty || '—'}</span>
                        <span role="cell">{transaction.description || '—'}</span>
                        <span role="cell"><span className="category-chip">{transaction.category || 'Uncategorized'}</span></span>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </section>
          )}
        </>
      ) : null}
    </AppShell>
  )
}

function formatAmount(amount: number | string | null) {
  if (amount === null || amount === '') return '—'
  const value = Number(amount)
  if (!Number.isFinite(value)) return `${amount}`
  return new Intl.NumberFormat('en-US', { maximumFractionDigits: 4 }).format(value)
}
