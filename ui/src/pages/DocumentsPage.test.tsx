import { act, cleanup, render, screen } from '@testing-library/react'
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
    cleanup()
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

  it('keeps automatic polling and the Documents-page upload action without a manual refresh control', async () => {
    const processingDocument = {
      id: 7,
      originalFilename: 'statement.csv',
      type: 'CSV',
      status: 'PROCESSING' as const,
      createdAt: '2026-09-28T12:00:00Z',
      errorMessage: null,
    }
    const processedDocument = { ...processingDocument, status: 'PROCESSED' as const }
    vi.mocked(getDocuments)
      .mockResolvedValueOnce({ ...emptyPage, content: [processingDocument], totalElements: 1 })
      .mockResolvedValue({ ...emptyPage, content: [processedDocument], totalElements: 1 })

    render(
      <MemoryRouter>
        <DocumentsPage />
      </MemoryRouter>,
    )

    await act(async () => {
      await vi.advanceTimersByTimeAsync(0)
    })
    expect(screen.queryByRole('button', { name: 'Refresh' })).not.toBeInTheDocument()
    expect(screen.getAllByRole('link', { name: 'Upload document' })).toHaveLength(1)
    expect(screen.getByText('Processing')).toBeInTheDocument()
    expect(getDocuments).toHaveBeenCalledTimes(1)

    await act(async () => {
      await vi.advanceTimersByTimeAsync(5000)
    })
    expect(getDocuments).toHaveBeenCalledTimes(2)
    expect(screen.getByRole('row', { name: /statement.csv, Processed/ })).toBeInTheDocument()
  })
})
