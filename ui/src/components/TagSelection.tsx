import { useId, useRef, useState } from 'react'
import type { TagDraft, TransactionTag } from '../types/transaction'

export function TagSelection({ choices, selected, onChange, allowCreate = false, disabled = false, label = 'Tags' }: {
  choices: TransactionTag[]
  selected: TagDraft[]
  onChange: (tags: TagDraft[]) => void
  allowCreate?: boolean
  disabled?: boolean
  label?: string
}) {
  const id = useId()
  const input = useRef<HTMLInputElement>(null)
  const [query, setQuery] = useState('')
  const [announcement, setAnnouncement] = useState('')
  const [isOpen, setIsOpen] = useState(false)
  const normalize = (name: string) => name.trim().toLowerCase()
  const matching = choices.filter(tag => !selected.some(item => normalize(item.name) === normalize(tag.name))
    && normalize(tag.name).includes(normalize(query)))
  const available = matching.slice(0, 6)
  function add(tag: TagDraft) {
    if (!selected.some(item => normalize(item.name) === normalize(tag.name))) {
      onChange([...selected, tag])
      setAnnouncement(`Added ${tag.name}`)
    }
    setQuery('')
    setIsOpen(false)
    input.current?.focus()
  }
  function addName() {
    const name = query.trim()
    if (!name || name.length > 255) return
    const existing = choices.find(tag => normalize(tag.name) === normalize(name))
    if (existing) add(existing)
    else if (allowCreate) add({ name })
  }
  return <div className="tag-selection" role="group" aria-labelledby={`${id}-label`}>
    <div className="tag-selection-heading">
      <span id={`${id}-label`}>{label}</span>
      {selected.length > 0 && <span className="tag-selection-count">{selected.length} selected</span>}
    </div>
    {selected.length > 0 && <div className="tag-chips">
      {selected.map(tag => <span className="tag-chip" key={tag.id ?? tag.name}>
        <span>{tag.name}</span>
        <button type="button" disabled={disabled} aria-label={`Remove tag ${tag.name}`} onClick={() => {
          onChange(selected.filter(item => item !== tag))
          setAnnouncement(`Removed ${tag.name}`)
          input.current?.focus()
        }}><svg aria-hidden="true" viewBox="0 0 16 16"><path d="M4 4 12 12M12 4 4 12" /></svg></button>
      </span>)}
    </div>}
    <label className="visually-hidden" htmlFor={`${id}-search`}>Search{allowCreate ? ' or create' : ''} tags</label>
    <div className="tag-search-wrap">
      <svg viewBox="0 0 20 20" fill="none" aria-hidden="true"><circle cx="8.8" cy="8.8" r="5.8" stroke="currentColor" strokeWidth="1.5" /><path d="m13.2 13.2 4 4" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" /></svg>
      <input id={`${id}-search`} ref={input} value={query} disabled={disabled} maxLength={255}
        aria-expanded={isOpen} aria-controls={`${id}-choices`} autoComplete="off"
        placeholder={allowCreate ? 'Search or create a tag' : 'Search your tags'}
        onFocus={() => setIsOpen(true)} onBlur={() => window.setTimeout(() => setIsOpen(false), 120)}
        onClick={() => setIsOpen(true)}
        onChange={event => { setQuery(event.target.value); setIsOpen(true) }} onKeyDown={event => {
          if (event.key === 'Enter') { event.preventDefault(); addName() }
          if (event.key === 'Escape') { setIsOpen(false); setQuery('') }
        }} />
      {query && <button className="tag-search-clear" type="button" aria-label="Clear tag search"
        onMouseDown={event => event.preventDefault()} onClick={() => { setQuery(''); input.current?.focus() }}>×</button>}
      {isOpen && !disabled && <div className="tag-choices" id={`${id}-choices`} role="listbox" aria-label="Available tags">
        {available.map(tag => <button className="tag-choice" role="option" aria-selected="false" type="button" key={tag.id}
          onMouseDown={event => event.preventDefault()} onClick={() => add(tag)}>
          <span className="tag-choice-mark" aria-hidden="true">＋</span><span className="tag-choice-name">{tag.name}</span>
          <span className="tag-choice-hint">Add</span>
        </button>)}
        {matching.length > available.length && <span className="tag-choice-empty">Type to narrow the list · {matching.length - available.length} more</span>}
        {!available.length && !query.trim() && <span className="tag-choice-empty">
          {choices.length ? 'All available tags are selected.' : 'No personal tags available yet.'}
        </span>}
        {!available.length && query.trim() && !allowCreate &&
          <span className="tag-choice-empty">No matching tags.</span>}
        {allowCreate && query.trim() && !choices.some(tag => normalize(tag.name) === normalize(query)) &&
          <button className="tag-choice tag-choice-create" role="option" aria-selected="false" type="button"
            onMouseDown={event => event.preventDefault()} onClick={addName}>
            <span className="tag-choice-mark" aria-hidden="true">＋</span><span>Create <strong>“{query.trim()}”</strong></span>
          </button>}
      </div>}
    </div>
    <span className="visually-hidden" role="status">{announcement}</span>
  </div>
}
