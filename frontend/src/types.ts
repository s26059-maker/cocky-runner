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
  maxExecutionTimeMs: number
  // For CE, always populated (a compile error is about the submitted code, not
  // test data). For RE/ERROR, only on a public sample test case; null otherwise
  // (hidden-case failures, WA, TLE) - see JudgeResult on the backend. Always
  // present in the response (never omitted), so this is not optional.
  errorOutput: string | null
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
  executionTimeMs: number
}
