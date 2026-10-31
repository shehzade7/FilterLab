package com.shehzade7.filterlab

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.max
import kotlin.random.Random

enum class Effect(val label: String) {
    Original("Original"), Grain("Grain"), Vhs("VHS"), Glitch("Glitch"), LightLeak("Leak"),
    Prism("Prism"), Vintage("Vintage"), Faded("Faded"), Dust("Dust"), Dreamy("Dreamy"),
    Glow("Glow"), Cinematic("Cinema"), Duotone("Duotone"), Warm("Warm"), Cool("Cool"),
    Pop("Pop"), Mirror("Mirror"), Pixel("Pixel"), Mono("Mono"), Noir("Noir");

    fun apply(src: Bitmap): Bitmap = when (this) {
        Original -> src
        Grain -> src.grain(0.55f)
        Vhs -> src.rgbSplit(src.width * 0.004f)
            .withMatrix(chain(saturationM(1.3f), contrastM(1.05f)))
            .scanlines()
            .grain(0.25f)
        Glitch -> src.rgbSplit(src.width * 0.012f).glitchBands().withMatrix(contrastM(1.15f))
        LightLeak -> src.lightLeak().grain(0.15f)
        Prism -> src.rgbSplit(src.width * 0.003f).prism()
        Vintage -> src.withMatrix(chain(sepiaM(0.45f), fadeM())).vignette().grain(0.3f)
        Faded -> src.withMatrix(chain(fadeM(), saturationM(0.75f)))
        Dust -> src.withMatrix(chain(sepiaM(0.2f), fadeM())).dust().grain(0.3f)
        Dreamy -> src.glow(0.8f).withMatrix(chain(scaleM(1f, 0.97f, 1f, 15f, 5f, 15f), fadeM()))
        Glow -> src.glow(0.7f)
        Cinematic -> src.withMatrix(chain(scaleM(1.08f, 1f, 0.9f, -5f, 0f, 15f), contrastM(1.12f))).letterbox()
        Duotone -> src.withMatrix(duotoneM(Color.rgb(40, 20, 90), Color.rgb(255, 140, 170)))
        Warm -> src.withMatrix(chain(scaleM(1.1f, 1.02f, 0.88f), saturationM(1.1f)))
        Cool -> src.withMatrix(scaleM(0.9f, 1f, 1.1f))
        Pop -> src.withMatrix(chain(saturationM(1.5f), contrastM(1.15f)))
        Mirror -> src.mirror()
        Pixel -> src.pixelate()
        Mono -> src.withMatrix(chain(saturationM(0f), contrastM(1.05f))).grain(0.2f)
        Noir -> src.withMatrix(chain(saturationM(0f), contrastM(1.35f))).vignette()
    }
}

data class Adjustments(
    val brightness: Float = 0f,   // -0.3..0.3
    val contrast: Float = 1f,     // 0.5..1.5
    val saturation: Float = 1f,   // 0..2
    val warmth: Float = 0f,       // -1..1
) {
    val isDefault get() = this == Adjustments()

    fun matrix(): ColorMatrix {
        val b = brightness * 255f
        return chain(
            saturationM(saturation),
            contrastM(contrast),
            scaleM(1f, 1f, 1f, b, b, b),
            scaleM(1 + 0.12f * warmth, 1f, 1 - 0.12f * warmth),
        )
    }
}

data class Extras(val dateStamp: Boolean = false, val frame: Boolean = false)

object Renderer {
    fun render(src: Bitmap, effect: Effect, amount: Float, adj: Adjustments, extras: Extras = Extras()): Bitmap {
        var out = src.copy(Bitmap.Config.ARGB_8888, true)
        if (effect != Effect.Original) {
            Canvas(out).drawBitmap(effect.apply(src), 0f, 0f, Paint().apply { alpha = (amount * 255).toInt() })
        }
        if (!adj.isDefault) out = out.withMatrix(adj.matrix())
        if (extras.dateStamp) out = out.dateStamp()
        if (extras.frame) out = out.polaroid()
        return out
    }
}

// region Color matrices

fun scaleM(r: Float, g: Float, b: Float, br: Float = 0f, bg: Float = 0f, bb: Float = 0f) = ColorMatrix(
    floatArrayOf(
        r, 0f, 0f, 0f, br,
        0f, g, 0f, 0f, bg,
        0f, 0f, b, 0f, bb,
        0f, 0f, 0f, 1f, 0f,
    )
)

fun contrastM(c: Float): ColorMatrix {
    val t = 128f * (1 - c)
    return scaleM(c, c, c, t, t, t)
}

fun saturationM(s: Float) = ColorMatrix().apply { setSaturation(s) }

