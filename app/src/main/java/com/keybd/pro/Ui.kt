package com.keybd.pro

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.TextView

fun Context.dp(v: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
fun Context.dpi(v: Float): Int = dp(v).toInt()
fun View.dp(v: Float): Float = context.dp(v)
fun View.dpi(v: Float): Int = context.dpi(v)

fun withAlpha(color: Int, alpha: Int): Int = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

fun roundRect(color: Int, radius: Float, stroke: Int = 0, strokeColor: Int = 0): GradientDrawable =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
        if (stroke > 0) setStroke(stroke, strokeColor)
    }

/** Temaya uygun dokunma efektli düz buton. */
fun Context.flatButton(text: String, theme: KbTheme, size: Float = 18f, bg: Int = Color.TRANSPARENT, radius: Float = dp(8f)): TextView =
    TextView(this).apply {
        this.text = text
        gravity = Gravity.CENTER
        setTextColor(theme.stripText)
        textSize = size
        isClickable = true
        isFocusable = false
        background = RippleDrawable(ColorStateList.valueOf(withAlpha(theme.stripText, 50)), roundRect(bg, radius), roundRect(Color.WHITE, radius))
    }

/** Kelime karakteri mi (harf, rakam). */
fun isWordChar(cp: Int): Boolean = Character.isLetterOrDigit(cp) || Character.getType(cp) == Character.NON_SPACING_MARK.toInt()

fun isEmoji(s: String): Boolean {
    if (s.isEmpty()) return false
    val cp = s.codePointAt(0)
    return cp >= 0x1F000 || cp in 0x2600..0x27BF || cp in 0x2190..0x21FF || cp in 0x2B00..0x2BFF || cp == 0x00A9 || cp == 0x00AE || cp in 0x2300..0x23FF
}

/** Fotoğrafı alanı dolduracak şekilde (center-crop) çizer, üstüne karartma uygular. */
class CropDrawable(private val bmp: android.graphics.Bitmap, private val base: Int, private val dim: Int) : android.graphics.drawable.Drawable() {
    private val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG)
    private val dst = android.graphics.RectF()

    override fun draw(c: android.graphics.Canvas) {
        val b = bounds
        c.drawColor(base)
        val s = maxOf(b.width() / bmp.width.toFloat(), b.height() / bmp.height.toFloat())
        val w = bmp.width * s
        val h = bmp.height * s
        dst.set(b.exactCenterX() - w / 2f, b.exactCenterY() - h / 2f, b.exactCenterX() + w / 2f, b.exactCenterY() + h / 2f)
        c.save()
        c.clipRect(b)
        c.drawBitmap(bmp, null, dst, paint)
        c.restore()
        if (dim > 0) c.drawColor(Color.argb(dim, 0, 0, 0))
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(cf: android.graphics.ColorFilter?) {
        paint.colorFilter = cf
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = android.graphics.PixelFormat.OPAQUE
}
