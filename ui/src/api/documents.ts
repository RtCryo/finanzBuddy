import { api } from './client'
import type { DocumentRecord } from '../types/document'
import type { TransactionRecord } from '../types/transaction'

type PageResponse<T> = {
  content: T[]
  totalElements: number
}

export async function getDocuments() {
  const response = await api.get<PageResponse<DocumentRecord>>('/documents', {
    params: { page: 0, size: 100, sort: 'createdAt,desc' },
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
