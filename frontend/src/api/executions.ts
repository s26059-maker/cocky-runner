import { apiFetch } from './client'
import type { ExecutionRequest, ExecutionResponse } from '../types'

export function runCode(request: ExecutionRequest): Promise<ExecutionResponse> {
  return apiFetch<ExecutionResponse>('/api/v1/executions', {
    method: 'POST',
    body: JSON.stringify(request),
  })
}
