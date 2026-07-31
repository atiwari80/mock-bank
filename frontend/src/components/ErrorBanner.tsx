import type { ApiError } from '../api/client'

// Shows the server's error exactly as it came back: the stable reason code and
// the human message. Never replace either with generic text.
export default function ErrorBanner({ error }: { error: ApiError | null }) {
  if (!error) return null

  return (
    <div className="error" role="alert">
      <div className="error-reason">{error.reason}</div>
      <div className="error-message">{error.message}</div>
    </div>
  )
}
