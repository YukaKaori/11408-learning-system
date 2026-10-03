/**
 * The study timer's two number formats. Pure, so they are tested directly.
 */

/** The running time as a clock face: `5:09`, `25:09`, `1:05:09`. */
export function formatClock(totalSeconds: number): string {
  const seconds = Math.max(0, Math.floor(totalSeconds))
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = seconds % 60
  const pad = (n: number) => String(n).padStart(2, '0')
  return h > 0 ? `${h}:${pad(m)}:${pad(s)}` : `${m}:${pad(s)}`
}

/**
 * Minutes as hours with at most one decimal — the unit a 考研 day is counted in
 * (`150` → `2.5`, `480` → `8`, `20` → `0.3`).
 */
export function hoursOf(minutes: number): string {
  const tenths = Math.round(Math.max(0, minutes) / 6) / 10
  return Number.isInteger(tenths) ? String(tenths) : tenths.toFixed(1)
}
