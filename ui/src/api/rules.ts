import { api } from './client'

export type RuleCondition = {
  field: 'COUNTERPARTY' | 'DESCRIPTION' | 'CATEGORY'
  operator: 'EXACT' | 'CONTAINS'
  value: string
}

export type TagRule = {
  id: number
  tagId: number
  mode: 'AND' | 'OR'
  conditions: RuleCondition[]
}

export type RuleDraft = Pick<TagRule, 'mode' | 'conditions'> &
  ({ tagId: number; tagName?: never } | { tagName: string; tagId?: never }) &
  { newCategoryNames?: string[] }

export async function getRules() {
  return (await api.get<TagRule[]>('/transactions/rules')).data
}

export async function createRule(rule: RuleDraft, applyExisting: boolean) {
  return (await api.post<TagRule>('/transactions/rules', { ...rule, applyExisting })).data
}

export async function updateRule(id: number, rule: RuleDraft, applyExisting: boolean) {
  return (await api.put<TagRule>(`/transactions/rules/${encodeURIComponent(id)}`,
    { ...rule, applyExisting })).data
}

export async function deleteRule(id: number, removeExisting: boolean) {
  await api.delete(`/transactions/rules/${encodeURIComponent(id)}`, { data: { removeExisting } })
}
