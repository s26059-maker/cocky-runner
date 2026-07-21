import { useEffect, useState } from 'react'
import { getProblems } from './api/problems'
import type { ProblemSummary } from './types'

function App() {
  const [problems, setProblems] = useState<ProblemSummary[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    getProblems()
      .then(setProblems)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : 'failed to load problems'))
  }, [])

  if (error) {
    return <p>Error: {error}</p>
  }

  if (!problems) {
    return <p>Loading...</p>
  }

  return (
    <ul>
      {problems.map((problem) => (
        <li key={problem.id}>{problem.title}</li>
      ))}
    </ul>
  )
}

export default App
