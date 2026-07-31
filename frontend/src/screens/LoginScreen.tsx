import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, toApiError, type ApiError } from '../api/client'
import { useSession } from '../auth/session'
import ErrorBanner from '../components/ErrorBanner'
import type { LoginResponse } from '../types'

const SEED_CUSTOMERS = [
  { id: 1, label: '1 — Alice Nguyen' },
  { id: 2, label: '2 — Brian Kowalski' },
  { id: 3, label: '3 — Chloe Ramos' },
  { id: 4, label: '4 — Dev Patel' },
]

export default function LoginScreen() {
  const [customerId, setCustomerId] = useState('1')
  const [error, setError] = useState<ApiError | null>(null)
  const [busy, setBusy] = useState(false)
  const { login } = useSession()
  const navigate = useNavigate()

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setBusy(true)
    try {
      const parsed = Number(customerId)
      const session = await api.post<LoginResponse>('/login', {
        customerId: Number.isNaN(parsed) ? customerId : parsed,
      })
      login(session)
      navigate('/dashboard')
    } catch (err) {
      setError(toApiError(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="page">
      <h1>Mock Bank</h1>
      <div className="card">
        <h2>Sign in</h2>
        <p className="muted">No password. Pick or type a customer id.</p>
        <ErrorBanner error={error} />
        <form onSubmit={handleSubmit}>
          <label htmlFor="customerId">Customer id</label>
          <input
            id="customerId"
            value={customerId}
            onChange={(event) => setCustomerId(event.target.value)}
          />
          <div className="nav-buttons" style={{ marginTop: 12 }}>
            {SEED_CUSTOMERS.map((seed) => (
              <button key={seed.id} type="button" onClick={() => setCustomerId(String(seed.id))}>
                {seed.label}
              </button>
            ))}
          </div>
          <div style={{ marginTop: 16 }}>
            <button className="primary" type="submit" disabled={busy}>
              {busy ? 'Signing in…' : 'Sign in'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
