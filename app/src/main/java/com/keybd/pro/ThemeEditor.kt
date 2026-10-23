package com.keybd.pro

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import java.io.File
import kotlin.math.max
import kotlin.math.min

/** "Kendi temanı oluştur": renk seçimi, arka plan fotoğrafı ve canlı önizleme. */
class ThemeEditor(private val a: Activity, private val prefs: Prefs, private val pickImage: () -> Unit, private val onApplied: () -> Unit) {

    private class Slot(val key: String, val label: String, var color: Int)

    private val sp = prefs.sp
    private val slots = listOf(
        Slot(Themes.CUSTOM_BG, "Arka plan", sp.getInt(Themes.CUSTOM_BG, Color.parseColor("#101820"))),
        Slot(Themes.CUSTOM_KEY, "Harf tuşları", sp.getInt(Themes.CUSTOM_KEY, Color.parseColor("#2A3A4A"))),
        Slot(Themes.CUSTOM_FN, "Özel tuşlar", sp.getInt(Themes.CUSTOM_FN, Color.parseColor("#1D2935"))),
        Slot(Themes.CUSTOM_TEXT, "Yazı", sp.getInt(Themes.CUSTOM_TEXT, Color.WHITE)),
        Slot(Themes.CUSTOM_ACCENT, "Vurgu (Enter)", sp.getInt(Themes.CUSTOM_ACCENT, Color.parseColor("#FFB300"))),
    )
    private var image = sp.getBoolean(Themes.CUSTOM_IMAGE, false) && bgFile().exists()
    private var keyAlpha = sp.getInt(Themes.CUSTOM_KEY_ALPHA, 170)
    private var dim = sp.getInt(Themes.CUSTOM_DIM, 30)
    private var bitmap: Bitmap? = null
    private lateinit var preview: PreviewView
    private val swatches = HashMap<String, View>()
    private var imageStatus: TextView? = null

    private fun bgFile() = File(a.filesDir, Themes.BG_FILE)
    private fun dp(v: Float) = a.dp(v)
    private fun dpi(v: Float) = a.dpi(v)

