import { useCallback, useEffect, useRef, useState } from 'react'
import { getDashboard } from '../api/dashboard'
import { getErrorMessage } from '../api/errors'
import { AppShell } from '../components/AppShell'
import { PageHeading } from '../components/PageHeading'
import type { DashboardMetrics } from '../types/dashboard'

const metricCards: { key: keyof DashboardMetrics; label: string; icon: string; tone: string }[] = [
  { key: 'totalDocuments', label: 'Total documents', icon: '▤', tone: 'summary-icon-green' },
  { key: 'processedDocuments', label: 'Processed documents', icon: '✓', tone: 'summary-icon-green' },
  { key: 'inProgressDocuments', label: 'Documents in progress', icon: '◷', tone: 'summary-icon-amber' },
  { key: 'savedTransactions', label: 'Saved transactions', icon: '↔', tone: 'summary-icon-blue' },
  { key: 'uniqueCategories', label: 'Unique categories', icon: '◈', tone: 'summary-icon-purple' },
  { key: 'uniqueCounterparties', label: 'Unique counterparties', icon: '◎', tone: 'summary-icon-teal' },
]

export function DashboardPage() {
  const [metrics, setMetrics] = useState<DashboardMetrics | null>(null)
  const [error, setError] = useState('')
  const [isLoading, setIsLoading] = useState(true)
  const requestId = useRef(0)

  const refresh = useCallback(async () => {
    const current = ++requestId.current
    try {
      const result = await getDashboard()
      if (current !== requestId.current) return
      setMetrics(result)
      setError('')
    } catch (requestError) {
      if (current !== requestId.current) return
      setError(getErrorMessage(requestError, 'We could not load your dashboard.'))
    } finally {
      if (current === requestId.current) setIsLoading(false)
    }
  }, [])

  useEffect(() => {
    const initialLoad = window.setTimeout(() => void refresh(), 0)
    const interval = window.setInterval(() => void refresh(), 5000)
    return () => {
      window.clearTimeout(initialLoad)
      window.clearInterval(interval)
      requestId.current += 1
    }
  }, [refresh])

  return (
    <AppShell>
      <PageHeading title="Dashboard" description="A quick overview of your documents and extracted transactions." />

      {error && <div className="notice notice-error list-notice" role="alert">{error}</div>}
      {isLoading && <div className="list-state" role="status">Loading your dashboard…</div>}
      {metrics && (
        <section className="summary-grid dashboard-summary-grid" aria-label="Dashboard summary">
          {metricCards.map(({ key, label, icon, tone }) => (
            <article className="surface summary-card" key={key}>
              <span>{label}</span>
              <strong>{metrics[key]}</strong>
              <span className={`summary-icon ${tone}`} aria-hidden="true">{icon}</span>
            </article>
          ))}
        </section>
      )}
      <p className="polling-note" aria-live="polite">Dashboard metrics refresh automatically every 5 seconds.</p>
    </AppShell>
  )
}
