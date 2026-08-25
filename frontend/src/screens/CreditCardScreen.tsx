import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, toApiError, type ApiError } from '../api/client'
import ErrorBanner from '../components/ErrorBanner'
import { formatMoney } from '../components/Money'
import { useSession } from '../auth/session'
import type { CreditCardResult } from '../types'

export default function CreditCardScreen() {
  const navigate = useNavigate()
  const { customer } = useSession()
  const [ssn, setSsn] = useState('')
  const [limit, setLimit] = useState('')
  const [result, setResult] = useState<CreditCardResult | null>(null)
  const [error, setError] = useState<ApiError | null>(null)
  const [busy, setBusy] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    setError(null)
    setResult(null)
    setBusy(true)
    try {
      const data = await api.post<CreditCardResult>('/credit-card/apply', {
        customerId: customer?.customerId,
        ssn,
        requestedLimit: Number(limit),
      })
      setResult(data)
    } catch (err) {
      setError(toApiError(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="page">
      <h1>Credit Card</h1>
      <p className="muted">
        <button type="button" onClick={() => navigate('/dashboard')}>
          ← Dashboard
        </button>
      </p>

      <div className="card">
        <h2>Apply for a card</h2>
        <p className="muted">
          Enter your Social Security Number and the credit limit you would like. We will check
          your bureau score and respond immediately.
        </p>

        <form onSubmit={handleSubmit}>
          <label htmlFor="ssn">Social Security Number (9 digits, no dashes)</label>
          <input
            id="ssn"
            type="text"
            inputMode="numeric"
            maxLength={9}
            value={ssn}
            onChange={(e) => setSsn(e.target.value)}
            placeholder="123456789"
          />

          <label htmlFor="limit" style={{ marginTop: 16 }}>
            Requested credit limit ($)
          </label>
          <input
            id="limit"
            inputMode="decimal"
            value={limit}
            onChange={(e) => setLimit(e.target.value)}
            placeholder="5000.00"
          />

          <div style={{ marginTop: 16 }}>
            <button className="primary" type="submit" disabled={busy}>
              {busy ? 'Checking…' : 'Apply'}
            </button>
          </div>
        </form>
      </div>

      {error || result ? (
        <div className="card">
          <h2>Result</h2>
          <ErrorBanner error={error} />
          {result ? (
            <p>
              ✅ Application <strong>{result.status}</strong>
              {/* A decline comes back as CREDIT_DECLINE, so a result always
                  carries a limit — but the column is nullable, so guard it. */}
              {result.approvedLimit === null
                ? '.'
                : ` — assigned limit ${formatMoney(result.approvedLimit)}.`}
            </p>
          ) : null}
        </div>
      ) : null}
    </div>
  )
}
