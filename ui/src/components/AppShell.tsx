import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom'
import type { ReactNode } from 'react'
import { clearSession, getEmail } from '../auth/storage'

type AppShellProps = {
  children: ReactNode
}

function Brand() {
  return (
    <Link className="brand" to="/documents" aria-label="finanzBuddy home">
      <span className="brand-mark" aria-hidden="true">
        <svg viewBox="0 0 24 24" fill="none">
          <path d="m6.5 12.5 3.7 3.7 7.4-8" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      </span>
      <span className="brand-name">finanz<span>Buddy</span></span>
    </Link>
  )
}

function WorkspaceIcon({ upload = false }: { upload?: boolean }) {
  return upload ? (
    <svg viewBox="0 0 20 20" fill="none" aria-hidden="true">
      <rect x="2.5" y="2.5" width="15" height="15" rx="4" stroke="currentColor" strokeWidth="1.6" />
      <path d="M10 5.5v9M5.5 10h9" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
    </svg>
  ) : (
    <svg viewBox="0 0 20 20" fill="none" aria-hidden="true">
      <rect x="2.5" y="2" width="15" height="16" rx="3.5" stroke="currentColor" strokeWidth="1.6" />
      <path d="M6 6.5h8M6 10h8M6 13.5h5" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
    </svg>
  )
}

function pageLabel(pathname: string) {
  if (pathname === '/documents/upload') return 'Upload'
  if (pathname.startsWith('/documents/')) return 'Document details'
  return 'Documents'
}

export function AppShell({ children }: AppShellProps) {
  const location = useLocation()
  const navigate = useNavigate()
  const email = getEmail() || 'Your account'
  const initials = email === 'Your account'
    ? 'FB'
    : email.split('@')[0].split(/[._-]/).filter(Boolean).slice(0, 2).map((part) => part[0]).join('').toUpperCase()

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <Brand />
        <p className="nav-caption">Workspace</p>
        <nav className="workspace-nav" aria-label="Workspace">
          <NavLink to="/documents" end className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}>
            <WorkspaceIcon />
            <span>Documents</span>
          </NavLink>
          <NavLink to="/documents/upload" className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}>
            <WorkspaceIcon upload />
            <span>Upload a file</span>
          </NavLink>
        </nav>
        <p className="sidebar-note">Your financial data is secure and only accessible to you.</p>
      </aside>

      <div className="workspace">
        <header className="topbar">
          <div className="breadcrumbs" aria-label="Breadcrumb">
            <span>Workspace</span>
            <svg viewBox="0 0 16 16" aria-hidden="true"><path d="m6 3 5 5-5 5" /></svg>
            <span className="breadcrumb-current">{pageLabel(location.pathname)}</span>
          </div>
          <div className="profile" aria-label={`Signed in as ${email}`}>
            <span className="profile-avatar" aria-hidden="true">{initials || 'FB'}</span>
            <span className="profile-email">{email}</span>
            <button
              className="sign-out"
              type="button"
              onClick={() => {
                clearSession()
                navigate('/login', { replace: true })
              }}
            >
              Sign out
            </button>
          </div>
        </header>
        <main className="main-content">{children}</main>
        <footer className="workspace-footer">Your financial data is secure and only accessible to you.</footer>
      </div>
    </div>
  )
}
