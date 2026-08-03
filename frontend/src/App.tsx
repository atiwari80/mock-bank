import type { ReactNode } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { useSession } from './auth/session'
import LoginScreen from './screens/LoginScreen'
import Dashboard from './screens/Dashboard'
import TransferScreen from './screens/TransferScreen'
import WithdrawScreen from './screens/WithdrawScreen'
import BillPayScreen from './screens/BillPayScreen'
import StatementsScreen from './screens/StatementsScreen'

/** Anything behind the login bounces back to it when there is no session. */
function RequireSession({ children }: { children: ReactNode }) {
  const { isLoggedIn } = useSession()
  return isLoggedIn ? <>{children}</> : <Navigate to="/login" replace />
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/login" replace />} />
      <Route path="/login" element={<LoginScreen />} />

      <Route
        path="/dashboard"
        element={
          <RequireSession>
            <Dashboard />
          </RequireSession>
        }
      />

      <Route
        path="/transfer"
        element={
          <RequireSession>
            <TransferScreen />
          </RequireSession>
        }
      />
      <Route
        path="/withdraw"
        element={
          <RequireSession>
            <WithdrawScreen />
          </RequireSession>
        }
      />
      <Route
        path="/billpay"
        element={
          <RequireSession>
            <BillPayScreen />
          </RequireSession>
        }
      />
      <Route
        path="/statements"
        element={
          <RequireSession>
            <StatementsScreen />
          </RequireSession>
        }
      />

      <Route path="*" element={<Navigate to="/login" replace />} />
    </Routes>
  )
}
