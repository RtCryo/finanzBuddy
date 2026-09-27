import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { api } from '../api/client'
import { getErrorMessage } from '../api/errors'
import { setSession } from '../auth/storage'
import { AuthLayout } from '../components/AuthLayout'

type LoginResponse = {
  token: string
}

export function LoginPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    setIsSubmitting(true)

    try {
      const response = await api.post<LoginResponse>('/auth/login', { email, password })
      if (!response.data.token) throw new Error('The server did not return an access token.')
      setSession(response.data.token, email)
      const destination = (location.state as { from?: { pathname?: string } } | null)?.from?.pathname
      const requestedPath = new URLSearchParams(location.search).get('next')
      const returnPath = destination ?? requestedPath
      navigate(returnPath?.startsWith('/documents') ? returnPath : '/documents', { replace: true })
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'We could not sign you in. Please check your details.'))
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <AuthLayout
      eyebrow="WELCOME BACK"
      title="Sign in to your account"
      description="Pick up right where you left off."
      footer={<>Don’t have an account? <Link to="/register">Create account</Link></>}
    >
      <form className="form-stack" onSubmit={handleSubmit}>
        {error && <div className="notice notice-error" role="alert">{error}</div>}
        <label className="field">
          <span>Email address</span>
          <input
            autoComplete="email"
            type="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            placeholder="alex@example.com"
            required
          />
        </label>
        <label className="field">
          <span>Password</span>
          <input
            autoComplete="current-password"
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            placeholder="Enter your password"
            required
          />
        </label>
        <button className="button button-primary auth-submit" type="submit" disabled={isSubmitting}>
          {isSubmitting ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
    </AuthLayout>
  )
}
