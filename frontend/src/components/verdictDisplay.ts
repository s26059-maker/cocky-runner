import type { Verdict } from '../types'
import type { BadgeTone } from './ui/Badge'

export const VERDICT_LABEL: Record<Verdict, string> = {
  AC: '정답',
  WA: '오답',
  TLE: '시간 초과',
  RE: '런타임 에러',
  CE: '컴파일 에러',
  ERROR: '채점 실패',
}

// ERROR has no dedicated verdict color of its own (it's an infra failure, not
// a judged outcome) - reuses the WA tone since it's a failure the same way.
export const VERDICT_TONE: Record<Verdict, BadgeTone> = {
  AC: 'ac',
  WA: 'wa',
  TLE: 'tle',
  RE: 're',
  CE: 'ce',
  ERROR: 'wa',
}
