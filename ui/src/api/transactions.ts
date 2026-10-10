import { api } from './client'
import type { TransactionHistoryPage, TransactionHistoryParams } from '../types/transaction'

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
    },
  })
  return response.data
}
