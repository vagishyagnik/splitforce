import jsQR from 'jsqr'

// Cap the scan resolution: big enough that a QR stays legible, small enough to stay fast.
const MAX_DIM = 1400

/**
 * Inspect an image file for a QR code.
 * Returns:
 *   true  — a QR code was found
 *   false — the image was readable but contained no QR code
 *   null  — the browser couldn't decode this format (e.g. HEIC on Chrome),
 *           so we can't tell; callers should let it through rather than reject.
 */
export async function detectQr(file) {
  let bitmap
  try {
    bitmap = await loadImage(file)
  } catch {
    return null // couldn't even load it — don't block on something we can't read
  }

  const { width, height } = bitmap
  if (!width || !height) {
    cleanup(bitmap)
    return null
  }

  const scale = Math.min(1, MAX_DIM / Math.max(width, height))
  const w = Math.max(1, Math.round(width * scale))
  const h = Math.max(1, Math.round(height * scale))

  const canvas = document.createElement('canvas')
  canvas.width = w
  canvas.height = h
  const ctx = canvas.getContext('2d', { willReadFrequently: true })
  if (!ctx) {
    cleanup(bitmap)
    return null
  }
  ctx.drawImage(bitmap, 0, 0, w, h)
  cleanup(bitmap)

  let data
  try {
    data = ctx.getImageData(0, 0, w, h)
  } catch {
    return null // tainted/blank canvas — can't inspect
  }

  // Try the image as-is, then inverted (dark-background / inverted QRs are common).
  const found =
    jsQR(data.data, w, h, { inversionAttempts: 'attemptBoth' })
  return !!(found && found.data)
}

function loadImage(file) {
  if (typeof createImageBitmap === 'function') {
    return createImageBitmap(file)
  }
  // Fallback for browsers without createImageBitmap.
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file)
    const img = new Image()
    img.onload = () => {
      URL.revokeObjectURL(url)
      resolve(img)
    }
    img.onerror = () => {
      URL.revokeObjectURL(url)
      reject(new Error('image load failed'))
    }
    img.src = url
  })
}

function cleanup(bitmap) {
  if (bitmap && typeof bitmap.close === 'function') bitmap.close()
}
