// Shapes the middleware returns. Kept in one place so feature screens extend
// them rather than re-declaring their own.

/** POST /login */
export interface LoginResponse {
  customerId: number
  name: string
}

/** GET /whoami */
export interface WhoAmIResponse {
  customerId: number
  name: string
  status: string
}

/** GET /accounts/me and GET /account/{id} */
export interface AccountSummary {
  id: number
  customerId: number
  balance: number | string
  hold: boolean
  dailyWithdrawn: number | string
  status: string
}

/** GET /accounts/me/transactions and the items of GET /transactions/{id} */
export interface StatementTransaction {
  id: number
  type: 'transfer' | 'withdraw' | 'billpay'
  amount: number | string
  status: 'completed' | 'pending' | 'failed'
  createdAt: string
}

/** Envelope for any paged list. */
export interface Paged<T> {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

/** GET /recipients */
export interface Recipient {
  id: number
  name: string
  enrolled: boolean
}

/** POST /transfer — status is 'completed' or 'pending_approval' */
export interface TransferResult {
  transferId: number
  status: string
  approvalId: number | null
  transferRef: string
  balance: number | string
}

/** POST /withdraw */
export interface WithdrawResult {
  transactionId: number
  balance: number | string
  dailyWithdrawn: number | string
}

/** POST /schedule-payment, GET /scheduled-payments */
export interface ScheduledPayment {
  id: number
  payee: string
  amount: number | string
  fireDate: string
  status: 'scheduled' | 'pending' | 'paid' | 'failed'
}

/** POST /scheduled-payments/run */
export interface RunResult {
  asOf: string
  queued: number
  paid: number
  failed: number
}

/** POST /credit-card/apply */
export interface CreditCardResult {
  applicationId: number
  status: 'approved' | 'declined'
  approvedLimit: number | string | null
  bureauScore: number
}

/** The single error body the middleware returns for every failure. */
export interface ErrorBody {
  reason: string
  message: string
}
