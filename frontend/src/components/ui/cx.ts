/** Joins truthy class name fragments with a space. No new dependency for this. */
export function cx(...classNames: Array<string | false | null | undefined>): string {
  return classNames.filter(Boolean).join(' ')
}
