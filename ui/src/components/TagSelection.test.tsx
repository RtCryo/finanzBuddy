import { act, cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { TagSelection } from './TagSelection'

const choices = [{ id: 1, name: 'Groceries' }]

function renderTagSelection() {
  return render(<TagSelection choices={choices} selected={[]} onChange={() => {}} />)
}

describe('TagSelection', () => {
  afterEach(() => {
    cleanup()
    vi.useRealTimers()
  })

  it('closes the choices shortly after the search loses focus', () => {
    vi.useFakeTimers()
    renderTagSelection()
    const search = screen.getByRole('textbox', { name: 'Search tags' })

    fireEvent.focus(search)
    expect(screen.getByRole('listbox', { name: 'Available tags' })).toBeInTheDocument()

    fireEvent.blur(search)
    expect(screen.getByRole('listbox', { name: 'Available tags' })).toBeInTheDocument()

    act(() => { vi.advanceTimersByTime(120) })
    expect(screen.queryByRole('listbox', { name: 'Available tags' })).not.toBeInTheDocument()
  })

  it('keeps the choices open when focus returns before the close delay', () => {
    vi.useFakeTimers()
    renderTagSelection()
    const search = screen.getByRole('textbox', { name: 'Search tags' })

    fireEvent.focus(search)
    fireEvent.blur(search)
    fireEvent.focus(search)
    act(() => { vi.advanceTimersByTime(120) })

    expect(screen.getByRole('listbox', { name: 'Available tags' })).toBeInTheDocument()
  })

  it('clears the pending close timer when unmounted after a blur', () => {
    vi.useFakeTimers()
    const { unmount } = renderTagSelection()
    const search = screen.getByRole('textbox', { name: 'Search tags' })

    fireEvent.focus(search)
    fireEvent.blur(search)
    expect(vi.getTimerCount()).toBe(1)

    unmount()

    expect(vi.getTimerCount()).toBe(0)
  })
})
