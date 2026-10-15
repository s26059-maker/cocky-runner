import { useCallback, useState } from 'react'

const STORAGE_KEY = 'cocky-runner:editor-font-size'

export const MIN_FONT_SIZE = 12
export const MAX_FONT_SIZE = 20

function clamp(n: number): number {
  return Math.min(MAX_FONT_SIZE, Math.max(MIN_FONT_SIZE, n))
}

/** The editor's historical size: --text-base (see tokens.css). */
export function defaultFontSize(): number {
  const base = parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--text-base'))
  return clamp(Math.round(base) || 13)
}

function readFontSize(): number {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw !== null) {
      const n = Number(raw)
      if (Number.isInteger(n) && n >= MIN_FONT_SIZE && n <= MAX_FONT_SIZE) {
        return n
      }
    }
  } catch {
    // Fall through to the default.
  }
  return defaultFontSize()
}

export function useEditorFontSize() {
  const [fontSize, setFontSizeState] = useState(readFontSize)

  const setFontSize = useCallback((next: number) => {
    const clamped = clamp(next)
    setFontSizeState(clamped)
    try {
      localStorage.setItem(STORAGE_KEY, String(clamped))
    } catch {
      // Not persisted; the in-memory value still applies.
    }
  }, [])

  return { fontSize, setFontSize }
}
