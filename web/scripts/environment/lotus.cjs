/*
 * Prepare the Login environment's one artwork for the stage, once, at author time.
 *
 *   source/pinklotus.png   the pink lotus this project generated for the Login
 *                          stage in Phase 4 (1536 × 1024, the flower on black)
 *   → src/assets/environment/lotus.webp
 *
 * Nothing about the flower changes. The frame is cropped to the flower (the
 * source is two-thirds empty black), the near-black ground (levels 0–2 of JPEG-
 * like noise) is set to true black so the screen blend adds nothing outside the
 * petals, and the result is encoded as a high-quality WebP (≈ 90 KB; the PNG
 * crop is ≈ 640 KB and indistinguishable at 3× zoom).
 *
 * The stage places this one image twice — mirrored on one side — so the two
 * lotus compositions are one download.
 *
 * Run with Playwright's Chromium on NODE_PATH (the `verify` skill's cache path):
 *
 *   node scripts/environment/lotus.cjs
 */
const fs = require('fs')
const path = require('path')
const { chromium } = require('playwright')

const SOURCE = path.join(__dirname, 'source/pinklotus.png')
const OUT = process.env.ART_OUT || path.join(__dirname, '../../src/assets/environment/lotus.webp')

/** The flower's bounds in the source are x 488–1188, y 120–939; this frame keeps a margin of black. */
const CROP = { x: 458, y: 90, width: 760, height: 880 }
/** Source levels at or below this are ground, not flower. */
const GROUND = 2
const QUALITY = 0.95

;(async () => {
  const browser = await chromium.launch()
  const page = await browser.newPage()
  await page.setContent('<!doctype html><html><body></body></html>')
  const data = await page.evaluate(
    async ([src, crop, ground, quality]) => {
      const img = new Image()
      img.src = src
      await img.decode()
      const canvas = document.createElement('canvas')
      canvas.width = crop.width
      canvas.height = crop.height
      const ctx = canvas.getContext('2d')
      ctx.drawImage(img, crop.x, crop.y, crop.width, crop.height, 0, 0, crop.width, crop.height)
      const pixels = ctx.getImageData(0, 0, crop.width, crop.height)
      const d = pixels.data
      // levels: ground → 0, 255 stays 255
      for (let i = 0; i < d.length; i += 4) {
        for (let k = 0; k < 3; k++) d[i + k] = Math.max(0, Math.round(((d[i + k] - ground) * 255) / (255 - ground)))
        d[i + 3] = 255
      }
      ctx.putImageData(pixels, 0, 0)
      return canvas.toDataURL('image/webp', quality)
    },
    ['data:image/png;base64,' + fs.readFileSync(SOURCE).toString('base64'), CROP, GROUND, QUALITY],
  )
  fs.writeFileSync(OUT, Buffer.from(data.split(',')[1], 'base64'))
  console.log(OUT, (fs.statSync(OUT).size / 1024).toFixed(0) + 'KB')
  await browser.close()
})()
