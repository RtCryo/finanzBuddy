import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { getTransactionCategories, getTransactionTags } from '../api/transactions'
import { createRule, deleteRule, getRules, updateRule } from '../api/rules'
import type { TagRule } from '../api/rules'
import { RulesPage } from './RulesPage'

vi.mock('../api/transactions', () => ({ getTransactionTags: vi.fn(), getTransactionCategories: vi.fn() }))
vi.mock('../api/rules', () => ({ getRules: vi.fn(), createRule: vi.fn(), updateRule: vi.fn(), deleteRule: vi.fn() }))

const rule: TagRule = { id: 3, tagId: 4, mode: 'AND',
  conditions: [{ field: 'COUNTERPARTY', operator: 'EXACT', value: 'X' }] }

function renderPage() {
  return render(<MemoryRouter initialEntries={['/rules']}><RulesPage /></MemoryRouter>)
}

describe('RulesPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(getTransactionTags).mockResolvedValue([{ id: 4, name: 'Travel' }])
    vi.mocked(getTransactionCategories).mockResolvedValue(['Groceries', 'Travel'])
    vi.mocked(getRules).mockResolvedValue([])
    vi.mocked(createRule).mockResolvedValue(rule)
    vi.mocked(updateRule).mockResolvedValue(rule)
    vi.mocked(deleteRule).mockResolvedValue()
  })
  afterEach(cleanup)

  it('shows loading, empty, and error states', async () => {
    let resolve!: (rules: TagRule[]) => void
    vi.mocked(getRules).mockReturnValueOnce(new Promise((done) => { resolve = done }))
    renderPage()
    expect(screen.getByRole('status')).toHaveTextContent('Loading your tag rules')
    resolve([])
    expect(await screen.findByText(/No rules yet/)).toBeInTheDocument()
    cleanup()
    vi.mocked(getRules).mockRejectedValueOnce(new Error('offline'))
    renderPage()
    expect(await screen.findByRole('alert')).toHaveTextContent('We could not load your rules.')
  })

  it('creates a flat AND rule with an explicit history choice and validation', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText(/No rules yet/)
    await user.click(screen.getByRole('button', { name: 'New rule' }))
    expect(screen.queryByRole('combobox', { name: 'Match conditions' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Review existing transactions' }))
    expect(screen.getByRole('alert')).toHaveTextContent('enter a value')
    await user.type(screen.getByRole('textbox', { name: 'Text' }), ' YUPI ')
    await user.click(screen.getByRole('button', { name: 'Add condition' }))
    expect(screen.getByRole('combobox', { name: 'Match conditions' })).toHaveValue('AND')
    await user.selectOptions(screen.getByRole('combobox', { name: 'Field 2' }), 'DESCRIPTION')
    await user.selectOptions(screen.getAllByRole('combobox', { name: 'Match' })[1], 'EXACT')
    await user.type(screen.getAllByRole('textbox', { name: 'Text' })[1], 'X')
    await user.click(screen.getByRole('button', { name: 'Review existing transactions' }))
    await user.click(screen.getByRole('button', { name: 'Back' }))
    expect(screen.getAllByRole('textbox', { name: 'Text' })[0]).toHaveValue(' YUPI ')
    await user.click(screen.getByRole('button', { name: 'Review existing transactions' }))
    expect(screen.getByRole('radio', { name: 'Leave existing tags unchanged' })).toBeChecked()
    await user.click(screen.getByRole('radio', { name: 'Update existing tags' }))
    await user.click(screen.getByRole('button', { name: 'Create rule' }))
    await waitFor(() => expect(createRule).toHaveBeenCalledWith({ tagId: 4, mode: 'AND', conditions: [
      { field: 'COUNTERPARTY', operator: 'CONTAINS', value: 'YUPI' },
      { field: 'DESCRIPTION', operator: 'EXACT', value: 'X' },
    ] }, true))
    expect(getRules).toHaveBeenCalledTimes(2)
    expect(getTransactionTags).toHaveBeenCalledTimes(2)
    expect(getTransactionCategories).toHaveBeenCalledTimes(2)
  })

  it('creates a new tag with a rule when no tags exist', async () => {
    const user = userEvent.setup()
    vi.mocked(getTransactionTags).mockResolvedValue([])
    renderPage()
    await screen.findByText(/No rules yet/)
    await user.click(screen.getByRole('button', { name: 'New rule' }))
    expect(screen.getByRole('textbox', { name: 'New tag name' })).toBeInTheDocument()
    await user.type(screen.getByRole('textbox', { name: 'New tag name' }), ' Groceries ')
    await user.type(screen.getByRole('textbox', { name: 'Text' }), 'Market')
    await user.click(screen.getByRole('button', { name: 'Review existing transactions' }))
    await user.click(screen.getByRole('button', { name: 'Create rule' }))
    await waitFor(() => expect(createRule).toHaveBeenCalledWith({
      tagName: 'Groceries', mode: 'AND',
      conditions: [{ field: 'COUNTERPARTY', operator: 'CONTAINS', value: 'Market' }],
    }, false))
  })

  it('edits with OR and leaves existing tags unchanged; delete can remove effects', async () => {
    const user = userEvent.setup()
    vi.mocked(getRules).mockResolvedValue([rule])
    renderPage()
    await screen.findByText(/Counterparty is/)
    await user.click(screen.getByRole('button', { name: 'Edit' }))
    expect(screen.queryByRole('combobox', { name: 'Match conditions' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Add condition' }))
    await user.selectOptions(screen.getByRole('combobox', { name: 'Match conditions' }), 'OR')
    await user.click(screen.getByRole('button', { name: 'Remove condition 2' }))
    expect(screen.queryByRole('combobox', { name: 'Match conditions' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Add condition' }))
    expect(screen.getByRole('combobox', { name: 'Match conditions' })).toHaveValue('OR')
    await user.click(screen.getByRole('button', { name: 'Remove condition 2' }))
    await user.click(screen.getByRole('button', { name: 'Review existing transactions' }))
    await user.click(screen.getByRole('button', { name: 'Save changes' }))
    await waitFor(() => expect(updateRule).toHaveBeenCalledWith(3,
      expect.objectContaining({ mode: 'OR' }), false))
    await user.click(screen.getByRole('button', { name: 'Delete' }))
    await user.click(screen.getByRole('radio', { name: 'Remove this rule’s tags' }))
    await user.click(screen.getByRole('button', { name: 'Delete rule' }))
    await waitFor(() => expect(deleteRule).toHaveBeenCalledWith(3, true))
  })

  it('supports keyboard condition editing and confirms an existing-transaction edit', async () => {
    const user = userEvent.setup()
    vi.mocked(getRules).mockResolvedValue([rule])
    renderPage()
    await screen.findByText(/Counterparty is/)
    const edit = screen.getByRole('button', { name: 'Edit' })
    edit.focus()
    await user.keyboard('{Enter}')
    const text = screen.getByRole('textbox', { name: 'Text' })
    text.focus()
    await user.keyboard('{Control>}a{/Control}YUPI')
    await user.click(screen.getByRole('button', { name: 'Review existing transactions' }))
    expect(screen.getByRole('radio', { name: 'Leave existing tags unchanged' })).toHaveFocus()
    await user.keyboard('{ArrowDown}')
    expect(screen.getByRole('radio', { name: 'Update existing tags' })).toBeChecked()
    await user.click(screen.getByRole('button', { name: 'Save changes' }))
    await waitFor(() => expect(updateRule).toHaveBeenCalledWith(3,
      expect.objectContaining({ conditions: [expect.objectContaining({ value: 'YUPI' })] }), true))
  })

  it('allows cancellation and preserves a failed operation for retry', async () => {
    const user = userEvent.setup()
    vi.mocked(getRules).mockResolvedValue([rule])
    vi.mocked(deleteRule).mockRejectedValueOnce(new Error('offline'))
    renderPage()
    await screen.findByText(/Counterparty is/)
    await user.click(screen.getByRole('button', { name: 'Delete' }))
    await user.click(screen.getByRole('button', { name: 'Cancel' }))
    expect(deleteRule).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: 'Delete' }))
    await user.click(screen.getByRole('button', { name: 'Delete rule' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('We could not save your rule.')
    expect(screen.getByRole('button', { name: 'Delete rule' })).toBeEnabled()
  })

  it('uses no-history choices for create and delete', async () => {
    const user = userEvent.setup()
    vi.mocked(getRules).mockResolvedValue([rule])
    renderPage()
    await screen.findByText(/Counterparty is/)
    await user.click(screen.getByRole('button', { name: 'New rule' }))
    await user.type(screen.getByRole('textbox', { name: 'Text' }), 'X')
    await user.click(screen.getByRole('button', { name: 'Review existing transactions' }))
    await user.click(screen.getByRole('button', { name: 'Create rule' }))
    await waitFor(() => expect(createRule).toHaveBeenCalledWith(expect.anything(), false))
    await user.click(screen.getByRole('button', { name: 'Delete' }))
    await user.click(screen.getByRole('button', { name: 'Delete rule' }))
    await waitFor(() => expect(deleteRule).toHaveBeenCalledWith(3, false))
  })

  it('retries a failed initial load without showing an empty list', async () => {
    const user = userEvent.setup()
    vi.mocked(getRules).mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce([rule])
    renderPage()
    expect(await screen.findByRole('alert')).toHaveTextContent('We could not load your rules.')
    expect(screen.queryByText(/No rules yet/)).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Retry' }))
    expect(await screen.findByText(/Counterparty is/)).toBeInTheDocument()
  })

  it('creates and edits Category conditions and renders their summary', async () => {
    const user = userEvent.setup()
    const categoryRule: TagRule = { ...rule,
      conditions: [{ field: 'CATEGORY', operator: 'CONTAINS', value: 'Groceries' }] }
    vi.mocked(createRule).mockResolvedValue(categoryRule)
    vi.mocked(getRules).mockResolvedValueOnce([]).mockResolvedValue([categoryRule])
    renderPage()
    await screen.findByText(/No rules yet/)
    await user.click(screen.getByRole('button', { name: 'New rule' }))
    await user.selectOptions(screen.getByRole('combobox', { name: 'Field 1' }), 'CATEGORY')
    expect(screen.queryByRole('textbox', { name: 'Text' })).not.toBeInTheDocument()
    await user.selectOptions(screen.getByRole('combobox', { name: 'Category 1' }), 'Groceries')
    await user.click(screen.getByRole('button', { name: 'Review existing transactions' }))
    await user.click(screen.getByRole('button', { name: 'Create rule' }))
    await waitFor(() => expect(createRule).toHaveBeenCalledWith(
      expect.objectContaining({ conditions: [{ field: 'CATEGORY', operator: 'CONTAINS', value: 'Groceries' }] }), false))
    expect(await screen.findByText(/Category contains “Groceries”/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Edit' }))
    expect(screen.getByRole('combobox', { name: 'Field 1' })).toHaveValue('CATEGORY')
    expect(screen.getByRole('combobox', { name: 'Category 1' })).toHaveValue('Groceries')
    await user.selectOptions(screen.getByRole('combobox', { name: 'Match' }), 'EXACT')
    await user.click(screen.getByRole('button', { name: 'Review existing transactions' }))
    await user.click(screen.getByRole('button', { name: 'Save changes' }))
    await waitFor(() => expect(updateRule).toHaveBeenCalledWith(3,
      expect.objectContaining({ conditions: [{ field: 'CATEGORY', operator: 'EXACT', value: 'Groceries' }] }), false))
  })

  it('creates a personal category with the rule and keeps it out of the dictionary on cancellation', async () => {
    const user = userEvent.setup()
    vi.mocked(getTransactionCategories).mockResolvedValue([])
    renderPage()
    await screen.findByText(/No rules yet/)
    await user.click(screen.getByRole('button', { name: 'New rule' }))
    await user.selectOptions(screen.getByRole('combobox', { name: 'Field 1' }), 'CATEGORY')
    expect(screen.getByRole('combobox', { name: 'Category 1' })).toHaveValue('__create_category__')
    await user.type(screen.getByRole('textbox', { name: 'New category name 1' }), ' Utilities ')
    await user.click(screen.getByRole('button', { name: 'Review existing transactions' }))
    await user.click(screen.getByRole('button', { name: 'Back' }))
    expect(createRule).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: 'Review existing transactions' }))
    await user.click(screen.getByRole('button', { name: 'Create rule' }))
    await waitFor(() => expect(createRule).toHaveBeenCalledWith({
      tagId: 4, mode: 'AND',
      conditions: [{ field: 'CATEGORY', operator: 'CONTAINS', value: 'Utilities' }],
      newCategoryNames: ['Utilities'],
    }, false))
  })

  it('preserves an older Category condition absent from the available categories', async () => {
    const user = userEvent.setup()
    vi.mocked(getRules).mockResolvedValue([{ ...rule,
      conditions: [{ field: 'CATEGORY', operator: 'CONTAINS', value: 'grocer' }] }])
    renderPage()
    await screen.findByText(/Category contains “grocer”/)
    await user.click(screen.getByRole('button', { name: 'Edit' }))
    expect(screen.getByRole('combobox', { name: 'Category 1' })).toHaveValue('grocer')
    await user.click(screen.getByRole('button', { name: 'Review existing transactions' }))
    await user.click(screen.getByRole('button', { name: 'Save changes' }))
    await waitFor(() => expect(updateRule).toHaveBeenCalledWith(3,
      expect.objectContaining({ conditions: [{ field: 'CATEGORY', operator: 'CONTAINS', value: 'grocer' }] }), false))
  })
})
