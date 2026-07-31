import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, toApiError, type ApiError } from '../api/client'
import { useSession } from '../auth/session'
import ErrorBanner from '../components/ErrorBanner'
import type { AccountSummary } from '../types'

const FEATURES = [
  { label: 'Transfer', path: '/transfer' },
  { label: 'Withdraw', path: '/withdraw' },
  { label: 'Bill Pay', path: '/billpay' },
  { label: 'Statements', path: '/statements' },
]

export default function Dashboard() {
  const { customer, logout } = useSession()
  const navigate = useNavigate()
  const [account, setAccount] = useState<AccountSummary | null>(null)
  const [error, setError] = useState<ApiError | null>(null)

  useEffect(() => {
    let cancelled = false
    // GET /accounts/me is the Account Ops vertical's endpoint; until it exists
    // the dashboard degrades to showing the error rather than hiding it.
    api
      .get<AccountSummary>('/accounts/me')
      .then((data) => {
        if (!cancelled) setAccount(data)
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(toApiError(err))
      })
    return () => {
      cancelled = true
    }
  }, [])

  function handleLogout() {
    logout()
    navigate('/login')
  }

  return (
    <div className="page">
      <h1>Mock Bank</h1>
      <p className="muted">
        Signed in as {customer?.name} (customer {customer?.customerId}){' '}
        <button type="button" onClick={handleLogout}>
          Sign out
        </button>
      </p>

      <div className="card">
        <h2>Available balance</h2>
        <ErrorBanner error={error} />
        <div className="balance">{account ? formatMoney(account.balance) : '—'}</div>
        {account?.hold ? <p className="muted">This account has a hold on it.</p> : null}
      </div>

      <div className="card">
        <h2>What would you like to do?</h2>
        <div className="nav-buttons">
          {FEATURES.map((feature) => (
            <button key={feature.path} type="button" onClick={() => navigate(feature.path)}>
              {feature.label}
            </button>
          ))}
        </div>
      </div>
    </div>
  )
}

function formatMoney(amount: number | string): string {
  const value = Number(amount)
  if (Number.isNaN(value)) return String(amount)
  return value.toLocaleString('en-US', { style: 'currency', currency: 'USD' })
}
