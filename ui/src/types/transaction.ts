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
