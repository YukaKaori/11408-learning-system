/*
 * Prepare the Login stage's wallpaper for the wire, once, at author time.
 *
 *   public/images/welcome-bg.png    the supplied artwork (1691 × 930, ≈ 1.7 MB):
 *                                   icy-blue peonies, a translucent ribbon,
 *                                   droplets and petals on an ice-white ground
 *   → public/images/welcome-bg.webp the same picture as a high-quality WebP
 *
 * Nothing about the picture changes — no crop, no tone curve. The PNG stays
 * where it is: it is the source of truth and the stage's fallback; the WebP is
 * what browsers that can decode it download instead (≈ 140 KB, mean channel
 * error ≈ 1/255, indistinguishable from the PNG at 3× zoom).
 *
 * Run with Playwright's Chromium on NODE_PATH (the `verify` skill's cache path):
 *
 *   node scripts/environment/welcome.cjs
 */
const fs = require('fs')
const path = require('path')
const { chromium } = require('playwright')

const SOURCE = path.join(__dirname, '../../public/images/welcome-bg.png')
const OUT = process.env.ART_OUT || path.join(__dirname, '../../public/images/welcome-bg.webp')
const QUALITY = 0.92

;(async () => {
  const browser = await chromium.launch()
  const page = await browser.newPage()
  await page.setContent('<!doctype html><html><body></body></html>')
  const data = await page.evaluate(
    async ([src, quality]) => {
      const img = new Image()
      img.src = src
      await img.decode()
      const canvas = document.createElement('canvas')
      canvas.width = img.naturalWidth
      canvas.height = img.naturalHeight
      canvas.getContext('2d').drawImage(img, 0, 0)
      return canvas.toDataURL('image/webp', quality)
    },
    ['data:image/png;base64,' + fs.readFileSync(SOURCE).toString('base64'), QUALITY],
  )
  fs.writeFileSync(OUT, Buffer.from(data.split(',')[1], 'base64'))
  await browser.close()
  console.log(`wrote ${path.relative(process.cwd(), OUT)} (${fs.statSync(OUT).size} bytes)`)
})()
