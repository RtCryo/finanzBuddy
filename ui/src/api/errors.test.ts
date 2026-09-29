import { AxiosError } from 'axios'
import { describe, expect, it } from 'vitest'
import { getErrorMessage } from './errors'

describe('getErrorMessage', () => {
  it('reads the problem-detail reason instead of the axios status text', () => {
    const error = new AxiosError('Request failed with status code 409')
    error.response = {
      data: { detail: 'Email already registered', status: 409 },
      status: 409,
      statusText: 'Conflict',
      headers: {},
      config: { headers: {} } as never,
    }

    expect(getErrorMessage(error, 'fallback')).toBe('Email already registered')
  })

  it('unwraps a quoted Spring status reason', () => {
    const error = new AxiosError('Request failed with status code 401')
    error.response = {
      data: { message: '401 UNAUTHORIZED "Invalid credentials"' },
      status: 401,
      statusText: 'Unauthorized',
      headers: {},
      config: { headers: {} } as never,
    }

    expect(getErrorMessage(error, 'fallback')).toBe('Invalid credentials')
  })
})
