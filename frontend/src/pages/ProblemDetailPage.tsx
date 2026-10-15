import { useRef, useState } from 'react'
import type { ChangeEvent, KeyboardEvent } from 'react'
import { Link, useParams } from 'react-router'
import { getProblem } from '../api/problems'
import { runSamples, submitSolution } from '../api/submissions'
import { runCode } from '../api/executions'
import { ApiError, toErrorMessage } from '../api/client'
import { useAsyncData } from '../hooks/useAsyncData'
import { useProblemDraft } from '../hooks/useProblemDraft'
import { useProblemStdin } from '../hooks/useProblemStdin'
import { MAX_FONT_SIZE, MIN_FONT_SIZE, useEditorFontSize } from '../hooks/useEditorFontSize'
import CodeEditor, {
  type CodeEditorHandle,
  LANGUAGE_TEMPLATES,
  SUPPORTED_LANGUAGES,
  type SupportedLanguage,
} from '../components/CodeEditor'
import SubmissionResult from '../components/SubmissionResult'
import ExecutionResult from '../components/ExecutionResult'
import SampleResult from '../components/SampleResult'
import Panel from '../components/ui/Panel'
import Button from '../components/ui/Button'
import Select from '../components/ui/Select'
import {
  MAX_SOURCE_LENGTH,
  type Diagnostic,
  type ExecutionResponse,
  type SampleRunResponse,
  type SubmissionResponse,
} from '../types'
import './ProblemDetailPage.css'

const LANGUAGE_LABELS: Record<SupportedLanguage, string> = {
  C: 'C',
  PYTHON: 'Python',
  JAVA: 'Java',
}

const IS_MAC = /Mac|iPhone|iPad|iPod/.test(navigator.userAgent)
const RUN_SHORTCUT_LABEL = IS_MAC ? '⌘Enter' : 'Ctrl+Enter'
const SUBMIT_SHORTCUT_LABEL = IS_MAC ? '⌘⇧Enter' : 'Ctrl+Shift+Enter'

type ResultTab = 'input' | 'run' | 'samples' | 'submit'

const RESULT_TABS: { id: ResultTab; label: string }[] = [
  { id: 'input', label: '입력' },
  { id: 'run', label: '실행 결과' },
  { id: 'samples', label: '예제 결과' },
  { id: 'submit', label: '제출 결과' },
]

const NO_DIAGNOSTICS: Diagnostic[] = []

