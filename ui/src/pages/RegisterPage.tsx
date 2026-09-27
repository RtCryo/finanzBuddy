import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import { getErrorMessage } from '../api/errors'
import { AuthLayout } from '../components/AuthLayout'

export function RegisterPage() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [error, setError] = useState('')
  const [success, setSuccess] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')

    if (password !== confirmPassword) {
      setError('Your passwords do not match.')
      return
    }

    setIsSubmitting(true)
    try {
      await api.post('/auth/register', { email, password })
      setSuccess(true)
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'We could not create your account. Please try again.'))
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <AuthLayout
      eyebrow="GET STARTED"
      title="Create your account"
      description="One secure place for all your financial documents."
      footer={<>Already have an account? <Link to="/login">Sign in</Link></>}
    >
      {success ? (
        <div className="notice notice-success" role="status">
          <strong>Your account is ready.</strong>
          <span>Sign in to start managing your financial documents.</span>
          <Link className="button button-primary" to="/login">Continue to sign in</Link>
        </div>
      ) : (
        <form className="form-stack" onSubmit={handleSubmit}>
          {error && <div className="notice notice-error" role="alert">{error}</div>}
          <label className="field">
            <span>Email address</span>
            <input
              autoComplete="email"
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              placeholder="you@example.com"
              required
            />
          </label>
          <label className="field">
            <span>Password</span>
            <input
              autoComplete="new-password"
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              placeholder="Create a password"
              minLength={6}
              maxLength={72}
              required
            />
            <small>Use at least 6 characters.</small>
          </label>
          <label className="field">
            <span>Confirm password</span>
            <input
              autoComplete="new-password"
              type="password"
              value={confirmPassword}
              onChange={(event) => setConfirmPassword(event.target.value)}
              placeholder="Re-enter your password"
              minLength={6}
              maxLength={72}
              required
            />
          </label>
          <button className="button button-primary auth-submit" type="submit" disabled={isSubmitting}>
            {isSubmitting ? 'Creating account…' : 'Create account'}
          </button>
        </form>
      )}
    </AuthLayout>
  )
}
