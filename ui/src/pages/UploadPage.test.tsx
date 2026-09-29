import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from '../api/client'
import { UploadPage } from './UploadPage'

describe('UploadPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('does not call a failed upload complete', async () => {
    vi.spyOn(api, 'post').mockRejectedValue(new Error('boom'))
    const user = userEvent.setup()

    render(
      <MemoryRouter>
        <UploadPage />
      </MemoryRouter>,
    )

    const file = new File(['a,b'], 'statement.csv', { type: 'text/csv' })
    await user.upload(screen.getByLabelText('Choose a CSV, PDF, or XML file'), file)
    await user.selectOptions(screen.getByRole('combobox'), 'CSV')
    await user.click(screen.getByRole('button', { name: 'Upload document' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('The upload failed. Please try again.')
    expect(screen.queryByText('Upload complete')).not.toBeInTheDocument()
  })
})
