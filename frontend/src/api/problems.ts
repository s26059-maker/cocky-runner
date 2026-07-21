import { apiFetch } from './client'
import type { ProblemDetail, ProblemSummary } from '../types'

export function getProblems(): Promise<ProblemSummary[]> {
  return apiFetch<ProblemSummary[]>('/api/v1/problems')
}

export function getProblem(id: string): Promise<ProblemDetail> {
  return apiFetch<ProblemDetail>(`/api/v1/problems/${encodeURIComponent(id)}`)
}