    fun show() {
        if (image) bitmap = decode(bgFile())
        val box = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(20f), dpi(8f), dpi(20f), dpi(8f))
        }
        preview = PreviewView(a)
        box.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpi(150f)).apply { bottomMargin = dpi(10f) })

        // Hazır renk takımları
        val presets = listOf(
            "Gece" to intArrayOf(0xFF101820.toInt(), 0xFF2A3A4A.toInt(), 0xFF1D2935.toInt(), Color.WHITE, 0xFFFFB300.toInt()),
            "Neon" to intArrayOf(0xFF0A0014.toInt(), 0xFF1E0A3C.toInt(), 0xFF140628.toInt(), 0xFF00F5FF.toInt(), 0xFFFF00C8.toInt()),
            "Kahve" to intArrayOf(0xFF3E2723.toInt(), 0xFF5D4037.toInt(), 0xFF4E342E.toInt(), 0xFFFFF3E0.toInt(), 0xFFFFAB40.toInt()),
            "Nane" to intArrayOf(0xFFE0F2F1.toInt(), Color.WHITE, 0xFFB2DFDB.toInt(), 0xFF004D40.toInt(), 0xFF00897B.toInt()),
            "Kan" to intArrayOf(0xFF120000.toInt(), 0xFF3B0000.toInt(), 0xFF240000.toInt(), Color.WHITE, 0xFFFF1744.toInt()),
        )
        val pr = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
        for ((name, cs) in presets) {
            val b = TextView(a).apply {
                text = name
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(cs[3])
                background = roundRect(cs[1], dp(10f), dpi(2f), cs[4])
                setOnClickListener {
                    for (i in slots.indices) slots[i].color = cs[i]
                    refresh()
                }
            }
            pr.addView(b, LinearLayout.LayoutParams(0, dpi(36f), 1f).apply { setMargins(dpi(2f), 0, dpi(2f), 0) })
        }
        box.addView(pr, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dpi(8f) })

        for (slot in slots) {
            val row = LinearLayout(a).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dpi(6f), 0, dpi(6f))
                isClickable = true
            }
            row.addView(TextView(a).apply { text = slot.label; textSize = 16f }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val sw = View(a)
            swatches[slot.key] = sw
            row.addView(sw, LinearLayout.LayoutParams(dpi(56f), dpi(30f)))
            row.setOnClickListener {
                ColorPicker(a, slot.color) { c ->
                    slot.color = c
                    refresh()
                }.show()
            }
            box.addView(row)
        }

        imageStatus = TextView(a).apply { textSize = 13f; setPadding(0, dpi(10f), 0, dpi(4f)) }
        box.addView(imageStatus)
        val imgRow = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
        imgRow.addView(TextView(a).apply {
            text = "📷 Fotoğraf seç"
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, dpi(10f), 0, dpi(10f))
            setOnClickListener { pickImage() }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        imgRow.addView(TextView(a).apply {
            text = "Fotoğrafı kaldır"
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, dpi(10f), 0, dpi(10f))
            setOnClickListener {
                image = false
                bitmap = null
                refresh()
            }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        box.addView(imgRow)
        box.addView(slider("Tuş saydamlığı (fotoğrafta)", 40, 255, keyAlpha) { keyAlpha = it; refresh() })
        box.addView(slider("Fotoğrafı karart", 0, 80, dim) { dim = it; refresh() })

        refresh()
        AlertDialog.Builder(a)
            .setTitle("🎨 Kendi temanı oluştur")
            .setView(ScrollView(a).apply { addView(box) })
            .setPositiveButton("Uygula") { _, _ -> apply() }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    private fun slider(label: String, min: Int, max: Int, value: Int, onChange: (Int) -> Unit): View {
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dpi(6f), 0, 0) }
        col.addView(TextView(a).apply { text = label; textSize = 14f })
        col.addView(SeekBar(a).apply {
            this.max = max - min
            progress = value - min
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser) onChange(p + min) }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        })
        return col
    }

    private fun refresh() {
        for (s in slots) swatches[s.key]?.background = roundRect(s.color, dp(8f), dpi(1f), Color.GRAY)
        imageStatus?.text = if (image) "Arka plan: fotoğraf ✓" else "Arka plan: düz renk (isterseniz fotoğraf seçin)"
        preview.invalidate()
    }

    /** Seçilen fotoğrafı küçültüp uygulama klasörüne kaydeder. */
    fun onImagePicked(uri: Uri) {
        try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            a.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            var sample = 1
            while (max(opts.outWidth, opts.outHeight) / sample > 2400) sample *= 2
            val bmp = a.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: throw IllegalStateException()
            val scale = min(1f, 1600f / max(bmp.width, bmp.height))
            val out = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
            bgFile().outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            bitmap = out
            image = true
            refresh()
        } catch (e: Exception) {
            android.widget.Toast.makeText(a, "Fotoğraf açılamadı", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun apply() {
        val e = sp.edit()
        for (s in slots) e.putInt(s.key, s.color)
        e.putBoolean(Themes.CUSTOM_IMAGE, image)
        e.putInt(Themes.CUSTOM_KEY_ALPHA, keyAlpha)
        e.putInt(Themes.CUSTOM_DIM, dim)
        e.putString(Prefs.THEME, "custom")
        e.putLong(Themes.CUSTOM_VER, System.currentTimeMillis())
        e.apply()
        onApplied()
    }

    private fun decode(f: File): Bitmap? = try {
        BitmapFactory.decodeFile(f.path)
    } catch (_: Throwable) {
        null
    }

    /** Klavyenin küçük bir canlı önizlemesi. */
    private inner class PreviewView(ctx: Context) : View(ctx) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val r = RectF()

        override fun onDraw(c: Canvas) {
            val bg = slots[0].color
            val bmp = bitmap
            if (image && bmp != null) {
                CropDrawable(bmp, bg, dim * 255 / 100).apply { setBounds(0, 0, width, height) }.draw(c)
            } else c.drawColor(bg)
            val alpha = if (image) keyAlpha else 255
            val rows = listOf("QWERTYUIOP", "ASDFGHJKL", "⇧ZXCVBNM⌫")
            val kw = width / 10f
            val kh = height / 4f
            p.textAlign = Paint.Align.CENTER
            for ((ri, row) in rows.withIndex()) {
                val off = (width - row.length * kw) / 2f
                for ((ci, ch) in row.withIndex()) {
                    val fn = ch == '⇧' || ch == '⌫'
                    r.set(off + ci * kw + dp(2f), ri * kh + dp(3f), off + (ci + 1) * kw - dp(2f), (ri + 1) * kh - dp(3f))
                    p.color = withAlpha(if (fn) slots[2].color else slots[1].color, alpha)
                    c.drawRoundRect(r, dp(6f), dp(6f), p)
                    p.color = slots[3].color
                    p.textSize = dp(13f)
                    c.drawText(ch.toString(), r.centerX(), r.centerY() - (p.descent() + p.ascent()) / 2f, p)
                }
            }
            // Alt satır: boşluk + Enter
            r.set(width * 0.2f, 3 * kh + dp(3f), width * 0.78f, 4 * kh - dp(3f))
            p.color = withAlpha(slots[1].color, alpha)
            c.drawRoundRect(r, dp(6f), dp(6f), p)
            r.set(width * 0.8f, 3 * kh + dp(3f), width - dp(2f), 4 * kh - dp(3f))
            p.color = slots[4].color
            c.drawRoundRect(r, dp(6f), dp(6f), p)
            val l = (0.299 * Color.red(slots[4].color) + 0.587 * Color.green(slots[4].color) + 0.114 * Color.blue(slots[4].color)) / 255
            p.color = if (l > 0.6) Color.BLACK else Color.WHITE
            c.drawText("↵", r.centerX(), r.centerY() - (p.descent() + p.ascent()) / 2f, p)
        }
    }
}

