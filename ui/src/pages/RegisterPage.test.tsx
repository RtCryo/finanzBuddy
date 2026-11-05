import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError } from 'axios'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from '../api/client'
import { RegisterPage } from './RegisterPage'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe('RegisterPage', () => {
  it('creates a shared homeBuddy account through the auth route', async () => {
    const post = vi.spyOn(api, 'post').mockResolvedValue({ data: {} })
    render(<MemoryRouter><RegisterPage /></MemoryRouter>)

    expect(screen.getByText(/homeBuddy account for finanzBuddy and calendarBuddy/)).toBeInTheDocument()
    await userEvent.type(screen.getByLabelText('Email address'), 'alex@example.com')
    await userEvent.type(screen.getByPlaceholderText('Create a password'), 'password123')
    await userEvent.type(screen.getByLabelText('Confirm password'), 'password123')
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }))

    expect(await screen.findByText('Your account is ready.')).toBeInTheDocument()
    expect(post).toHaveBeenCalledWith('/auth/register', {
      email: 'alex@example.com', password: 'password123',
    })
  })

  it('shows the identity service error', async () => {
    vi.spyOn(api, 'post').mockRejectedValue(new AxiosError('Registration failed'))
    render(<MemoryRouter><RegisterPage /></MemoryRouter>)
    await userEvent.type(screen.getByLabelText('Email address'), 'alex@example.com')
    await userEvent.type(screen.getByPlaceholderText('Create a password'), 'password123')
    await userEvent.type(screen.getByLabelText('Confirm password'), 'password123')
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Registration failed')
  })
})
