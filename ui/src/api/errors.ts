import axios from 'axios'

export function getErrorMessage(error: unknown, fallback: string) {
  if (axios.isAxiosError(error)) {
    const responseMessage = error.response?.data?.message
    if (typeof responseMessage === 'string' && responseMessage.trim()) return responseMessage

    const detail = error.response?.data?.detail
    if (typeof detail === 'string' && detail.trim()) return detail

    if (error.message) return error.message
  }

  return fallback
}
