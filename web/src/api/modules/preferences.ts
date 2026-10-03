import { api } from '@/api/http'

export type ThemePreference = 'light' | 'dark' | 'system'

export type LocalePreference = 'zh-CN' | 'en-US'

/**
 * Mirror of PreferencesResponse.java — the user's effective preferences.
 * When no row exists yet the server returns the defaults
 * (`system` / `zh-CN` / 60), which are part of the API contract.
 */
export interface PreferencesDto {
  theme: ThemePreference
  locale: LocalePreference
  dailyGoalMinutes: number
}

/** Partial update — omitted fields keep their current (or default) value. */
export interface UpdatePreferencesPayload {
  theme?: ThemePreference
  locale?: LocalePreference
  /** 1..1440 minutes. */
  dailyGoalMinutes?: number
}

export function getPreferences() {
  return api.get<PreferencesDto>('/v1/preferences')
}

export function updatePreferences(payload: UpdatePreferencesPayload) {
  return api.put<PreferencesDto>('/v1/preferences', payload)
}
