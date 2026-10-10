import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { api } from '../api/client'
import { LoginPage } from './LoginPage'

function LocationProbe() {
  const location = useLocation()
  return <div>at:{location.pathname}{location.search}{location.hash}</div>
}

describe('LoginPage', () => {
  it('returns to the protected path, including search and hash', async () => {
    vi.spyOn(api, 'post').mockResolvedValue({ data: { token: 'abc' } })

    render(
      <MemoryRouter initialEntries={[{
        pathname: '/login',
        search: '?next=/documents/4',
        state: { from: { pathname: '/documents/9', search: '?page=2', hash: '#row' } },
      }]}
      >
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/documents/9" element={<LocationProbe />} />
        </Routes>
      </MemoryRouter>,
    )

    await userEvent.type(screen.getByLabelText('Email address'), 'alex@example.com')
    await userEvent.type(screen.getByLabelText('Password'), 'password123')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByText('at:/documents/9?page=2#row')).toBeInTheDocument()
  })

  it('returns to a requested transaction route with its query and hash', async () => {
    vi.spyOn(api, 'post').mockResolvedValue({ data: { token: 'abc' } })

    render(
      <MemoryRouter initialEntries={[{
        pathname: '/login',
        state: { from: { pathname: '/transactions', search: '?page=2', hash: '#recent' } },
      }]}
      >
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/transactions" element={<LocationProbe />} />
        </Routes>
      </MemoryRouter>,
    )

    await userEvent.type(screen.getByLabelText('Email address'), 'alex@example.com')
    await userEvent.type(screen.getByLabelText('Password'), 'password123')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByText('at:/transactions?page=2#recent')).toBeInTheDocument()
  })

  it('uses Dashboard after login when no protected destination was requested', async () => {
    vi.spyOn(api, 'post').mockResolvedValue({ data: { token: 'abc' } })

    render(
      <MemoryRouter initialEntries={['/login']}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/dashboard" element={<LocationProbe />} />
        </Routes>
      </MemoryRouter>,
    )

    await userEvent.type(screen.getByLabelText('Email address'), 'alex@example.com')
    await userEvent.type(screen.getByLabelText('Password'), 'password123')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByText('at:/dashboard')).toBeInTheDocument()
  })
})