/** Lifts blacks and softens whites. */
fun fadeM() = scaleM(0.83f, 0.83f, 0.83f, 30f, 30f, 30f)

fun sepiaM(amount: Float): ColorMatrix {
    val s = floatArrayOf(
        0.393f, 0.769f, 0.189f,
        0.349f, 0.686f, 0.168f,
        0.272f, 0.534f, 0.131f,
    )
    val a = amount
    val m = FloatArray(20)
    for (row in 0..2) for (col in 0..2) {
        val identity = if (row == col) 1f else 0f
        m[row * 5 + col] = identity * (1 - a) + s[row * 3 + col] * a
    }
    m[18] = 1f
    return ColorMatrix(m)
}

/** Maps luminance onto a gradient from [dark] to [light]. */
fun duotoneM(dark: Int, light: Int): ColorMatrix {
    val m = FloatArray(20)
    val lum = floatArrayOf(0.3f, 0.59f, 0.11f)
    val d = intArrayOf(Color.red(dark), Color.green(dark), Color.blue(dark))
    val l = intArrayOf(Color.red(light), Color.green(light), Color.blue(light))
    for (row in 0..2) {
        val k = (l[row] - d[row]) / 255f
        for (col in 0..2) m[row * 5 + col] = lum[col] * k
        m[row * 5 + 4] = d[row].toFloat()
    }
    m[18] = 1f
    return ColorMatrix(m)
}

/** Applies matrices in order. */
fun chain(vararg ms: ColorMatrix) = ColorMatrix().apply { ms.forEach { postConcat(it) } }

// endregion

// region Bitmap building blocks

private fun Bitmap.blank(): Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
private fun Bitmap.mutableCopy(): Bitmap = copy(Bitmap.Config.ARGB_8888, true)
private fun Bitmap.fullRect() = Rect(0, 0, width, height)

fun Bitmap.withMatrix(m: ColorMatrix): Bitmap {
    val out = blank()
    Canvas(out).drawBitmap(this, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(m) })
    return out
}

@Volatile private var noiseCache: Bitmap? = null

private fun noise(w: Int, h: Int): Bitmap {
    noiseCache?.let { if (it.width == w && it.height == h) return it }
    val px = IntArray(w * h) { val v = Random.nextInt(256); Color.rgb(v, v, v) }
    return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888).also { noiseCache = it }
}

fun Bitmap.grain(strength: Float): Bitmap {
    val s = max(1, width / 1200)
    val n = noise(max(1, width / s), max(1, height / s))
    val out = mutableCopy()
    Canvas(out).drawBitmap(n, null, fullRect(), Paint().apply {
        blendMode = BlendMode.SOFT_LIGHT
        alpha = (strength * 255).toInt()
        isFilterBitmap = false
    })
    return out
}

fun Bitmap.rgbSplit(dx: Float): Bitmap {
    val out = blank()
    val c = Canvas(out)
    c.drawColor(Color.BLACK)
    val p = Paint().apply { blendMode = BlendMode.PLUS }
    p.colorFilter = ColorMatrixColorFilter(scaleM(1f, 0f, 0f)); c.drawBitmap(this, dx, 0f, p)
    p.colorFilter = ColorMatrixColorFilter(scaleM(0f, 1f, 0f)); c.drawBitmap(this, 0f, 0f, p)
    p.colorFilter = ColorMatrixColorFilter(scaleM(0f, 0f, 1f)); c.drawBitmap(this, -dx, 0f, p)
    return out
}

fun Bitmap.scanlines(): Bitmap {
    val out = mutableCopy()
    val c = Canvas(out)
    val line = max(1f, height / 400f)
    val p = Paint().apply { color = Color.argb(56, 0, 0, 0) }
    var y = 0f
    while (y < height) {
        c.drawRect(0f, y, width.toFloat(), y + line, p)
        y += line * 2
    }
    return out
}

fun Bitmap.glitchBands(): Bitmap {
    val out = mutableCopy()
    val c = Canvas(out)
    val bands = listOf(Triple(0.18f, 0.05f, 0.04f), Triple(0.52f, 0.03f, -0.06f), Triple(0.78f, 0.04f, 0.03f))
    for ((y, h, dx) in bands) {
        val top = (height * y).toInt()
        val bottom = (height * (y + h)).toInt()
        val shift = (width * dx).toInt()
        c.drawBitmap(this, Rect(0, top, width, bottom), Rect(shift, top, width + shift, bottom), null)
    }
    return out
}

