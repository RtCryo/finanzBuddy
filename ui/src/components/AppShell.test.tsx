import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it } from 'vitest'
import { getToken, setSession } from '../auth/storage'
import { AppShell } from './AppShell'

describe('AppShell', () => {
  afterEach(cleanup)

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

  it('links to transaction history and labels the workspace page', () => {
    render(
      <MemoryRouter initialEntries={['/transactions']}>
        <Routes>
          <Route path="/transactions" element={<AppShell><div>history</div></AppShell>} />
        </Routes>
      </MemoryRouter>,
    )

    expect(screen.getByRole('link', { name: 'Transaction history' }))
      .toHaveAttribute('href', '/transactions')
    expect(screen.getByRole('link', { name: 'Transaction history' }))
      .toHaveAttribute('aria-current', 'page')
    expect(screen.getByLabelText('Breadcrumb')).toHaveTextContent('Transaction history')
  })

  it('keeps shell navigation and profile actions reachable by keyboard', async () => {
    const user = userEvent.setup()
    render(
      <MemoryRouter initialEntries={['/dashboard']}>
        <AppShell><div>dashboard</div></AppShell>
      </MemoryRouter>,
    )

    await user.tab()
    expect(screen.getByRole('link', { name: 'finanzBuddy home' })).toHaveFocus()
    await user.tab()
    expect(screen.getByRole('link', { name: 'Dashboard' })).toHaveFocus()
    await user.tab()
    expect(screen.getByRole('link', { name: 'Documents' })).toHaveFocus()
    await user.tab()
    expect(screen.getByRole('link', { name: 'Transaction history' })).toHaveFocus()
    await user.tab()
    expect(screen.getByRole('button', { name: 'Sign out' })).toHaveFocus()
  })

  it('shows shared workspace destinations and privacy reassurance once', () => {
    render(
      <MemoryRouter initialEntries={['/documents']}>
        <AppShell><div>docs</div></AppShell>
      </MemoryRouter>,
    )

    const navigation = screen.getByRole('navigation', { name: 'Workspace' })
    expect(within(navigation).getByRole('link', { name: 'Dashboard' })).toHaveAttribute('href', '/dashboard')
    expect(within(navigation).getByRole('link', { name: 'Documents' })).toHaveAttribute('href', '/documents')
    expect(within(navigation).getByRole('link', { name: 'Transaction history' })).toHaveAttribute('href', '/transactions')
    expect(within(navigation).queryByRole('link', { name: 'Upload a file' })).not.toBeInTheDocument()
    expect(screen.getAllByText('Your financial data is secure and only accessible to you.')).toHaveLength(1)
  })
})
