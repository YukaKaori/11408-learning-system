/**
 * Parses an ISO `yyyy-MM-dd` calendar-bucket date (the analytics/workspace
 * activity wire format) as a *local* date. Never `new Date(iso)` — that
 * parses date-only strings as UTC midnight and shifts the day in timezones
 * west of Greenwich.
 */
export function parseIsoDate(iso: string): Date {
  const [year, month, day] = iso.split('-').map(Number)
  return new Date(year!, month! - 1, day!)
}