fun Bitmap.lightLeak(): Bitmap {
    val out = mutableCopy()
    val c = Canvas(out)
    val r = max(width, height).toFloat()
    val w = width.toFloat()
    val h = height.toFloat()
    val p = Paint().apply { blendMode = BlendMode.SCREEN }
    p.shader = RadialGradient(w, 0f, r * 0.8f, Color.rgb(255, 115, 25), Color.BLACK, Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, w, h, p)
    p.shader = RadialGradient(0f, h, r * 0.6f, Color.rgb(255, 50, 128), Color.BLACK, Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, w, h, p)
    return out
}

fun Bitmap.prism(): Bitmap {
    val out = mutableCopy()
    val w = width.toFloat()
    val h = height.toFloat()
    val colors = intArrayOf(
        Color.BLACK, Color.rgb(255, 60, 60), Color.rgb(255, 200, 40), Color.rgb(60, 255, 120),
        Color.rgb(60, 140, 255), Color.rgb(200, 60, 255), Color.BLACK,
    )
    val p = Paint().apply {
        blendMode = BlendMode.SCREEN
        alpha = 110
        shader = LinearGradient(0f, h * 0.2f, w, h * 0.8f, colors, null, Shader.TileMode.CLAMP)
    }
    Canvas(out).drawRect(0f, 0f, w, h, p)
    return out
}

fun Bitmap.dust(): Bitmap {
    val out = mutableCopy()
    val c = Canvas(out)
    val rnd = Random(7)
    val unit = max(width, height) / 1000f
    val speck = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(200, 255, 255, 240) }
    repeat(140) {
        c.drawCircle(rnd.nextFloat() * width, rnd.nextFloat() * height, unit * (0.6f + rnd.nextFloat() * 2f), speck)
    }
    val hair = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 255, 255, 240)
        strokeWidth = unit * 0.8f
    }
    repeat(6) {
        val x = rnd.nextFloat() * width
        val y = rnd.nextFloat() * height
        c.drawLine(x, y, x + (rnd.nextFloat() - 0.5f) * unit * 60, y + (rnd.nextFloat() - 0.5f) * unit * 60, hair)
    }
    return out
}

fun Bitmap.vignette(): Bitmap {
    val out = mutableCopy()
    val cx = width / 2f
    val cy = height / 2f
    val p = Paint().apply {
        shader = RadialGradient(
            cx, cy, hypot(cx, cy),
            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(170, 0, 0, 0)),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    Canvas(out).drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
    return out
}

fun Bitmap.glow(strength: Float): Bitmap {
    val tiny = Bitmap.createScaledBitmap(this, max(1, width / 16), max(1, height / 16), true)
    val soft = Bitmap.createScaledBitmap(tiny, max(1, width / 8), max(1, height / 8), true)
    val blurred = Bitmap.createScaledBitmap(soft, width, height, true)
    val out = mutableCopy()
    Canvas(out).drawBitmap(blurred, 0f, 0f, Paint().apply {
        blendMode = BlendMode.SCREEN
        alpha = (strength * 255).toInt()
    })
    return out
}

fun Bitmap.letterbox(): Bitmap {
    if (width <= height) return this
    val out = mutableCopy()
    val c = Canvas(out)
    val bar = height * 0.1f
    val p = Paint().apply { color = Color.BLACK }
    c.drawRect(0f, 0f, width.toFloat(), bar, p)
    c.drawRect(0f, height - bar, width.toFloat(), height.toFloat(), p)
    return out
}

fun Bitmap.mirror(): Bitmap {
    val out = mutableCopy()
    val half = width / 2
    val left = Bitmap.createBitmap(this, 0, 0, max(1, half), height)
    val flip = Matrix().apply {
        setScale(-1f, 1f)
        postTranslate(width.toFloat(), 0f)
    }
    Canvas(out).drawBitmap(left, flip, null)
    return out
}

fun Bitmap.pixelate(): Bitmap {
    val cells = 60
    val small = Bitmap.createScaledBitmap(this, cells, max(1, cells * height / width), false)
    return Bitmap.createScaledBitmap(small, width, height, false)
}

fun Bitmap.dateStamp(): Bitmap {
    val out = mutableCopy()
    val text = SimpleDateFormat("''yy MM dd", Locale.US).format(Date())
    val size = max(width, height) * 0.035f
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 140, 40)
        textSize = size
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.RIGHT
        setShadowLayer(size * 0.35f, 0f, 0f, Color.rgb(255, 80, 0))
    }
    Canvas(out).drawText(text, width - size * 1.2f, height - size * 1.2f, p)
    return out
}

fun Bitmap.polaroid(): Bitmap {
    val side = (width * 0.05f).toInt()
    val bottom = (width * 0.2f).toInt()
    val out = Bitmap.createBitmap(width + side * 2, height + side + bottom, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    c.drawColor(Color.rgb(250, 248, 242))
    c.drawBitmap(this, side.toFloat(), side.toFloat(), null)
    return out
}

// endregion
