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

/**
 * GET /accounts/me — owned by the Account Ops vertical and not implemented yet.
 * Declared here so the dashboard is already typed against it.
 */
export interface AccountSummary {
  id: number
  customerId: number
  balance: number | string
  hold: boolean
  dailyWithdrawn: number | string
  status: string
}

/** GET /accounts/me/transactions */
export interface StatementTransaction {
  id: number
  type: 'transfer' | 'withdraw' | 'billpay'
  amount: number | string
  status: 'completed' | 'pending' | 'failed'
  createdAt: string
}

/** The single error body the middleware returns for every failure. */
export interface ErrorBody {
  reason: string
  message: string
}
