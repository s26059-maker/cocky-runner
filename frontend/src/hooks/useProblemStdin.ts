import { useCallback, useEffect, useRef, useState } from 'react'

const DEBOUNCE_MS = 500

function stdinKey(problemId: string): string {
  return `cocky-runner:stdin:${problemId}`
}

function readStdin(problemId: string): string | null {
  try {
    return localStorage.getItem(stdinKey(problemId))
  } catch {
    // Storage unavailable (private mode, disabled) - behave as "nothing saved".
    return null
  }
}

/**
 * Per-problem stdin for "run": restored from `cocky-runner:stdin:{problemId}`
 * (a key of its own, separate from the code draft), falling back to
 * `defaultStdin` (the first sample's input) when nothing was saved.
 *
 * `defaultStdin` is null until the problem has loaded; the restore happens
 * once it isn't. Only user edits are persisted (debounced) - the pre-filled
 * sample is never written back, so it keeps following the problem's sample
 * until the user actually changes it.
 */
export function useProblemStdin(problemId: string | undefined, defaultStdin: string | null) {
  const [stdin, setStdinState] = useState('')
  const ready = defaultStdin !== null
  const defaultRef = useRef(defaultStdin)
  defaultRef.current = defaultStdin
  const saveTimerRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)

  useEffect(() => {
    if (!problemId || !ready) {
      return
    }
    setStdinState(readStdin(problemId) ?? defaultRef.current ?? '')
    return () => clearTimeout(saveTimerRef.current)
  }, [problemId, ready])

  const setStdin = useCallback(
    (value: string) => {
      setStdinState(value)
      if (!problemId) {
        return
      }
      clearTimeout(saveTimerRef.current)
      saveTimerRef.current = setTimeout(() => {
        try {
          localStorage.setItem(stdinKey(problemId), value)
        } catch {
          // Quota / private mode - stdin just doesn't persist.
        }
      }, DEBOUNCE_MS)
    },
    [problemId],
  )

  return { stdin, setStdin }
}
