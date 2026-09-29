import { api } from './client'
import type { DocumentRecord } from '../types/document'
import type { TransactionRecord } from '../types/transaction'

export type DocumentPage = {
  content: DocumentRecord[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  totalDocuments: number
  processedCount: number
  inProgressCount: number
}

export async function getDocuments(page = 0, filename = '') {
  const response = await api.get<DocumentPage>('/documents', {
    params: {
      page,
      size: 20,
      sort: 'createdAt,desc',
      ...(filename ? { filename } : {}),
    },
  })
  return response.data
}

export async function getDocument(id: string) {
  const response = await api.get<DocumentRecord>(`/documents/${encodeURIComponent(id)}`)
  return response.data
}

export async function getDocumentTransactions(id: string) {
  const response = await api.get<TransactionRecord[]>(
    `/documents/${encodeURIComponent(id)}/transactions`,
  )
  return response.data
}
