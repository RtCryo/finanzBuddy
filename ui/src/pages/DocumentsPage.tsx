import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { getDocuments } from '../api/documents'
import { getErrorMessage } from '../api/errors'
import { AppShell } from '../components/AppShell'
import { DocumentStatusBadge } from '../components/DocumentStatusBadge'
import { formatInstant } from '../format'
import type { DocumentRecord, DocumentStatus } from '../types/document'

const statusLabels: Record<DocumentStatus, string> = {
  PENDING: 'Pending',
  PROCESSING: 'Processing',
  PROCESSED: 'Processed',
  FAILED: 'Failed',
}

export function DocumentsPage() {
  const [documents, setDocuments] = useState<DocumentRecord[]>([])
  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const [appliedSearch, setAppliedSearch] = useState('')
  const [totalElements, setTotalElements] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalDocuments, setTotalDocuments] = useState(0)
  const [processedCount, setProcessedCount] = useState(0)
  const [processingCount, setProcessingCount] = useState(0)
  const [error, setError] = useState('')
  const [isLoading, setIsLoading] = useState(true)
  const [isRefreshing, setIsRefreshing] = useState(false)
  const requestId = useRef(0)
  const searchTimer = useRef<number | null>(null)

  const refresh = useCallback(async (showLoading = false) => {
    const current = ++requestId.current
    if (showLoading) setIsRefreshing(true)
    try {
      const result = await getDocuments(page, appliedSearch)
      if (current !== requestId.current) return
      setDocuments(result.content)
      setTotalElements(result.totalElements)
      setTotalPages(result.totalPages)
      setTotalDocuments(result.totalDocuments)
      setProcessedCount(result.processedCount)
      setProcessingCount(result.inProgressCount)
      setError('')
    } catch (requestError) {
      if (current !== requestId.current) return
      setError(getErrorMessage(requestError, 'We could not load your documents.'))
    } finally {
      if (current === requestId.current) {
        setIsLoading(false)
        setIsRefreshing(false)
      }
    }
  }, [appliedSearch, page])

  useEffect(() => {
    const initialLoad = window.setTimeout(() => void refresh(), 0)
    const interval = window.setInterval(() => void refresh(), 5000)
    return () => {
      window.clearTimeout(initialLoad)
      window.clearInterval(interval)
    }
  }, [refresh])

  useEffect(() => () => {
    if (searchTimer.current) window.clearTimeout(searchTimer.current)
  }, [])

  function updateSearch(value: string) {
    setSearch(value)
    if (searchTimer.current) window.clearTimeout(searchTimer.current)
    searchTimer.current = window.setTimeout(() => {
      setAppliedSearch(value.trim())
      setPage(0)
    }, 300)
  }

  return (
    <AppShell>
      <section className="page-heading">
        <div>
          <h1>Your documents</h1>
          <p>Upload statements and track processing, all in one place.</p>
        </div>
        <Link className="button button-primary" to="/documents/upload"><span aria-hidden="true">＋</span> Upload document</Link>
      </section>

      <section className="summary-grid" aria-label="Document summary">
        <div className="surface summary-card"><span>Total documents</span><strong>{totalDocuments}</strong><span className="summary-icon summary-icon-green" aria-hidden="true">▤</span></div>
        <div className="surface summary-card"><span>Processed</span><strong>{processedCount}</strong><span className="summary-icon summary-icon-green" aria-hidden="true">✓</span></div>
        <div className="surface summary-card"><span>In progress</span><strong>{processingCount}</strong><span className="summary-icon summary-icon-amber" aria-hidden="true">◷</span></div>
      </section>

      <section className="surface documents-card" aria-labelledby="documents-heading">
        <div className="documents-toolbar">
          <div>
            <h2 id="documents-heading">All documents</h2>
            <p>{totalElements} {totalElements === 1 ? 'file' : 'files'}</p>
          </div>
          <div className="document-actions">
            <label className="search-field">
              <svg viewBox="0 0 20 20" fill="none" aria-hidden="true"><circle cx="8.8" cy="8.8" r="5.8" stroke="currentColor" strokeWidth="1.5" /><path d="m13.2 13.2 4 4" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" /></svg>
              <span className="visually-hidden">Search documents</span>
              <input value={search} onChange={(event) => updateSearch(event.target.value)} placeholder="Search documents" />
            </label>
            <button className="button button-secondary refresh-button" type="button" onClick={() => void refresh(true)} disabled={isRefreshing}>
              <svg viewBox="0 0 20 20" fill="none" aria-hidden="true"><path d="M16.5 8A6.7 6.7 0 0 0 4.8 5.3L3.5 6.7M3.5 6.7V3.8M3.5 6.7h2.9M3.5 12a6.7 6.7 0 0 0 11.7 2.7l1.3-1.4m0 0v2.9m0-2.9h-2.9" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" /></svg>
              {isRefreshing ? 'Refreshing' : 'Refresh'}
            </button>
          </div>
        </div>

        {error && <div className="notice notice-error list-notice" role="alert">{error}</div>}
        {isLoading ? (
          <div className="list-state" role="status">Loading your documents…</div>
        ) : documents.length === 0 ? (
          <div className="list-state empty-list">
            <span className="empty-icon" aria-hidden="true">▤</span>
            <strong>{appliedSearch ? 'No matching documents' : 'No documents yet'}</strong>
            <span>{appliedSearch ? 'Try a different filename.' : 'Upload your first financial document to see it here.'}</span>
            {!appliedSearch && <Link className="button button-primary" to="/documents/upload">Upload document</Link>}
          </div>
        ) : (
          <div className="document-table-wrap">
            <div role="table" aria-label="Your uploaded documents">
              <div className="document-table-header" role="row">
                <span role="columnheader">Name</span>
                <span role="columnheader">Type</span>
                <span role="columnheader">Date uploaded</span>
                <span role="columnheader">Status</span>
              </div>
              {documents.map((document) => (
                <Link
                  className="document-row"
                  key={document.id}
                  to={`/documents/${document.id}`}
                  role="row"
                  aria-label={`Open ${document.originalFilename}, ${statusLabels[document.status]}`}
                >
                  <span className="document-name" role="cell">
                    <span className="document-file-icon" aria-hidden="true">▤</span>
                    <span><strong>{document.originalFilename}</strong><small>Document ID {document.id}</small></span>
                  </span>
                  <span role="cell"><span className="type-chip">{document.type}</span></span>
                  <span className="document-date" role="cell">{formatInstant(document.createdAt)}</span>
                  <span role="cell"><DocumentStatusBadge status={document.status} /></span>
                </Link>
              ))}
            </div>
          </div>
        )}
        {totalPages > 1 && (
          <div className="pager">
            <button className="button button-secondary" type="button" onClick={() => setPage((current) => current - 1)} disabled={page === 0}>
              Previous
            </button>
            <span>Page {page + 1} of {totalPages}</span>
            <button className="button button-secondary" type="button" onClick={() => setPage((current) => current + 1)} disabled={page + 1 >= totalPages}>
              Next
            </button>
          </div>
        )}
      </section>
      <p className="polling-note" aria-live="polite">Document statuses refresh automatically every 5 seconds.</p>
    </AppShell>
  )
}
