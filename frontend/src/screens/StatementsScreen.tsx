import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, toApiError, type ApiError } from '../api/client'
import ErrorBanner from '../components/ErrorBanner'
import { capitalize, formatDate, formatMoney, formatType } from '../components/Money'
import type { AccountSummary, Paged, StatementTransaction } from '../types'

const PAGE_SIZE = 10

export default function StatementsScreen() {
  const navigate = useNavigate()
  const [account, setAccount] = useState<AccountSummary | null>(null)
  const [statement, setStatement] = useState<Paged<StatementTransaction> | null>(null)
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [page, setPage] = useState(0)
  const [error, setError] = useState<ApiError | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false

    api
      .get<AccountSummary>('/accounts/me')
      .then((summary) => {
        if (!cancelled) setAccount(summary)
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setError(toApiError(err))
          setLoading(false)
        }
      })

    return () => {
      cancelled = true
    }
  }, [])

  const loadPage = useCallback(
    async (accountId: number, wanted: number) => {
      setLoading(true)
      setError(null)
      try {
        const query = new URLSearchParams({ page: String(wanted), size: String(PAGE_SIZE) })
        if (from !== '') query.set('from', from)
        if (to !== '') query.set('to', to)

        setStatement(
          await api.get<Paged<StatementTransaction>>(`/transactions/${accountId}?${query.toString()}`),
        )
        setPage(wanted)
      } catch (err) {
        setError(toApiError(err))
      } finally {
        setLoading(false)
      }
    },
    [from, to],
  )

  useEffect(() => {
    if (account) void loadPage(account.id, 0)
    // Only re-run when the account arrives; filtering is driven by the button.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [account])

  const transactions = statement?.items ?? []
  const totalPages = statement?.totalPages ?? 0

  return (
    <div className="page">
      <h1>Account statement</h1>
      <p className="muted">
        <button type="button" onClick={() => navigate('/dashboard')}>
          ← Dashboard
        </button>
      </p>

      {account ? (
        <div className="card">
          <h2>Balance</h2>
          <div className="balance">{formatMoney(account.balance)}</div>
          <p className="muted">
            Account {account.id} · {account.status}
            {account.hold ? ' · hold' : ''}
          </p>
        </div>
      ) : null}

      <div className="card">
        <h2>Transaction history</h2>
        <ErrorBanner error={error} />

        <div style={{ display: 'flex', gap: 8, alignItems: 'flex-end', flexWrap: 'wrap' }}>
          <div>
            <label htmlFor="from">From</label>
            <input id="from" type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
          </div>
          <div>
            <label htmlFor="to">To</label>
            <input id="to" type="date" value={to} onChange={(e) => setTo(e.target.value)} />
          </div>
          <button type="button" onClick={() => account && loadPage(account.id, 0)}>
            Filter
          </button>
          <button
            type="button"
            onClick={() => {
              setFrom('')
              setTo('')
            }}
          >
            Clear
          </button>
        </div>

        <div style={{ marginTop: 16 }}>
          {loading ? <p className="muted">Loading transactions…</p> : null}

          {!loading && !error && transactions.length === 0 ? (
            <p className="muted">No transactions to display.</p>
          ) : null}

          {!loading && transactions.length > 0 ? (
            <>
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

              <div style={{ marginTop: 12, display: 'flex', gap: 8, alignItems: 'center' }}>
                <button
                  type="button"
                  disabled={page <= 0}
                  onClick={() => account && loadPage(account.id, page - 1)}
                >
                  ‹ Prev
                </button>
                <span className="muted">
                  Page {page + 1} of {totalPages} · {statement?.totalItems} transactions
                </span>
                <button
                  type="button"
                  disabled={page + 1 >= totalPages}
                  onClick={() => account && loadPage(account.id, page + 1)}
                >
                  Next ›
                </button>
              </div>
            </>
          ) : null}
        </div>
      </div>
    </div>
  )
}
