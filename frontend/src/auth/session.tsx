import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'
import { setSessionCustomerId } from '../api/client'
import type { LoginResponse } from '../types'

// The whole session: who is logged in, in React state only. Deliberately not
// persisted — no localStorage, no sessionStorage, no cookie. Reload = logged out.

export interface SessionValue {
  customer: LoginResponse | null
  customerId: number | null
  isLoggedIn: boolean
  /** The header every authenticated request carries. */
  authHeader: Record<string, string>
  login: (loggedIn: LoginResponse) => void
  logout: () => void
}

const SessionContext = createContext<SessionValue | null>(null)

export function SessionProvider({ children }: { children: ReactNode }) {
  const [customer, setCustomer] = useState<LoginResponse | null>(null)

  const login = useCallback((loggedIn: LoginResponse) => {
    setCustomer(loggedIn)
    setSessionCustomerId(loggedIn.customerId)
  }, [])

  const logout = useCallback(() => {
    setCustomer(null)
    setSessionCustomerId(null)
  }, [])

  const value = useMemo<SessionValue>(() => {
    const authHeader: Record<string, string> = customer
      ? { 'X-Customer-Id': String(customer.customerId) }
      : {}

    return {
      customer,
      customerId: customer?.customerId ?? null,
      isLoggedIn: customer !== null,
      authHeader,
      login,
      logout,
    }
  }, [customer, login, logout])

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>
}

export function useSession(): SessionValue {
  const context = useContext(SessionContext)
  if (!context) {
    throw new Error('useSession must be used inside a <SessionProvider>')
  }
  return context
}
