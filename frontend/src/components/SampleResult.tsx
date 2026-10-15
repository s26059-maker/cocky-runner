import type { SampleRunResponse } from '../types'
import Badge from './ui/Badge'
import Button from './ui/Button'
import { VERDICT_LABEL, VERDICT_TONE } from './verdictDisplay'
import './SampleResult.css'

interface SampleResultProps {
  result: SampleRunResponse | null
  sampleError: string | null
  running?: boolean
  onSendInput: (input: string) => void
}

function SampleResult({ result, sampleError, running = false, onSendInput }: SampleResultProps) {
  if (running) {
    return (
      <div className="sample-result" role="status">
        <span className="sample-result__pending">예제 실행 중...</span>
      </div>
    )
  }

  if (sampleError) {
    return (
      <div className="sample-result" role="alert">
        <div className="sample-result__header">
          <Badge tone="wa">예제 실행 실패</Badge>
        </div>
        <pre className="sample-result__block">{sampleError}</pre>
      </div>
    )
  }

  if (!result) {
    return <p className="sample-result__empty">예제 실행 결과가 여기에 표시됩니다.</p>
  }

  if (result.compileError != null) {
    return (
      <div className="sample-result" role="status">
        <div className="sample-result__header">
          <Badge tone="ce">CE · 컴파일 에러</Badge>
        </div>
        <pre className="sample-result__block">{result.compileError}</pre>
      </div>
    )
  }

  const passed = result.cases.filter((c) => c.verdict === 'AC').length

  return (
    <div className="sample-result" role="status">
      <div className="sample-result__header">
        <span className="sample-result__summary">
          {passed}/{result.cases.length} 통과
        </span>
      </div>
      {result.cases.length === 0 && (
        <p className="sample-result__empty">이 문제에는 예제가 없습니다.</p>
      )}
      {result.cases.map((c) => (
        <details className="sample-card" key={c.index} open={c.verdict !== 'AC'}>
          <summary className="sample-card__summary">
            <span className="sample-card__title">예제 {c.index}</span>
            <Badge tone={VERDICT_TONE[c.verdict]}>
              {c.verdict} · {VERDICT_LABEL[c.verdict]}
            </Badge>
            {c.userWallMs != null && <span className="sample-card__time">{c.userWallMs}ms</span>}
          </summary>
          <div className="sample-card__body">
            <div className="sample-card__io">
              <section>
                <h3 className="sample-result__label">입력</h3>
                <pre className="sample-result__block">{c.input}</pre>
              </section>
              <section>
                <h3 className="sample-result__label">기대 출력</h3>
                <pre className="sample-result__block">{c.expectedOutput}</pre>
              </section>
              <section>
                <h3 className="sample-result__label">실제 출력</h3>
                <pre className="sample-result__block">{c.actualOutput ?? ''}</pre>
              </section>
            </div>
            {c.stderr && (
              <section>
                <h3 className="sample-result__label">stderr</h3>
                <pre className="sample-result__block sample-result__block--stderr">{c.stderr}</pre>
              </section>
            )}
            <div className="sample-card__actions">
              <Button variant="ghost" size="sm" onClick={() => onSendInput(c.input)}>
                입력으로 보내기
              </Button>
            </div>
          </div>
        </details>
      ))}
    </div>
  )
}

export default SampleResult
