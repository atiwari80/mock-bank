import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, toApiError, type ApiError } from '../api/client'
import ErrorBanner from '../components/ErrorBanner'
import { formatMoney } from '../components/Money'
import type { AccountSummary, WithdrawResult } from '../types'

export default function WithdrawScreen() {
  const navigate = useNavigate()
  const [account, setAccount] = useState<AccountSummary | null>(null)
  const [amount, setAmount] = useState('')
  const [result, setResult] = useState<WithdrawResult | null>(null)
  const [error, setError] = useState<ApiError | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    let cancelled = false

    api
      .get<AccountSummary>('/accounts/me')
      .then((summary) => {
        if (!cancelled) setAccount(summary)
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(toApiError(err))
      })

    return () => {
      cancelled = true
    }
  }, [])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!account) return

    setError(null)
    setResult(null)
    setBusy(true)
    try {
      const taken = await api.post<WithdrawResult>('/withdraw', {
        account: account.id,
        amount: Number(amount),
      })
      setResult(taken)
      setAccount({ ...account, balance: taken.balance, dailyWithdrawn: taken.dailyWithdrawn })
    } catch (err) {
      setError(toApiError(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="page">
      <h1>Withdraw</h1>
      <p className="muted">
        <button type="button" onClick={() => navigate('/dashboard')}>
          ← Dashboard
        </button>
      </p>

      <div className="card">
        <h2>Take cash out</h2>
        {account ? (
          <p className="muted">
            Available: {formatMoney(account.balance)} · withdrawn today:{' '}
            {formatMoney(account.dailyWithdrawn)}
          </p>
        ) : null}

        <form onSubmit={handleSubmit}>
          <label htmlFor="amount">Amount</label>
          <input
            id="amount"
            inputMode="decimal"
            value={amount}
            onChange={(event) => setAmount(event.target.value)}
            placeholder="0.00"
          />
          <div style={{ marginTop: 16 }}>
            <button className="primary" type="submit" disabled={busy || !account}>
              {busy ? 'Withdrawing…' : 'Withdraw'}
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
              ✅ Withdrew successfully — new balance {formatMoney(result.balance)}, total withdrawn
              today {formatMoney(result.dailyWithdrawn)}.
            </p>
          ) : null}
        </div>
      ) : null}
    </div>
  )
}
