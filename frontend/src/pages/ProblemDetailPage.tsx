import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { getProblem } from '../api/problems'
import { submitSolution } from '../api/submissions'
import { ApiError, toErrorMessage } from '../api/client'
import { useAsyncData } from '../hooks/useAsyncData'
import CodeEditor, { SUPPORTED_LANGUAGES, type SupportedLanguage } from '../components/CodeEditor'
import SubmissionResult from '../components/SubmissionResult'
import type { SubmissionResponse } from '../types'

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
    return <p>불러오는 중...</p>
  }

  if (error) {
    if (error instanceof ApiError && error.isNotFound) {
      return (
        <>
          <p>문제를 찾을 수 없습니다.</p>
          <Link to="/">목록으로</Link>
        </>
      )
    }
    return <p role="alert">문제를 불러오지 못했습니다: {toErrorMessage(error)}</p>
  }

  if (!problem) {
    return null
  }

  return (
    <div className="problem-detail">
      <Link to="/">목록으로</Link>

      <div className="problem-detail-layout">
        <section className="problem-statement">
          <h1>{problem.title}</h1>
          <p>제한 시간: {problem.timeLimitMs}ms</p>
          <p className="description">{problem.description}</p>

          {problem.sampleTestCases.map((sample, index) => (
            <div className="sample" key={index}>
              <h2>예제 {index + 1}</h2>
              <div className="sample-io">
                <div>
                  <h3>입력</h3>
                  <pre>{sample.input}</pre>
                </div>
                <div>
                  <h3>예상 출력</h3>
                  <pre>{sample.expectedOutput}</pre>
                </div>
              </div>
            </div>
          ))}
        </section>

        <section className="submit-panel">
          <h2>제출</h2>

          <div className="submit-row">
            <label htmlFor="language-select">언어</label>
            <select
              id="language-select"
              value={language}
              onChange={(e) => setLanguage(e.target.value as SupportedLanguage)}
            >
              {SUPPORTED_LANGUAGES.map((lang) => (
                <option key={lang} value={lang}>
                  {lang}
                </option>
              ))}
            </select>
          </div>

          <div className="code-editor">
            <CodeEditor value={code} onChange={setCode} language={language} readOnly={submitting} />
          </div>

          <div className="submit-actions">
            <button type="button" onClick={handleSubmit} disabled={!code.trim() || submitting}>
              {submitting ? '채점 중...' : '제출'}
            </button>
          </div>

          <SubmissionResult result={result} submitError={submitError} />
        </section>
      </div>
    </div>
  )
}

export default ProblemDetailPage
