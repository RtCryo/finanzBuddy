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

export type TransactionHistoryPage = {
  content: TransactionRecord[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  categories: string[]
  hasUncategorized: boolean
}

export type TransactionHistoryParams = {
  page?: number
  size?: number
  query?: string
  from?: string
  to?: string
  category?: string
}
