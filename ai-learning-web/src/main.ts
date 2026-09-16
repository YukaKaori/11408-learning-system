import { createApp } from 'vue'
import { createPinia } from 'pinia'

import 'element-plus/theme-chalk/dark/css-vars.css'
import '@/styles/index.css'

import App from './App.vue'
import router from './router'
import { i18n } from '@/locales'
import { useAppStore } from '@/stores/app'
import { applyGlassTier } from '@/styles/materialTier'

// The material tier is one decision, taken once before anything mounts and
// written on <html> as data-glass-tier; every glass surface obeys it through
// the cascade (styles/materialTier.ts).
applyGlassTier()

const app = createApp(App)

app.use(createPinia())
app.use(i18n)
app.use(router)

// Apply persisted theme/locale before mounting to avoid a flash of wrong theme.
useAppStore().init()

app.mount('#app')
