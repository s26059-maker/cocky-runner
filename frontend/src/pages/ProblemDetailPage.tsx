import { useCallback, useRef, useState } from 'react'
import type { ChangeEvent } from 'react'
import { Link, useParams } from 'react-router'
import { getProblem } from '../api/problems'
import { submitSolution } from '../api/submissions'
import { ApiError, toErrorMessage } from '../api/client'
import { useAsyncData } from '../hooks/useAsyncData'
import { useProblemDraft } from '../hooks/useProblemDraft'
import CodeEditor, {
  LANGUAGE_TEMPLATES,
  SUPPORTED_LANGUAGES,
  type SupportedLanguage,
} from '../components/CodeEditor'
import SubmissionResult from '../components/SubmissionResult'
import Panel from '../components/ui/Panel'
import Button from '../components/ui/Button'
import Select from '../components/ui/Select'
import { MAX_SOURCE_LENGTH, type SubmissionResponse } from '../types'
import './ProblemDetailPage.css'

const LANGUAGE_LABELS: Record<SupportedLanguage, string> = {
  C: 'C',
  PYTHON: 'Python',
}

const IS_MAC = /Mac|iPhone|iPad|iPod/.test(navigator.userAgent)
const SUBMIT_SHORTCUT_LABEL = IS_MAC ? '⌘Enter' : 'Ctrl+Enter'

function ProblemDetailPage() {
  const { id } = useParams<{ id: string }>()
  const { data: problem, error, loading } = useAsyncData(() => getProblem(id!), [id])

  const { language, setLanguage, code, setCode } = useProblemDraft(
    id,
    SUPPORTED_LANGUAGES,
    SUPPORTED_LANGUAGES[0],
    LANGUAGE_TEMPLATES,
  )
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState<SubmissionResponse | null>(null)
  const [submitError, setSubmitError] = useState<string | null>(null)

  const isBlank = !code.trim()
  const isTooLong = code.length > MAX_SOURCE_LENGTH
  const canSubmit = !isBlank && !isTooLong && !submitting

  async function handleSubmit() {
    if (!id || !canSubmit) {
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

  // CodeEditor memoizes its CodeMirror extensions on [language, onSubmit], so
  // onSubmit needs a stable identity - handleSubmit itself isn't stable (it
  // closes over code/submitting, which change on every keystroke). This ref
  // always points at the latest handleSubmit; the wrapper passed down never
  // changes identity but always calls the current one.
  const handleSubmitRef = useRef(handleSubmit)
  handleSubmitRef.current = handleSubmit
  const handleSubmitViaShortcut = useCallback(() => {
    handleSubmitRef.current()
  }, [])

  function handleLanguageChange(e: ChangeEvent<HTMLSelectElement>) {
    const newLanguage = e.target.value as SupportedLanguage
    setLanguage(newLanguage)
    // Never overwrite code the user already wrote - only fill in the new
    // language's template if the editor is currently empty.
    if (!code.trim()) {
      setCode(LANGUAGE_TEMPLATES[newLanguage])
    }
  }

  function handleReset() {
    setCode(LANGUAGE_TEMPLATES[language])
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
            <>
              <Button variant="ghost" size="sm" onClick={handleReset} disabled={submitting}>
                Reset
              </Button>
              <Select
                aria-label="언어"
                value={language}
                onChange={handleLanguageChange}
                disabled={submitting}
              >
                {SUPPORTED_LANGUAGES.map((lang) => (
                  <option key={lang} value={lang}>
                    {LANGUAGE_LABELS[lang]}
                  </option>
                ))}
              </Select>
            </>
          }
        >
          <div className="solution-panel">
            <div className="solution-panel__editor">
              <CodeEditor
                value={code}
                onChange={setCode}
                language={language}
                readOnly={submitting}
                onSubmit={handleSubmitViaShortcut}
              />
            </div>

            {isTooLong && (
              <p className="solution-panel__validation-error">
                코드가 최대 길이 {MAX_SOURCE_LENGTH.toLocaleString()}자를 초과했습니다 (현재{' '}
                {code.length.toLocaleString()}자).
              </p>
            )}

            <div className="solution-panel__actions">
              <span className="solution-panel__shortcut-hint">{SUBMIT_SHORTCUT_LABEL}</span>
              <Button onClick={handleSubmit} disabled={!canSubmit} title={`${SUBMIT_SHORTCUT_LABEL}로도 제출할 수 있습니다`}>
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
