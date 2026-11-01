import { useEffect, useRef, useState } from 'react'
import type { TransactionTag } from '../types/transaction'

export function HistoryTagFilter({ choices, selected, mode, onSelectionChange, onModeChange, error, onRetry }: {
  choices: TransactionTag[]
  selected: TransactionTag[]
  mode: 'AND' | 'OR'
  onSelectionChange: (tags: TransactionTag[]) => void
  onModeChange: (mode: 'AND' | 'OR') => void
  error: string
  onRetry: () => void
}) {
  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    function closeOutside(event: PointerEvent) {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false)
    }
    document.addEventListener('pointerdown', closeOutside)
    return () => document.removeEventListener('pointerdown', closeOutside)
  }, [open])

  function toggle(tag: TransactionTag) {
    onSelectionChange(selected.some(item => item.id === tag.id)
      ? selected.filter(item => item.id !== tag.id)
      : [...selected, tag])
  }

  return <div className="history-tag-picker" ref={rootRef} onKeyDown={event => {
    if (event.key === 'Escape' && open) {
      event.stopPropagation()
      setOpen(false)
      rootRef.current?.querySelector<HTMLButtonElement>('.history-tag-trigger')?.focus()
    }
  }}>
    <button className="history-tag-trigger" type="button" aria-expanded={open}
      aria-controls="history-tag-options" aria-label={`Filter by tags, ${selected.length} selected`}
      onClick={() => setOpen(current => !current)}>
      <span>{selected.length ? `Tags (${selected.length})` : 'All tags'}</span>
      <svg className="history-select-chevron" viewBox="0 0 20 20" aria-hidden="true"><path d="m5 7 5 5 5-5" /></svg>
    </button>
    {open && <div className="history-tag-popover" id="history-tag-options">
      <div className="history-tag-popover-heading">
        <strong>Filter by tags</strong>
        <span>{selected.length} selected</span>
      </div>
      {error ? <div className="history-tag-popover-error" role="alert">
        {error} <button type="button" onClick={onRetry}>Retry</button>
      </div> : choices.length ? <div className="history-tag-options" role="group" aria-label="Choose tags">
        {choices.map(tag => <label className="history-tag-option" key={tag.id}>
          <input type="checkbox" checked={selected.some(item => item.id === tag.id)} onChange={() => toggle(tag)} />
          <span>{tag.name}</span>
        </label>)}
      </div> : <p className="history-tag-empty">No personal tags yet.</p>}
      <fieldset className="history-tag-match">
        <legend>Match selected tags</legend>
        <label><input type="radio" name="history-tag-mode" checked={mode === 'AND'} onChange={() => onModeChange('AND')} /> All selected (AND)</label>
        <label><input type="radio" name="history-tag-mode" checked={mode === 'OR'} onChange={() => onModeChange('OR')} /> Any selected (OR)</label>
      </fieldset>
    </div>}
  </div>
}
