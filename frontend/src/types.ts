// 1:1 mirrors of the backend DTOs (see com.cocky.cockyrunner.dto / domain on the server).

// Mirrors SourceCodeLimits.MAX_SOURCE_LENGTH on the backend - a client-side
// check against the same limit (also in UTF-16 code units, i.e. JS string
// .length, matching String.length() on the server) so an over-limit
// submission is rejected locally instead of round-tripping to find out.
// The backend still enforces this independently; this only saves the trip.
export const MAX_SOURCE_LENGTH = 65536

export interface ProblemSummary {
  id: string
  title: string
}

export interface TestCasePreview {
  input: string
  expectedOutput: string
}

export interface ProblemDetail {
  id: string
  title: string
  description: string
  timeLimitMs: number
  sampleTestCases: TestCasePreview[]
}

export type DiagnosticSeverity = 'ERROR' | 'WARNING' | 'INFO'

// A compiler/runtime message pinned to a source line (see Diagnostic on the backend).
export interface Diagnostic {
  // 1-based.
  line: number
  // 1-based; null when the tool only reports a line.
  column: number | null
  severity: DiagnosticSeverity
  message: string
}

export type Verdict = 'AC' | 'WA' | 'TLE' | 'RE' | 'CE' | 'ERROR'

export interface SubmissionRequest {
  language: string
  code: string
}

export interface SubmissionResponse {
  verdict: Verdict
  passedCount: number
  totalCount: number
  failedCaseNumber: number | null
  // Host-measured, includes container startup - not the user program's time.
  maxExecutionTimeMs: number
  // User program's own wall/CPU time (max across executed cases). null when it
  // couldn't be measured for any executed case (TLE, CE, parse failure).
  userWallMs: number | null
  userCpuMs: number | null
  // For CE, always populated (a compile error is about the submitted code, not
  // test data). For RE/ERROR, only on a public sample test case; null otherwise
  // (hidden-case failures, WA, TLE) - see JudgeResult on the backend. Always
  // present in the response (never omitted), so this is not optional.
  errorOutput: string | null
  // Parsed from errorOutput's untruncated text; empty when there is none.
  diagnostics: Diagnostic[]
}

export interface SampleCaseResult {
  // 1-based position among the problem's sample cases.
  index: number
  input: string
  expectedOutput: string
  // Truncated server-side; null when infra failure left nothing to show.
  actualOutput: string | null
  verdict: Verdict
  stderr: string | null
  userWallMs: number | null
}

// Verdicts are decided by the backend (JudgeService) - never compare output here.
export interface SampleRunResponse {
  // Set (and cases empty) when compilation failed.
  compileError: string | null
  cases: SampleCaseResult[]
  // From the compile error, else from the first RE case's stderr.
  diagnostics: Diagnostic[]
}

export type ExecutionStatus = 'SUCCESS' | 'RUNTIME_ERROR' | 'TIMEOUT' | 'COMPILE_ERROR' | 'ERROR'

export interface ExecutionRequest {
  language: string
  code: string
  stdin?: string
}

export interface ExecutionResponse {
  status: ExecutionStatus
  stdout: string
  stderr: string
  exitCode: number
  // Host-measured, includes container startup - not the user program's time.
  executionTimeMs: number
  // User program's own wall/CPU time. null when it couldn't be determined
  // (CE, TIMEOUT, infra ERROR) - see ExecutionResponse on the backend.
  userWallMs: number | null
  userCpuMs: number | null
  // Parsed from the compile error (CE) or stderr (RE); empty otherwise.
  diagnostics: Diagnostic[]
}
