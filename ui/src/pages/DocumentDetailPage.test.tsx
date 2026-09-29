import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from '../api/client'
import { getDocument, getDocumentTransactions } from '../api/documents'
import { DocumentDetailPage } from './DocumentDetailPage'

vi.mock('../api/documents', () => ({
  getDocument: vi.fn(),
  getDocumentTransactions: vi.fn(),
}))

const processingDocument = {
  id: 7,
  originalFilename: 'statement.csv',
  type: 'CSV',
  status: 'PROCESSING' as const,
  createdAt: '2026-09-28T12:00:00Z',
  errorMessage: null,
}

function renderDetail() {
  return render(
    <MemoryRouter initialEntries={['/documents/7']}>
      <Routes>
        <Route path="/documents/:id" element={<DocumentDetailPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('DocumentDetailPage', () => {
  beforeEach(() => {
    vi.mocked(getDocument).mockReset()
    vi.mocked(getDocumentTransactions).mockReset()
    URL.createObjectURL = vi.fn(() => 'blob:mock')
    URL.revokeObjectURL = vi.fn()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('refreshes a processing document', async () => {
    vi.useFakeTimers()
    vi.mocked(getDocument).mockResolvedValue(processingDocument)

    renderDetail()
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0)
    })

    expect(screen.getByText('Your document is being processed')).toBeInTheDocument()
    expect(getDocument).toHaveBeenCalledTimes(1)

    await act(async () => {
      await vi.advanceTimersByTimeAsync(5000)
    })
    expect(getDocument).toHaveBeenCalledTimes(2)
  })

  it('requests the original file as a download and attaches the link', async () => {
    vi.mocked(getDocument).mockResolvedValue({ ...processingDocument, status: 'PROCESSED' })
    vi.mocked(getDocumentTransactions).mockResolvedValue([])
    const get = vi.spyOn(api, 'get').mockResolvedValue({ data: new Blob(['csv']), headers: {} })
    const append = vi.spyOn(document.body, 'appendChild')
    const user = userEvent.setup()

    renderDetail()
    await user.click(await screen.findByRole('button', { name: 'Download original file statement.csv' }))

    expect(get).toHaveBeenCalledWith('/documents/7/download', expect.objectContaining({
      responseType: 'blob',
      headers: { Accept: 'application/octet-stream, application/problem+json, application/json' },
    }))
    expect(append).toHaveBeenCalled()
    append.mockRestore()
    get.mockRestore()
  })
})
