import { act, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { getDocuments } from '../api/documents'
import { DocumentsPage } from './DocumentsPage'

vi.mock('../api/documents', () => ({
  getDocuments: vi.fn(),
}))

const emptyPage = {
  content: [],
  totalElements: 0,
  totalPages: 0,
  number: 0,
  size: 20,
  totalDocuments: 0,
  processedCount: 0,
  inProgressCount: 0,
}

describe('DocumentsPage', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.mocked(getDocuments).mockReset()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('clears a list error after a later poll succeeds', async () => {
    vi.mocked(getDocuments).mockRejectedValue(new Error('offline'))

    render(
      <MemoryRouter>
        <DocumentsPage />
      </MemoryRouter>,
    )

    await act(async () => {
      await vi.advanceTimersByTimeAsync(0)
    })
    expect(screen.getByRole('alert')).toHaveTextContent('We could not load your documents.')

    vi.mocked(getDocuments).mockResolvedValue(emptyPage)
    await act(async () => {
      await vi.advanceTimersByTimeAsync(5000)
    })
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })
})
