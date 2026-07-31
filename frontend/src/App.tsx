import type { ReactNode } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { useSession } from './auth/session'
import LoginScreen from './screens/LoginScreen'
import Dashboard from './screens/Dashboard'
import PlaceholderScreen from './screens/PlaceholderScreen'

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

      {/* Feature routes — each vertical replaces its own placeholder. */}
      <Route
        path="/transfer"
        element={
          <RequireSession>
            <PlaceholderScreen title="Transfer" owner="Money Out" />
          </RequireSession>
        }
      />
      <Route
        path="/withdraw"
        element={
          <RequireSession>
            <PlaceholderScreen title="Withdraw" owner="Account Ops" />
          </RequireSession>
        }
      />
      <Route
        path="/billpay"
        element={
          <RequireSession>
            <PlaceholderScreen title="Bill Pay" owner="Account Ops" />
          </RequireSession>
        }
      />
      <Route
        path="/statements"
        element={
          <RequireSession>
            <PlaceholderScreen title="Statements" owner="Account Ops" />
          </RequireSession>
        }
      />

      <Route path="*" element={<Navigate to="/login" replace />} />
    </Routes>
  )
}
