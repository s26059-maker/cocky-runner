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
