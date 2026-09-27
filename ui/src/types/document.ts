export type DocumentStatus = 'PENDING' | 'PROCESSING' | 'PROCESSED' | 'FAILED'

export type DocumentRecord = {
  id: number
  originalFilename: string
  type: string
  status: DocumentStatus
  createdAt: string
  errorMessage?: string | null
}
