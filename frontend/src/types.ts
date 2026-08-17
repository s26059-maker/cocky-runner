// 1:1 mirrors of the backend DTOs (see com.cocky.cockyrunner.dto / domain on the server).

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

export type Verdict = 'AC' | 'WA' | 'TLE' | 'RE' | 'ERROR'

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
  // Only ever populated for RE/ERROR on a public sample test case; null otherwise
  // (hidden-case failures, WA, TLE) - see JudgeResult on the backend. Always present
  // in the response (never omitted), so this is not optional.
  errorOutput: string | null
}

export type ExecutionStatus = 'SUCCESS' | 'RUNTIME_ERROR' | 'TIMEOUT' | 'ERROR'

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
