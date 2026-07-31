import { useNavigate } from 'react-router-dom'

interface PlaceholderScreenProps {
  title: string
  owner: string
}

// Stand-in for a feature screen that a vertical owner has not built yet.
export default function PlaceholderScreen({ title, owner }: PlaceholderScreenProps) {
  const navigate = useNavigate()

  return (
    <div className="page">
      <h1>{title}</h1>
      <div className="card">
        <p>Not built yet — this screen belongs to the {owner} vertical.</p>
        <button type="button" onClick={() => navigate('/dashboard')}>
          Back to dashboard
        </button>
      </div>
    </div>
  )
}
