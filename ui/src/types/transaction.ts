export type TransactionRecord = {
  id: number
  bookingDate: string | null
  valueDate: string | null
  amount: number | string | null
  currency: string | null
  counterparty: string | null
  description: string | null
  category: string | null
}

export type TransactionVersion = {
  versionNumber: number
  transaction: TransactionRecord
  createdAt: string | null
}

export type PersonalCategoryCreateResponse = {
  name: string
  transaction: TransactionRecord
}

export type TransactionHistoryPage = {
  content: TransactionRecord[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  categories: string[]
  hasUncategorized: boolean
}

export type TransactionHistorySortField =
  | 'valueDate'
  | 'amount'
  | 'currency'
  | 'counterparty'
  | 'description'
  | 'category'

export type TransactionHistorySortDirection = 'ASC' | 'DESC'

export type TransactionHistoryParams = {
  tagIds?: number[]
  tagMode?: 'AND' | 'OR'
  page?: number
  size?: number
  query?: string
  from?: string
  to?: string
  category?: string
  sortField?: TransactionHistorySortField
  sortDirection?: TransactionHistorySortDirection
}

export type TransactionTag = { id: number; name: string }
export type TagDraft = { id: number; name: string } | { name: string; id?: undefined }
export type TransactionEdit = {
  category?: string
  newCategoryName?: string
  tags: ({ id: number } | { name: string })[]
}
