import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getDocuments } from '../api/documents'
import { getErrorMessage } from '../api/errors'
import { AppShell } from '../components/AppShell'
import { DocumentStatusBadge } from '../components/DocumentStatusBadge'
import type { DocumentRecord, DocumentStatus } from '../types/document'

const statusLabels: Record<DocumentStatus, string> = {
  PENDING: 'Pending',
  PROCESSING: 'Processing',
  PROCESSED: 'Processed',
  FAILED: 'Failed',
}

function formatDate(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.valueOf())
    ? '—'
    : new Intl.DateTimeFormat('en-US', { month: 'short', day: 'numeric', year: 'numeric' }).format(date)
}

export function DocumentsPage() {
  const [documents, setDocuments] = useState<DocumentRecord[]>([])
  const [search, setSearch] = useState('')
  const [error, setError] = useState('')
  const [isLoading, setIsLoading] = useState(true)
  const [isRefreshing, setIsRefreshing] = useState(false)

  const refresh = useCallback(async (showLoading = false) => {
    if (showLoading) {
      setIsRefreshing(true)
      setError('')
    }
    try {
      const page = await getDocuments()
      setDocuments(page.content)
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'We could not load your documents.'))
    } finally {
      setIsLoading(false)
      setIsRefreshing(false)
    }
  }, [])

  useEffect(() => {
    const initialLoad = window.setTimeout(() => void refresh(), 0)
    const interval = window.setInterval(() => void refresh(), 5000)
    return () => {
      window.clearTimeout(initialLoad)
      window.clearInterval(interval)
    }
  }, [refresh])

  const filteredDocuments = documents.filter((document) =>
    document.originalFilename.toLowerCase().includes(search.trim().toLowerCase()),
  )
  const processedCount = documents.filter((document) => document.status === 'PROCESSED').length
  const processingCount = documents.filter((document) => ['PENDING', 'PROCESSING'].includes(document.status)).length

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
        <div className="surface summary-card"><span>Total documents</span><strong>{documents.length}</strong><span className="summary-icon summary-icon-green" aria-hidden="true">▤</span></div>
        <div className="surface summary-card"><span>Processed</span><strong>{processedCount}</strong><span className="summary-icon summary-icon-green" aria-hidden="true">✓</span></div>
        <div className="surface summary-card"><span>In progress</span><strong>{processingCount}</strong><span className="summary-icon summary-icon-amber" aria-hidden="true">◷</span></div>
      </section>

      <section className="surface documents-card" aria-labelledby="documents-heading">
        <div className="documents-toolbar">
          <div>
            <h2 id="documents-heading">All documents</h2>
            <p>{documents.length} {documents.length === 1 ? 'file' : 'files'}</p>
          </div>
          <div className="document-actions">
            <label className="search-field">
              <svg viewBox="0 0 20 20" fill="none" aria-hidden="true"><circle cx="8.8" cy="8.8" r="5.8" stroke="currentColor" strokeWidth="1.5" /><path d="m13.2 13.2 4 4" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" /></svg>
              <span className="visually-hidden">Search documents</span>
              <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search documents" />
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
        ) : filteredDocuments.length === 0 ? (
          <div className="list-state empty-list">
            <span className="empty-icon" aria-hidden="true">▤</span>
            <strong>{search ? 'No matching documents' : 'No documents yet'}</strong>
            <span>{search ? 'Try a different filename.' : 'Upload your first financial document to see it here.'}</span>
            {!search && <Link className="button button-primary" to="/documents/upload">Upload document</Link>}
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
              {filteredDocuments.map((document) => (
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
                  <span className="document-date" role="cell">{formatDate(document.createdAt)}</span>
                  <span role="cell"><DocumentStatusBadge status={document.status} /></span>
                </Link>
              ))}
            </div>
          </div>
        )}
      </section>
      <p className="polling-note" aria-live="polite">Document statuses refresh automatically every 5 seconds.</p>
    </AppShell>
  )
}
