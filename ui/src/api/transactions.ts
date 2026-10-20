import { api } from './client'
import type {
  PersonalCategoryCreateResponse,
  TransactionHistoryPage,
  TransactionHistoryParams,
  TransactionRecord,
  TransactionVersion,
} from '../types/transaction'

export const UNCATEGORIZED_CATEGORY = '__uncategorized__'

export async function getTransactionHistory(params: TransactionHistoryParams = {}) {
  const response = await api.get<TransactionHistoryPage>('/transactions', {
    params: {
      page: params.page ?? 0,
      size: params.size ?? 20,
      ...(params.query ? { query: params.query } : {}),
      ...(params.from ? { from: params.from } : {}),
      ...(params.to ? { to: params.to } : {}),
      ...(params.category ? { category: params.category } : {}),
      ...(params.sortField ? { sortField: params.sortField } : {}),
      ...(params.sortDirection ? { sortDirection: params.sortDirection } : {}),
    },
  })
  return response.data
}

export async function getTransactionCategories() {
  const response = await api.get<string[]>('/transactions/categories')
  return response.data
}

export async function createPersonalTransactionCategory(transactionId: number, name: string) {
  const response = await api.post<PersonalCategoryCreateResponse>('/transactions/categories', {
    transactionId,
    name,
  })
  return response.data
}

export async function getTransactionVersions(transactionId: number) {
  const response = await api.get<TransactionVersion[]>(`/transactions/${encodeURIComponent(transactionId)}/versions`)
  return response.data
}

export async function updateTransactionCategory(transactionId: number, category: string) {
  const response = await api.put<TransactionRecord>(
    `/transactions/${encodeURIComponent(transactionId)}/category`,
    { category },
  )
  return response.data
}
