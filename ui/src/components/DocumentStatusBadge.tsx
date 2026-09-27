import type { DocumentStatus } from '../types/document'

const statusLabels: Record<DocumentStatus, string> = {
  PENDING: 'Pending',
  PROCESSING: 'Processing',
  PROCESSED: 'Processed',
  FAILED: 'Failed',
}

export function DocumentStatusBadge({ status }: { status: DocumentStatus }) {
  return <span className={`status-badge status-${status.toLowerCase()}`}>{statusLabels[status] ?? status}</span>
}
