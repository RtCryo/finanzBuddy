import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { getToken, setSession } from '../auth/storage'
import { AppShell } from './AppShell'

describe('AppShell', () => {
  it('signs the user out', async () => {
    setSession('token', 'alex@example.com')

    render(
      <MemoryRouter initialEntries={['/documents']}>
        <Routes>
          <Route path="/documents" element={<AppShell><div>docs</div></AppShell>} />
          <Route path="/login" element={<div>login-screen</div>} />
        </Routes>
      </MemoryRouter>,
    )

    await userEvent.click(screen.getByRole('button', { name: 'Sign out' }))

    expect(getToken()).toBeNull()
    expect(screen.getByText('login-screen')).toBeInTheDocument()
  })
})
