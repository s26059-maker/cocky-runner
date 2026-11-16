import type * as Monaco from 'monaco-editor'

// Browser-side (wasm) document formatting for the editor's languages.
// Hooked into Monaco's built-in "Format Document" (Shift+Alt+F / context menu).
// The wasm modules are only fetched the first time a format is requested.

const CLANG_STYLE = '{BasedOnStyle: Google, IndentWidth: 4, ColumnLimit: 100}'
const RUFF_CONFIG = { indent_width: 4, line_width: 100 }

type Formatter = (source: string) => Promise<string>

// Cache the load promise (not the module) so concurrent first calls share one
// init. A failed load clears the cache so the next attempt retries.
function lazy<T>(load: () => Promise<T>): () => Promise<T> {
  let cached: Promise<T> | null = null
  return () => {
    if (!cached) {
      cached = load().catch((e) => {
        cached = null
        throw e
      })
    }
    return cached
  }
}

const loadClang = lazy(async () => {
  const mod = await import('@wasm-fmt/clang-format/vite')
  await mod.default()
  return mod
})

const loadRuff = lazy(async () => {
  const mod = await import('@wasm-fmt/ruff_fmt/vite')
  await mod.default()
  return mod
})

const clangFormatter =
  (filename: string): Formatter =>
  async (source) => (await loadClang()).format(source, filename, CLANG_STYLE)

const ruffFormatter: Formatter = async (source) =>
  (await loadRuff()).format(source, 'main.py', RUFF_CONFIG)

const FORMATTERS: Record<string, Formatter> = {
  c: clangFormatter('main.c'),
  java: clangFormatter('Main.java'),
  python: ruffFormatter,
}

let registered = false

export function registerFormatters(monaco: typeof Monaco): void {
  if (registered) return
  registered = true

  for (const [languageId, formatter] of Object.entries(FORMATTERS)) {
    monaco.languages.registerDocumentFormattingEditProvider(languageId, {
      async provideDocumentFormattingEdits(model) {
        const original = model.getValue()
        try {
          const formatted = await formatter(original)
          if (formatted === original) return []
          // One whole-document replacement => a single undo step.
          return [{ range: model.getFullModelRange(), text: formatted }]
        } catch (e) {
          console.warn(`[format] ${languageId} formatting skipped:`, e)
          return []
        }
      },
    })
  }
}
