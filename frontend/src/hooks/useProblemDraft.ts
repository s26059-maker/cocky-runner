import { useEffect, useRef, useState } from 'react'

const DEBOUNCE_MS = 500

function draftKey(problemId: string): string {
  return `cocky-runner:draft:${problemId}`
}

interface Draft<L extends string> {
  language: L
  code: string
}

/**
 * Reads and validates a stored draft. Never throws: JSON.parse failures, a
 * shape that doesn't match, an unrecognized language, or localStorage itself
 * throwing (Safari private mode, quota, disabled storage, ...) are all
 * treated the same way - silently fall back to the given default - since
 * none of that is something the user did wrong or needs to see.
 */
function readDraft<L extends string>(problemId: string, validLanguages: readonly L[], fallbackLanguage: L): Draft<L> {
  try {
    const raw = localStorage.getItem(draftKey(problemId))
    if (raw) {
      const parsed: unknown = JSON.parse(raw)
      if (
        typeof parsed === 'object' &&
        parsed !== null &&
        typeof (parsed as Record<string, unknown>).code === 'string' &&
        validLanguages.includes((parsed as Record<string, unknown>).language as L)
      ) {
        return {
          language: (parsed as Record<string, unknown>).language as L,
          code: (parsed as Record<string, unknown>).code as string,
        }
      }
    }
  } catch {
    // Fall through to the default below.
  }
  return { language: fallbackLanguage, code: '' }
}

/**
 * Per-problem draft persistence: { language, code } as one JSON value under
 * `cocky-runner:draft:{problemId}`, restored on mount/problem change and
 * saved 500ms after the last edit. Kept regardless of submission outcome -
 * this hook has no idea whether a submission ever happened.
 *
 * If the restored (or default, for a problem with no draft yet) code is
 * blank, `templates[language]` is inserted - this is the one point where a
 * template gets applied on load; switching languages afterwards is the
 * caller's responsibility (see ProblemDetailPage), since only the caller
 * knows the current, live `code` value at the moment of that switch.
 */
export function useProblemDraft<L extends string>(
  problemId: string | undefined,
  validLanguages: readonly L[],
  fallbackLanguage: L,
  templates: Record<L, string>,
) {
  const [language, setLanguage] = useState<L>(fallbackLanguage)
  const [code, setCode] = useState('')

  // What was just loaded for the current problemId, so the save effect below
  // can tell "this is still exactly what we loaded" apart from "the user (or
  // a template) actually changed something" - see the save effect for why
  // that distinction matters here.
  const lastLoadedRef = useRef<{ problemId: string; language: L; code: string } | null>(null)

  useEffect(() => {
    if (!problemId) {
      return
    }
    const draft = readDraft(problemId, validLanguages, fallbackLanguage)
    const resolvedCode = draft.code.trim() ? draft.code : templates[draft.language]
    lastLoadedRef.current = { problemId, language: draft.language, code: resolvedCode }
    setLanguage(draft.language)
    setCode(resolvedCode)
    // validLanguages/fallbackLanguage/templates are static for the lifetime of
    // this component; only problemId actually changing should trigger a reload.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [problemId])

  useEffect(() => {
    if (!problemId) {
      return
    }
    const lastLoaded = lastLoadedRef.current
    if (
      lastLoaded &&
      lastLoaded.problemId === problemId &&
      lastLoaded.language === language &&
      lastLoaded.code === code
    ) {
      // Nothing has actually changed since the load above (this run is that
      // same load, seen through the not-yet-updated closure of the render
      // that scheduled it) - saving here would be redundant at best, and at
      // worst races the load itself with stale pre-load values. The load
      // effect always runs first and its state update always lands (and this
      // effect re-runs, this time matching) well within DEBOUNCE_MS.
      return
    }
    const timeoutId = setTimeout(() => {
      try {
        localStorage.setItem(draftKey(problemId), JSON.stringify({ language, code }))
      } catch {
        // Storage can fail (quota, private mode, disabled) - the draft just
        // doesn't persist; the editor itself must keep working regardless.
      }
    }, DEBOUNCE_MS)
    return () => clearTimeout(timeoutId)
  }, [problemId, language, code])

  return { language, setLanguage, code, setCode }
}
