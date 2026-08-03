import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, toApiError, type ApiError } from '../api/client'
import ErrorBanner from '../components/ErrorBanner'
import { capitalize, formatMoney } from '../components/Money'
import type { RunResult, ScheduledPayment } from '../types'

export default function BillPayScreen() {
  const navigate = useNavigate()
  const [payments, setPayments] = useState<ScheduledPayment[]>([])
  const [payee, setPayee] = useState('')
  const [amount, setAmount] = useState('')
  const [date, setDate] = useState('')
  const [asOf, setAsOf] = useState('')
  const [error, setError] = useState<ApiError | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const load = useCallback(async () => {
    try {
      setPayments(await api.get<ScheduledPayment[]>('/scheduled-payments'))
    } catch (err) {
      setError(toApiError(err))
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  async function handleSchedule(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setNotice(null)
    setBusy(true)
    try {
      const created = await api.post<ScheduledPayment>('/schedule-payment', {
        payee,
        amount: Number(amount),
        date,
      })
      setNotice(`Scheduled ${formatMoney(created.amount)} to ${created.payee} for ${created.fireDate}.`)
      setPayee('')
      setAmount('')
      setDate('')
      await load()
    } catch (err) {
      setError(toApiError(err))
    } finally {
      setBusy(false)
    }
  }

  // Cancel is offered on every row on purpose: attempting it on a payment that
  // has already moved is how you see the specific NOT_CANCELLABLE reason.
  async function handleCancel(paymentId: number) {
    setError(null)
    setNotice(null)
    try {
      await api.post(`/scheduled-payments/${paymentId}/cancel`)
      setNotice(`Payment ${paymentId} cancelled.`)
      await load()
    } catch (err) {
      setError(toApiError(err))
    }
  }

  async function handleRun() {
    setError(null)
    setNotice(null)
    try {
      const result = await api.post<RunResult>('/scheduled-payments/run', {
        asOfDate: asOf === '' ? null : asOf,
      })
      setNotice(
        `Ran as at ${result.asOf}: ${result.queued} queued, ${result.paid} paid, ${result.failed} failed.`,
      )
      await load()
    } catch (err) {
      setError(toApiError(err))
    }
  }

  return (
    <div className="page">
      <h1>Bill Pay</h1>
      <p className="muted">
        <button type="button" onClick={() => navigate('/dashboard')}>
          ← Dashboard
        </button>
      </p>

      <div className="card">
        <h2>Schedule a payment</h2>
        <ErrorBanner error={error} />
        {notice ? <p className="muted">{notice}</p> : null}

        <form onSubmit={handleSchedule}>
          <label htmlFor="payee">Payee</label>
          <input id="payee" value={payee} onChange={(event) => setPayee(event.target.value)} />

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

          <div style={{ marginTop: 12 }}>
            <label htmlFor="date">Date</label>
            <input
              id="date"
              type="date"
              value={date}
              onChange={(event) => setDate(event.target.value)}
            />
          </div>

          <div style={{ marginTop: 16 }}>
            <button className="primary" type="submit" disabled={busy}>
              {busy ? 'Scheduling…' : 'Schedule payment'}
            </button>
          </div>
        </form>
      </div>

      <div className="card">
        <h2>Scheduled</h2>
        {payments.length === 0 ? (
          <p className="muted">Nothing scheduled.</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th scope="col">Payee</th>
                <th scope="col">Amount</th>
                <th scope="col">Date</th>
                <th scope="col">Status</th>
                <th scope="col" />
              </tr>
            </thead>
            <tbody>
              {payments.map((payment) => (
                <tr key={payment.id}>
                  <td>{payment.payee}</td>
                  <td>{formatMoney(payment.amount)}</td>
                  <td>{payment.fireDate}</td>
                  <td>{capitalize(payment.status)}</td>
                  <td>
                    <button type="button" onClick={() => handleCancel(payment.id)}>
                      Cancel
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <div className="card">
        <h2>Run due payments</h2>
        <p className="muted">
          Demo control. Payments do not fire on a timer — this advances everything due as at the
          date below by one step (scheduled → pending → paid or failed).
        </p>
        <label htmlFor="asOf">As at</label>
        <input id="asOf" type="date" value={asOf} onChange={(event) => setAsOf(event.target.value)} />
        <div style={{ marginTop: 12 }}>
          <button type="button" onClick={handleRun}>
            Run
          </button>
        </div>
      </div>
    </div>
  )
}
