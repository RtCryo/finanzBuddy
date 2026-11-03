import { useCallback, useEffect, useRef, useState } from 'react'
import { getTransactionCategories, getTransactionTags } from '../api/transactions'
import { createRule, deleteRule, getRules, updateRule } from '../api/rules'
import type { RuleCondition, RuleDraft, TagRule } from '../api/rules'
import { getErrorMessage } from '../api/errors'
import { AppShell } from '../components/AppShell'
import { PageHeading } from '../components/PageHeading'
import type { TransactionTag } from '../types/transaction'

type RuleConditionForm = RuleCondition & { createCategory?: boolean; key?: number }
let nextConditionKey = 0
const withKey = (condition: RuleConditionForm): RuleConditionForm =>
  ({ ...condition, key: nextConditionKey++ })
type RuleForm = { tagId: number; mode: TagRule['mode']; conditions: RuleConditionForm[] }
const createCategoryOption = '__create_category__'

const emptyDraft = (): RuleForm => ({
  tagId: 0,
  mode: 'AND',
  conditions: [withKey({ field: 'COUNTERPARTY', operator: 'CONTAINS', value: '' })],
})

type Pending = { action: 'create' | 'update' | 'delete'; id?: number; draft?: RuleDraft }

export function RulesPage() {
  const [rules, setRules] = useState<TagRule[]>([])
  const [tags, setTags] = useState<TransactionTag[]>([])
  const [categories, setCategories] = useState<string[]>([])
  const [loading, setLoading] = useState(true)
  const [loadFailed, setLoadFailed] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [draft, setDraft] = useState<RuleForm | null>(null)
  const [newTagName, setNewTagName] = useState('')
  const [editingId, setEditingId] = useState<number | null>(null)
  const [validation, setValidation] = useState('')
  const [pending, setPending] = useState<Pending | null>(null)
  const [applyExisting, setApplyExisting] = useState(false)
  const [saving, setSaving] = useState(false)
  const choiceRef = useRef<HTMLInputElement>(null)

  const refresh = useCallback(async () => {
    const [nextRules, nextTags, nextCategories] = await Promise.all([
      getRules(), getTransactionTags(), getTransactionCategories(),
    ])
    setRules(nextRules)
    setTags(nextTags)
    setCategories(nextCategories)
  }, [])

  useEffect(() => {
    let active = true
    void refresh()
      .then(() => {
        if (!active) return
        setError('')
        setLoadFailed(false)
      }).catch((failure: unknown) => {
        if (active) {
          setLoadFailed(true)
          setError(getErrorMessage(failure, 'We could not load your rules.'))
        }
      }).finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [refresh])

  async function retryLoad() {
    setLoading(true)
    try {
      await refresh()
      setLoadFailed(false)
      setError('')
    } catch (failure) {
      setError(getErrorMessage(failure, 'We could not load your rules.'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (pending) choiceRef.current?.focus()
  }, [pending])

  function beginCreate() {
    setDraft({ ...emptyDraft(), tagId: tags[0]?.id ?? 0 })
    setNewTagName('')
    setEditingId(null)
    setValidation('')
    setNotice('')
  }

  function beginEdit(rule: TagRule) {
    setDraft({ tagId: rule.tagId, mode: rule.mode,
      conditions: rule.conditions.map((condition) => withKey({ ...condition, createCategory: false })) })
    setEditingId(rule.id)
    setNewTagName('')
    setValidation('')
    setNotice('')
  }

  function updateCondition(index: number, next: RuleConditionForm) {
    if (!draft) return
    setDraft({ ...draft, conditions: draft.conditions.map((item, row) => row === index ? next : item) })
  }

  function reviewSave() {
    if (!draft || (draft.tagId === 0
      ? !newTagName.trim() || newTagName.trim().length > 255
      : !tags.some((tag) => tag.id === draft.tagId))
      || draft.conditions.length === 0
      || draft.conditions.some((condition) => !condition.value.trim()
        || condition.value.trim().length > 255)) {
      setValidation('Choose or create a tag and enter a value of up to 255 characters for every condition.')
      return
    }
    setValidation('')
    setApplyExisting(false)
    setPending({ action: editingId === null ? 'create' : 'update', id: editingId ?? undefined,
      draft: { ...(draft.tagId === 0 ? { tagName: newTagName.trim() } : { tagId: draft.tagId }),
        mode: draft.mode, conditions: draft.conditions.map((condition) => ({
          field: condition.field, operator: condition.operator, value: condition.value.trim(),
        })),
        ...(draft.conditions.some((condition) => condition.createCategory) ? {
          newCategoryNames: draft.conditions.filter((condition) => condition.createCategory)
            .map((condition) => condition.value.trim()),
        } : {}),
      } })
  }

  function reviewDelete(id: number) {
    setApplyExisting(false)
    setPending({ action: 'delete', id })
    setError('')
  }

  async function confirm() {
    if (!pending) return
    setSaving(true)
    setError('')
    try {
      if (pending.action === 'create' && pending.draft) {
        await createRule(pending.draft, applyExisting)
      } else if (pending.action === 'update' && pending.draft && pending.id !== undefined) {
        await updateRule(pending.id, pending.draft, applyExisting)
      } else if (pending.action === 'delete' && pending.id !== undefined) {
        await deleteRule(pending.id, applyExisting)
      }
      setPending(null)
      setDraft(null)
      setEditingId(null)
      setNotice('Tag rules updated.')
      try {
        await refresh()
      } catch (failure) {
        setLoadFailed(true)
        setError(getErrorMessage(failure, 'Your rule was saved, but the list could not refresh.'))
      }
    } catch (failure) {
      setError(getErrorMessage(failure, 'We could not save your rule. Try again.'))
    } finally {
      setSaving(false)
    }
  }

  const tagName = (id: number) => tags.find((tag) => tag.id === id)?.name ?? 'Unavailable tag'

  return (
    <AppShell>
      <PageHeading title="Tag rules" description="Automatically add your personal tags when transactions match."
        action={<button type="button" className="button button-primary" onClick={beginCreate}
          disabled={loading || loadFailed}>New rule</button>} />

      {error && <div className="notice notice-error" role="alert">{error}
        {loadFailed && <button type="button" className="button button-secondary"
          onClick={() => void retryLoad()}>Retry</button>}
      </div>}
      {notice && <div className="notice notice-success" role="status">{notice}</div>}
      {loading ? <div className="surface list-state" role="status">Loading your tag rules…</div> : loadFailed ? null : (
        <section className="surface rules-card" aria-labelledby="rules-heading">
          <h2 id="rules-heading">Your rules</h2>
          {rules.length === 0 ? <p className="rules-empty">No rules yet. Add one to tag future imports automatically.</p>
            : <ul className="rules-list">{rules.map((rule) => (
              <li key={rule.id} className="rule-item">
                <div><strong>{tagName(rule.tagId)}</strong><p>{rule.conditions.map((condition) =>
                  `${condition.field === 'COUNTERPARTY' ? 'Counterparty' : condition.field === 'CATEGORY' ? 'Category' : 'Description'} ${condition.operator === 'EXACT' ? 'is' : 'contains'} “${condition.value}”`).join(` ${rule.mode} `)}</p></div>
                <div className="rule-actions">
                  <button type="button" className="button button-secondary" onClick={() => beginEdit(rule)}>Edit</button>
                  <button type="button" className="button button-secondary" onClick={() => reviewDelete(rule.id)}>Delete</button>
                </div>
              </li>
            ))}</ul>}
        </section>
      )}

      {draft && !pending && <section className="surface rules-card" aria-labelledby="rule-editor-heading">
        <h2 id="rule-editor-heading">{editingId === null ? 'New rule' : 'Edit rule'}</h2>
        <div className="rules-form">
          <label className="field"><span>Tag to add</span><select value={draft.tagId}
            onChange={(event) => setDraft({ ...draft, tagId: Number(event.target.value) })}>
            {tags.map((tag) => <option key={tag.id} value={tag.id}>{tag.name}</option>)}
            <option value={0}>Create a new tag…</option>
          </select></label>
          {draft.tagId === 0 && <label className="field"><span>New tag name</span>
            <input value={newTagName} maxLength={255} onChange={(event) => setNewTagName(event.target.value)} />
          </label>}
          {draft.conditions.length > 1 && <label className="field"><span>Match conditions</span><select value={draft.mode}
            onChange={(event) => setDraft({ ...draft, mode: event.target.value as RuleDraft['mode'] })}>
            <option value="AND">Match all (AND)</option><option value="OR">Match any (OR)</option>
          </select></label>}
          {draft.conditions.map((condition, index) => <div className="rule-condition" key={condition.key}>
            <label className="field"><span>Field {index + 1}</span><select value={condition.field}
              onChange={(event) => {
                const field = event.target.value as RuleCondition['field']
                updateCondition(index, { ...condition, field,
                  value: field === 'CATEGORY' ? (categories[0] ?? '') : '',
                  createCategory: field === 'CATEGORY' && categories.length === 0 })
              }}>
              <option value="COUNTERPARTY">Counterparty</option><option value="DESCRIPTION">Description</option>
              <option value="CATEGORY">Category</option>
            </select></label>
            <label className="field"><span>Match</span><select value={condition.operator}
              onChange={(event) => updateCondition(index, { ...condition, operator: event.target.value as RuleCondition['operator'] })}>
              <option value="EXACT">Exactly</option><option value="CONTAINS">Contains</option>
            </select></label>
            {condition.field === 'CATEGORY' ? <>
              <label className="field"><span>Category {index + 1}</span><select
                value={condition.createCategory ? createCategoryOption : condition.value}
                onChange={(event) => updateCondition(index, { ...condition,
                  createCategory: event.target.value === createCategoryOption,
                  value: event.target.value === createCategoryOption ? '' : event.target.value })}>
                {!condition.createCategory && condition.value && !categories.some((category) => category === condition.value)
                  && <option value={condition.value}>{condition.value}</option>}
                {categories.map((category) => <option key={category} value={category}>{category}</option>)}
                <option value={createCategoryOption}>+ Create category…</option>
              </select></label>
              {condition.createCategory && <label className="field rule-category-name"><span>New category name {index + 1}</span>
                <input value={condition.value} maxLength={255}
                  onChange={(event) => updateCondition(index, { ...condition, value: event.target.value })} />
              </label>}
            </> : <label className="field"><span>Text</span><input value={condition.value} maxLength={255}
              onChange={(event) => updateCondition(index, { ...condition, value: event.target.value })} /></label>}
            {draft.conditions.length > 1 && <button type="button" className="button button-secondary rule-remove-button"
              onClick={() => setDraft({ ...draft, conditions: draft.conditions.filter((_, row) => row !== index) })}
              aria-label={`Remove condition ${index + 1}`}>Remove condition</button>}
          </div>)}
          <button type="button" className="button button-secondary" onClick={() => setDraft({ ...draft,
            conditions: [...draft.conditions, withKey({ field: 'COUNTERPARTY', operator: 'CONTAINS', value: '' })] })}>
            Add condition
          </button>
          {validation && <p className="notice notice-error" role="alert">{validation}</p>}
          <div className="rule-actions rule-form-actions">
            <button type="button" className="button button-secondary" onClick={() => setDraft(null)}>Cancel</button>
            <button type="button" className="button button-primary" onClick={reviewSave}>Review existing transactions</button>
          </div>
        </div>
      </section>}

      {pending && <section className="surface rules-card" aria-labelledby="rule-choice-heading">
        <h2 id="rule-choice-heading">Existing transactions</h2>
        <p>{pending.action === 'create' ? 'Apply this rule to existing transactions?' : pending.action === 'update'
          ? 'Update this rule’s effects on existing transactions?' : 'Remove this rule’s effects from existing transactions?'}</p>
        <fieldset className="rule-choice"><legend>Choose what happens to existing transactions</legend>
          <label><input ref={choiceRef} type="radio" name="existing-choice" checked={!applyExisting}
            onChange={() => setApplyExisting(false)} /> Leave existing tags unchanged</label>
          <label><input type="radio" name="existing-choice" checked={applyExisting}
            onChange={() => setApplyExisting(true)} /> {pending.action === 'delete' ? 'Remove this rule’s tags' : 'Update existing tags'}</label>
        </fieldset>
        <div className="rule-actions rule-form-actions">
          <button type="button" className="button button-secondary" disabled={saving}
            onClick={() => setPending(null)}>{pending.action === 'delete' ? 'Cancel' : 'Back'}</button>
          <button type="button" className="button button-primary" disabled={saving}
            onClick={() => void confirm()}>{saving ? (pending.action === 'delete' ? 'Deleting…' : 'Saving…')
              : pending.action === 'create' ? 'Create rule' : pending.action === 'update' ? 'Save changes' : 'Delete rule'}</button>
        </div>
      </section>}
    </AppShell>
  )
}
