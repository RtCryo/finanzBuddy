import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'

type AuthLayoutProps = {
  eyebrow: string
  title: string
  description: string
  children: ReactNode
  footer: ReactNode
}

export function AuthLayout({ eyebrow, title, description, children, footer }: AuthLayoutProps) {
  return (
    <main className="auth-page">
      <div className="auth-card">
        <section className="auth-form-panel">
          <Link className="brand auth-brand" to="/login" aria-label="finanzBuddy home">
            <span className="brand-mark" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none"><path d="m6.5 12.5 3.7 3.7 7.4-8" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" /></svg>
            </span>
            <span className="brand-name">finanz<span>Buddy</span></span>
          </Link>
          <div className="auth-form-content">
            <p className="auth-eyebrow">{eyebrow}</p>
            <h1>{title}</h1>
            <p className="auth-description">{description}</p>
            {children}
            <div className="auth-footer">{footer}</div>
          </div>
          <p className="auth-privacy">Your financial data is secure and only accessible to you.</p>
        </section>

        <aside className="auth-story" aria-label="About finanzBuddy">
          <div className="story-orbit" aria-hidden="true">
            <div className="story-card">
              <div className="story-card-heading">{eyebrow === 'GET STARTED' ? 'Your documents' : 'Documents processed'}</div>
              {eyebrow === 'GET STARTED' ? (
                <>
                  <div className="story-document-icon" aria-hidden="true">▤</div>
                  <strong>All in one secure place</strong>
                  <div className="story-card-row"><span className="story-check">✓</span> Upload statements</div>
                  <div className="story-card-row"><span className="story-check">✓</span> View extracted transactions</div>
                </>
              ) : (
                <>
                  <strong className="story-count">12</strong>
                  <div className="story-progress"><span /></div>
                  <div className="story-card-row"><span className="story-check">✓</span> Everything is up to date</div>
                </>
              )}
            </div>
          </div>
          <div className="story-copy">
            <h2>{eyebrow === 'GET STARTED' ? <>A little more clarity<br /><span>starts here.</span></> : <>Your finances,<br /><span>finally in order.</span></>}</h2>
            <p>{eyebrow === 'GET STARTED' ? 'Bring your statements together and let us organize the details.' : 'A clearer view of your documents and transactions.'}</p>
          </div>
          <p className="story-caption">Simple, secure financial document management.</p>
        </aside>
      </div>
    </main>
  )
}
