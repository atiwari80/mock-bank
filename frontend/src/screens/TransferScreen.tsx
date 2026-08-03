import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, toApiError, type ApiError } from '../api/client'
import ErrorBanner from '../components/ErrorBanner'
import { formatMoney } from '../components/Money'
import type { AccountSummary, Recipient, TransferResult } from '../types'

export default function TransferScreen() {
  const navigate = useNavigate()
  const [account, setAccount] = useState<AccountSummary | null>(null)
  const [recipients, setRecipients] = useState<Recipient[]>([])
  const [recipientId, setRecipientId] = useState('')
  const [amount, setAmount] = useState('')
  const [result, setResult] = useState<TransferResult | null>(null)
  const [error, setError] = useState<ApiError | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    let cancelled = false

    Promise.all([api.get<AccountSummary>('/accounts/me'), api.get<Recipient[]>('/recipients')])
      .then(([summary, list]) => {
        if (cancelled) return
        setAccount(summary)
        setRecipients(list)
        if (list.length > 0) setRecipientId(String(list[0].id))
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
      const sent = await api.post<TransferResult>('/transfer', {
        fromAccount: account.id,
        toRecipient: Number(recipientId),
        amount: Number(amount),
      })
      setResult(sent)
      setAccount({ ...account, balance: sent.balance })
    } catch (err) {
      setError(toApiError(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="page">
      <h1>Transfer</h1>
      <p className="muted">
        <button type="button" onClick={() => navigate('/dashboard')}>
          ← Dashboard
        </button>
      </p>

      <div className="card">
        <h2>Send money</h2>
        {account ? (
          <p className="muted">Available: {formatMoney(account.balance)}</p>
        ) : null}

        <form onSubmit={handleSubmit}>
          <label htmlFor="recipient">To</label>
          <select
            id="recipient"
            value={recipientId}
            onChange={(event) => setRecipientId(event.target.value)}
          >
            {recipients.map((recipient) => (
              <option key={recipient.id} value={recipient.id}>
                {recipient.name}
                {recipient.enrolled ? '' : ' (not enrolled)'}
              </option>
            ))}
          </select>

          <div style={{ marginTop: 12 }}>
            <label htmlFor="amount">Amount</label>
            <input
              id="amount"
              inputMode="decimal"
              value={amount}
              onChange={(event) => setAmount(event.target.value)}
              placeholder="0.00"
            />
          </div>

          <div style={{ marginTop: 16 }}>
            <button className="primary" type="submit" disabled={busy || !account || !recipientId}>
              {busy ? 'Sending…' : 'Send transfer'}
            </button>
          </div>
        </form>
      </div>

      {/* The outcome must be specific: which rule stopped it, or that it is
          waiting on approval rather than simply refused. */}
      {error || result ? (
        <div className="card">
          <h2>Result</h2>
          <ErrorBanner error={error} />
          {result?.status === 'completed' ? (
            <p>✅ Transfer complete — reference #{result.transferRef}</p>
          ) : null}
          {result?.status === 'pending_approval' ? (
            <p>
              ⏳ Pending approval — this transfer is waiting on a reviewer (approval #
              {result.approvalId}). Nothing has left your account yet.
            </p>
          ) : null}
        </div>
      ) : null}
    </div>
  )
}
