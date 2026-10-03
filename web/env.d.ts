/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

/** Injected at build time from package.json (see vite.config.ts `define`). */
declare const __APP_VERSION__: string
