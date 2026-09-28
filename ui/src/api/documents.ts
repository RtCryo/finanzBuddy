import { api } from './client'
import type { DocumentRecord } from '../types/document'
import type { TransactionRecord } from '../types/transaction'

type PageResponse<T> = {
  content: T[]
  totalElements: number
}

const pageSize = 100

async function fetchDocumentPage(page: number) {
  const response = await api.get<PageResponse<DocumentRecord>>('/documents', {
    params: { page, size: pageSize, sort: 'createdAt,desc' },
  })
  return response.data
}

export async function getDocuments() {
  const first = await fetchDocumentPage(0)
  if (first.content.length >= first.totalElements) return first

  const remainingPages = Math.ceil(first.totalElements / pageSize) - 1
  const rest = await Promise.all(
    Array.from({ length: remainingPages }, (_, index) => fetchDocumentPage(index + 1)),
  )
  return {
    content: [...first.content, ...rest.flatMap((page) => page.content)],
    totalElements: first.totalElements,
  }
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