function ProblemDetailPage() {
  const { id } = useParams<{ id: string }>()
  const { data: problem, error, loading } = useAsyncData(() => getProblem(id!), [id])

  const { language, setLanguage, code, setCode } = useProblemDraft(
    id,
    SUPPORTED_LANGUAGES,
    SUPPORTED_LANGUAGES[0],
    LANGUAGE_TEMPLATES,
  )
  // null until *this* problem has loaded (useAsyncData keeps the previous
  // problem's data around while the next one is fetching).
  const sampleInput = problem && problem.id === id ? (problem.sampleTestCases[0]?.input ?? '') : null
  const { stdin, setStdin } = useProblemStdin(id, sampleInput)
  const { fontSize, setFontSize } = useEditorFontSize()

  const [activeTab, setActiveTab] = useState<ResultTab>('input')
  const [running, setRunning] = useState(false)
  const [runResult, setRunResult] = useState<ExecutionResponse | null>(null)
  const [runError, setRunError] = useState<string | null>(null)
  const [runningSamples, setRunningSamples] = useState(false)
  const [sampleResult, setSampleResult] = useState<SampleRunResponse | null>(null)
  const [sampleError, setSampleError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState<SubmissionResponse | null>(null)
  const [submitError, setSubmitError] = useState<string | null>(null)

  // Editor underlines. Cleared when a run/samples/submit starts and on a language
  // change - never on typing. Tagged with the problem id so another problem's
  // errors can't show up on this problem's code.
  const editorRef = useRef<CodeEditorHandle>(null)
  const [markerState, setMarkerState] = useState<{ problemId: string | undefined; items: Diagnostic[] }>({
    problemId: id,
    items: NO_DIAGNOSTICS,
  })
  const markers = markerState.problemId === id ? markerState.items : NO_DIAGNOSTICS
  function showMarkers(items: Diagnostic[] | undefined) {
    setMarkerState({ problemId: id, items: items && items.length > 0 ? items : NO_DIAGNOSTICS })
  }

  function handleSelectDiagnostic(d: Diagnostic) {
    editorRef.current?.revealLine(d.line, d.column)
  }

  const isBlank = !code.trim()
  const isTooLong = code.length > MAX_SOURCE_LENGTH
  const busy = running || runningSamples || submitting
  const canRun = !isBlank && !isTooLong && !busy
  const canRunSamples = canRun
  const canSubmit = canRun

  async function handleRunSamples() {
    if (!id || !canRunSamples) {
      return
    }

    setActiveTab('samples')
    setRunningSamples(true)
    setSampleResult(null)
    setSampleError(null)
    showMarkers(NO_DIAGNOSTICS)

    try {
      const response = await runSamples(id, { language, code })
      setSampleResult(response)
      showMarkers(response.diagnostics)
    } catch (err) {
      setSampleError(toErrorMessage(err))
    } finally {
      setRunningSamples(false)
    }
  }

  function handleSendInput(input: string) {
    setStdin(input)
    setActiveTab('input')
  }

  async function handleRun() {
    if (!canRun) {
      return
    }

    setActiveTab('run')
    setRunning(true)
    setRunResult(null)
    setRunError(null)
    showMarkers(NO_DIAGNOSTICS)

    try {
      const response = await runCode({ language, code, stdin })
      setRunResult(response)
      showMarkers(response.diagnostics)
    } catch (err) {
      setRunError(toErrorMessage(err))
    } finally {
      setRunning(false)
    }
  }

  async function handleSubmit() {
    if (!id || !canSubmit) {
      return
    }

    setActiveTab('submit')
    setSubmitting(true)
    setResult(null)
    setSubmitError(null)
    showMarkers(NO_DIAGNOSTICS)

    try {
      const response = await submitSolution(id, { language, code })
      setResult(response)
      showMarkers(response.diagnostics)
    } catch (err) {
      setSubmitError(toErrorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  function handleStdinKeyDown(e: KeyboardEvent<HTMLTextAreaElement>) {
    if ((e.ctrlKey || e.metaKey) && !e.shiftKey && e.key === 'Enter') {
      e.preventDefault()
      void handleRun()
    }
  }

  function handleLanguageChange(e: ChangeEvent<HTMLSelectElement>) {
    const newLanguage = e.target.value as SupportedLanguage
    setLanguage(newLanguage)
    showMarkers(NO_DIAGNOSTICS)
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
              <div className="font-size-control" role="group" aria-label="글꼴 크기">
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => setFontSize(fontSize - 1)}
                  disabled={fontSize <= MIN_FONT_SIZE}
                  aria-label="글꼴 작게"
                >
                  −
                </Button>
                <span className="font-size-control__value" aria-live="polite">
                  {fontSize}
                </span>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => setFontSize(fontSize + 1)}
                  disabled={fontSize >= MAX_FONT_SIZE}
                  aria-label="글꼴 크게"
                >
                  +
                </Button>
              </div>
              <Button variant="ghost" size="sm" onClick={handleReset} disabled={busy}>
                Reset
              </Button>
              <Select
                aria-label="언어"
                value={language}
                onChange={handleLanguageChange}
                disabled={busy}
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
                ref={editorRef}
                diagnostics={markers}
                value={code}
                onChange={setCode}
                language={language}
                readOnly={submitting}
                onSubmit={handleSubmit}
                onRun={handleRun}
                fontSize={fontSize}
              />
            </div>

            {isTooLong && (
              <p className="solution-panel__validation-error">
                코드가 최대 길이 {MAX_SOURCE_LENGTH.toLocaleString()}자를 초과했습니다 (현재{' '}
                {code.length.toLocaleString()}자).
              </p>
            )}

            <div className="solution-panel__actions">
              {language === 'JAVA' && (
                <span className="solution-panel__language-hint">클래스 이름은 Main이어야 합니다</span>
              )}
              <Button
                variant="ghost"
                onClick={handleRun}
                disabled={!canRun}
                title={`${RUN_SHORTCUT_LABEL}로도 실행할 수 있습니다`}
              >
                {running ? '실행 중...' : '실행'}
                <span className="solution-panel__shortcut-hint">{RUN_SHORTCUT_LABEL}</span>
              </Button>
              <Button variant="ghost" onClick={handleRunSamples} disabled={!canRunSamples}>
                {runningSamples ? '예제 실행 중...' : '예제 실행'}
              </Button>
              <Button
                onClick={handleSubmit}
                disabled={!canSubmit}
                title={`${SUBMIT_SHORTCUT_LABEL}로도 제출할 수 있습니다`}
              >
                {submitting ? '채점 중...' : '제출'}
                <span className="solution-panel__shortcut-hint">{SUBMIT_SHORTCUT_LABEL}</span>
              </Button>
            </div>

            <div className="result-tabs">
              <div className="result-tabs__list" role="tablist">
                {RESULT_TABS.map((tab) => (
                  <button
                    key={tab.id}
                    type="button"
                    role="tab"
                    id={`result-tab-${tab.id}`}
                    aria-selected={activeTab === tab.id}
                    aria-controls="result-tabpanel"
                    className={`result-tabs__tab${activeTab === tab.id ? ' result-tabs__tab--active' : ''}`}
                    onClick={() => setActiveTab(tab.id)}
                  >
                    {tab.label}
                  </button>
                ))}
              </div>
              <div
                className="result-tabs__panel"
                role="tabpanel"
                id="result-tabpanel"
                aria-labelledby={`result-tab-${activeTab}`}
              >
                {activeTab === 'input' && (
                  <textarea
                    className="result-tabs__stdin"
                    aria-label="표준 입력 (stdin)"
                    placeholder={`실행 시 표준 입력으로 전달됩니다 (${RUN_SHORTCUT_LABEL}로 실행)`}
                    spellCheck={false}
                    value={stdin}
                    onChange={(e) => setStdin(e.target.value)}
                    onKeyDown={handleStdinKeyDown}
                  />
                )}
                {activeTab === 'run' && (
                  <ExecutionResult
                    result={runResult}
                    runError={runError}
                    running={running}
                    onSelectDiagnostic={handleSelectDiagnostic}
                  />
                )}
                {activeTab === 'samples' && (
                  <SampleResult
                    result={sampleResult}
                    sampleError={sampleError}
                    running={runningSamples}
                    onSendInput={handleSendInput}
                    onSelectDiagnostic={handleSelectDiagnostic}
                  />
                )}
                {activeTab === 'submit' && (
                  <SubmissionResult
                    result={result}
                    submitError={submitError}
                    submitting={submitting}
                    onSelectDiagnostic={handleSelectDiagnostic}
                  />
                )}
              </div>
            </div>
          </div>
        </Panel>
      </div>
    </div>
  )
}

export default ProblemDetailPage
