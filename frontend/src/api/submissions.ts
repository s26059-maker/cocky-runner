import { apiFetch } from './client'
import type { SampleRunResponse, SubmissionRequest, SubmissionResponse } from '../types'

export function runSamples(
  problemId: string,
  request: SubmissionRequest,
): Promise<SampleRunResponse> {
  return apiFetch<SampleRunResponse>(`/api/v1/problems/${encodeURIComponent(problemId)}/samples/run`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function submitSolution(
  problemId: string,
  request: SubmissionRequest,
): Promise<SubmissionResponse> {
  return apiFetch<SubmissionResponse>(`/api/v1/problems/${encodeURIComponent(problemId)}/submissions`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
}
