import { useI18n } from 'vue-i18n'

/**
 * Locale-aware study-time formatting ("2 小时 15 分" / "2h 15m").
 * All modules that show durations go through this — never hand-roll.
 */
export function useDuration() {
  const { t } = useI18n()

  function formatMinutes(totalMinutes: number): string {
    const h = Math.floor(totalMinutes / 60)
    const m = Math.round(totalMinutes % 60)
    if (h === 0) return t('common.duration.minutes', { n: m })
    if (m === 0) return t('common.duration.hours', { n: h })
    return t('common.duration.hoursMinutes', { h, m })
  }

  return { formatMinutes }
}
