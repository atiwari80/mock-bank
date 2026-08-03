import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, toApiError, type ApiError } from '../api/client'
import ErrorBanner from '../components/ErrorBanner'
import type { StatementTransaction } from '../types'

export default function StatementsScreen() {
  const navigate = useNavigate()
  const [transactions, setTransactions] = useState<StatementTransaction[]>([])
  const [error, setError] = useState<ApiError | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false

    api
      .get<StatementTransaction[]>('/accounts/me/transactions')
      .then((data) => {
        if (!cancelled) setTransactions(data)
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(toApiError(err))
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [])

  return (
    <div className="page">
      <h1>Account statement</h1>

      <div className="card">
        <h2>Transaction history</h2>
        <ErrorBanner error={error} />

        {loading ? <p className="muted">Loading transactions…</p> : null}

        {!loading && !error && transactions.length === 0 ? (
          <p className="muted">No transactions to display.</p>
        ) : null}

        {!loading && !error && transactions.length > 0 ? (
          <table>
            <thead>
              <tr>
                <th scope="col">Date</th>
                <th scope="col">Type</th>
                <th scope="col">Amount</th>
                <th scope="col">Status</th>
              </tr>
            </thead>
            <tbody>
              {transactions.map((transaction) => (
                <tr key={transaction.id}>
                  <td>{formatDate(transaction.createdAt)}</td>
                  <td>{formatType(transaction.type)}</td>
                  <td>{formatMoney(transaction.amount)}</td>
                  <td>{capitalize(transaction.status)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : null}
      </div>

      <button type="button" onClick={() => navigate('/dashboard')}>
        Back to dashboard
      </button>
    </div>
  )
}

function formatMoney(amount: number | string): string {
  const value = Number(amount)
  if (Number.isNaN(value)) return String(amount)
  return value.toLocaleString('en-US', { style: 'currency', currency: 'USD' })
}

function formatDate(createdAt: string): string {
  const value = new Date(createdAt)
  if (Number.isNaN(value.getTime())) return createdAt
  return value.toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  })
}

function formatType(type: StatementTransaction['type']): string {
  if (type === 'billpay') return 'Bill Pay'
  return capitalize(type)
}

function capitalize(value: string): string {
  return value.charAt(0).toUpperCase() + value.slice(1)
}
