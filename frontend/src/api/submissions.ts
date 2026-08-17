import { apiFetch } from './client'
import type { SubmissionRequest, SubmissionResponse } from '../types'

export function submitSolution(
  problemId: string,
  request: SubmissionRequest,
): Promise<SubmissionResponse> {
  return apiFetch<SubmissionResponse>(`/api/v1/problems/${encodeURIComponent(problemId)}/submissions`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
}
