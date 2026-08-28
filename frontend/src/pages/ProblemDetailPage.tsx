import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { getProblem } from '../api/problems'
import { submitSolution } from '../api/submissions'
import { ApiError, toErrorMessage } from '../api/client'
import { useAsyncData } from '../hooks/useAsyncData'
import CodeEditor, { SUPPORTED_LANGUAGES, type SupportedLanguage } from '../components/CodeEditor'
import SubmissionResult from '../components/SubmissionResult'
import Panel from '../components/ui/Panel'
import Button from '../components/ui/Button'
import Select from '../components/ui/Select'
import type { SubmissionResponse } from '../types'
import './ProblemDetailPage.css'

function ProblemDetailPage() {
  const { id } = useParams<{ id: string }>()
  const { data: problem, error, loading } = useAsyncData(() => getProblem(id!), [id])

  const [language, setLanguage] = useState<SupportedLanguage>(SUPPORTED_LANGUAGES[0])
  const [code, setCode] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState<SubmissionResponse | null>(null)
  const [submitError, setSubmitError] = useState<string | null>(null)

  async function handleSubmit() {
    if (!id || !code.trim() || submitting) {
      return
    }

    setSubmitting(true)
    setResult(null)
    setSubmitError(null)

    try {
      const response = await submitSolution(id, { language, code })
      setResult(response)
    } catch (err) {
      setSubmitError(toErrorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) {
    return <p className="page-status">불러오는 중...</p>
  }

  if (error) {
    if (error instanceof ApiError && error.isNotFound) {
      return (
        <div className="empty-state">
          <p className="page-status">문제를 찾을 수 없습니다.</p>
          <Link to="/" className="btn btn--ghost">
            목록으로
          </Link>
        </div>
      )
    }
    return (
      <p className="page-status" role="alert">
        문제를 불러오지 못했습니다: {toErrorMessage(error)}
      </p>
    )
  }

  if (!problem) {
    return null
  }

  return (
    <div className="problem-detail">
      <Link to="/" className="btn btn--ghost problem-detail__back">
        목록으로
      </Link>

      <div className="problem-detail-layout">
        <Panel className="problem-detail__panel" title="PROBLEM">
          <h1 className="problem-detail__title">{problem.title}</h1>
          <p className="problem-detail__meta">제한 시간 {problem.timeLimitMs}ms</p>
          <p className="problem-detail__description">{problem.description}</p>

          {problem.sampleTestCases.map((sample, index) => (
            <div className="sample" key={index}>
              <h2 className="sample__title">예제 {index + 1}</h2>
              <div className="sample-io">
                <div>
                  <h3 className="sample-io__label">입력</h3>
                  <pre className="sample-io__block">{sample.input}</pre>
                </div>
                <div>
                  <h3 className="sample-io__label">예상 출력</h3>
                  <pre className="sample-io__block">{sample.expectedOutput}</pre>
                </div>
              </div>
            </div>
          ))}
        </Panel>

        <Panel
          className="problem-detail__panel"
          title="SOLUTION"
          headerRight={
            <Select
              aria-label="언어"
              value={language}
              onChange={(e) => setLanguage(e.target.value as SupportedLanguage)}
              disabled={submitting}
            >
              {SUPPORTED_LANGUAGES.map((lang) => (
                <option key={lang} value={lang}>
                  {lang}
                </option>
              ))}
            </Select>
          }
        >
          <div className="solution-panel">
            <div className="solution-panel__editor">
              <CodeEditor value={code} onChange={setCode} language={language} readOnly={submitting} />
            </div>

            <div className="solution-panel__actions">
              <Button onClick={handleSubmit} disabled={!code.trim() || submitting}>
                {submitting ? '채점 중...' : '제출'}
              </Button>
            </div>

            <SubmissionResult result={result} submitError={submitError} submitting={submitting} />
          </div>
        </Panel>
      </div>
    </div>
  )
}

export default ProblemDetailPage
