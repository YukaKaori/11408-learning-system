import { createI18n } from 'vue-i18n'
import zhCN from './zh-CN'
import enUS from './en-US'

export const SUPPORTED_LOCALES = ['zh-CN', 'en-US'] as const
export type AppLocale = (typeof SUPPORTED_LOCALES)[number]

export const DEFAULT_LOCALE: AppLocale = 'zh-CN'

export function isSupportedLocale(value: string | null): value is AppLocale {
  return SUPPORTED_LOCALES.includes(value as AppLocale)
}

const datetimeFormat = {
  long: {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  },
  /** Compact day, e.g. 07-12 / Jul 12 — list rows, card metadata. */
  short: {
    month: 'short',
    day: 'numeric',
  },
  /** Wall-clock time, e.g. 14:30 — calendar sessions, schedules. */
  time: {
    hour: '2-digit',
    minute: '2-digit',
  },
  /** Month + year, e.g. 2026年7月 / July 2026 — profile, calendar header. */
  monthYear: {
    year: 'numeric',
    month: 'long',
  },
} as const

export const i18n = createI18n<[typeof zhCN], AppLocale, false>({
  legacy: false,
  locale: DEFAULT_LOCALE,
  fallbackLocale: 'en-US',
  messages: {
    'zh-CN': zhCN,
    'en-US': enUS,
  },
  datetimeFormats: {
    'zh-CN': datetimeFormat,
    'en-US': datetimeFormat,
  },
})
