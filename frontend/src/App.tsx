import { BrowserRouter, Routes, Route } from 'react-router'
import AppShell from './components/AppShell'
import ProblemListPage from './pages/ProblemListPage'
import ProblemDetailPage from './pages/ProblemDetailPage'
import NotFoundPage from './pages/NotFoundPage'
import './App.css'

function App() {
  return (
    <BrowserRouter>
      <AppShell>
        <div className="page">
          <Routes>
            <Route path="/" element={<ProblemListPage />} />
            <Route path="/problems/:id" element={<ProblemDetailPage />} />
            <Route path="*" element={<NotFoundPage />} />
          </Routes>
        </div>
      </AppShell>
    </BrowserRouter>
  )
}

export default App