/** Basit renk seçici: hazır renkler, ton/doygunluk/parlaklık ve HEX kodu. */
class ColorPicker(private val a: Activity, initial: Int, private val onPick: (Int) -> Unit) {
    private val hsv = FloatArray(3).also { Color.colorToHSV(initial, it) }
    private var color = initial

    fun show() {
        val box = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(a.dpi(20f), a.dpi(8f), a.dpi(20f), 0)
        }
        val sw = View(a)
        box.addView(sw, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, a.dpi(48f)))
        val hex = EditText(a).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            hint = "#RRGGBB"
        }
        val bars = ArrayList<SeekBar>()
        var updating = false
        fun sync(fromHex: Boolean = false) {
            updating = true
            sw.background = roundRect(color, a.dp(10f), a.dpi(1f), Color.GRAY)
            if (!fromHex) hex.setText(String.format("#%06X", 0xFFFFFF and color))
            bars.getOrNull(0)?.progress = hsv[0].toInt()
            bars.getOrNull(1)?.progress = (hsv[1] * 100).toInt()
            bars.getOrNull(2)?.progress = (hsv[2] * 100).toInt()
            updating = false
        }
        val palette = intArrayOf(
            0xFF000000.toInt(), 0xFF424242.toInt(), 0xFF9E9E9E.toInt(), 0xFFFFFFFF.toInt(), 0xFFF44336.toInt(), 0xFFE91E63.toInt(),
            0xFF9C27B0.toInt(), 0xFF673AB7.toInt(), 0xFF3F51B5.toInt(), 0xFF2196F3.toInt(), 0xFF03A9F4.toInt(), 0xFF00BCD4.toInt(),
            0xFF009688.toInt(), 0xFF4CAF50.toInt(), 0xFF8BC34A.toInt(), 0xFFCDDC39.toInt(), 0xFFFFEB3B.toInt(), 0xFFFFC107.toInt(),
            0xFFFF9800.toInt(), 0xFFFF5722.toInt(), 0xFF795548.toInt(), 0xFF607D8B.toInt(), 0xFF0D1B2A.toInt(), 0xFF1B263B.toInt(),
        )
        var row: LinearLayout? = null
        for ((i, c) in palette.withIndex()) {
            if (i % 6 == 0) {
                row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
                box.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, a.dpi(40f)).apply { topMargin = a.dpi(6f) })
            }
            row!!.addView(View(a).apply {
                background = roundRect(c, a.dp(8f), a.dpi(1f), Color.GRAY)
                setOnClickListener {
                    color = c
                    Color.colorToHSV(c, hsv)
                    sync()
                }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { setMargins(a.dpi(3f), 0, a.dpi(3f), 0) })
        }
        for ((label, mx) in listOf("Ton" to 360, "Doygunluk" to 100, "Parlaklık" to 100)) {
            box.addView(TextView(a).apply { text = label; textSize = 13f; setPadding(0, a.dpi(8f), 0, 0) })
            val sb = SeekBar(a).apply { max = mx }
            sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    if (updating || !fromUser) return
                    when (bars.indexOf(s)) {
                        0 -> hsv[0] = p.toFloat()
                        1 -> hsv[1] = p / 100f
                        2 -> hsv[2] = p / 100f
                    }
                    color = Color.HSVToColor(hsv)
                    sync()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
            bars += sb
            box.addView(sb)
        }
        box.addView(hex)
        hex.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, af: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (updating) return
                val t = s?.toString()?.trim() ?: return
                if (Regex("^#?[0-9A-Fa-f]{6}$").matches(t)) {
                    color = Color.parseColor(if (t.startsWith("#")) t else "#$t")
                    Color.colorToHSV(color, hsv)
                    sync(fromHex = true)
                }
            }
        })
        sync()
        AlertDialog.Builder(a).setTitle("Renk seç")
            .setView(ScrollView(a).apply { addView(box) })
            .setPositiveButton("Tamam") { _, _ -> onPick(color) }
            .setNegativeButton("Vazgeç", null)
            .show()
    }
}
