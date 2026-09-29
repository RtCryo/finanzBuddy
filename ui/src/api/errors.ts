import axios from 'axios'

export function getErrorMessage(error: unknown, fallback: string) {
  if (axios.isAxiosError(error)) {
    const fromBody = readErrorBody(error.response?.data)
    if (fromBody) return fromBody
    if (error.message) return error.message
  }

  return fallback
}

function readErrorBody(data: unknown) {
  if (!data || typeof data !== 'object') return null
  const body = data as Record<string, unknown>
  const detail = text(body.detail)
  if (detail) return detail

  const message = text(body.message)
  if (message && message !== 'No message available') return unwrapStatusReason(message)

  if (Array.isArray(body.errors)) {
    for (const item of body.errors) {
      if (!item || typeof item !== 'object') continue
      const fieldMessage = text((item as { defaultMessage?: unknown }).defaultMessage)
      if (fieldMessage) return fieldMessage
    }
  }

  return null
}

function text(value: unknown) {
  return typeof value === 'string' && value.trim() ? value.trim() : null
}

function unwrapStatusReason(message: string) {
  const quoted = message.match(/"([^"]+)"\s*$/)
  return quoted?.[1] ?? message
}
