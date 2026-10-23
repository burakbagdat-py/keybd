package com.keybd.pro

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.io.File

class SettingsActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var list: LinearLayout
    private lateinit var status1: TextView
    private lateinit var status2: TextView
    private val accent = Color.parseColor("#1F6FEB")
    private var textColor = Color.BLACK
    private var subColor = Color.GRAY
    private var cardColor = Color.WHITE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val night = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        textColor = if (night) Color.parseColor("#E6EDF3") else Color.parseColor("#1F2328")
        subColor = if (night) Color.parseColor("#8B949E") else Color.parseColor("#59636E")
        cardColor = if (night) Color.parseColor("#161B22") else Color.WHITE
        val bg = if (night) Color.parseColor("#0D1117") else Color.parseColor("#F3F5F8")

        val scroll = ScrollView(this).apply { setBackgroundColor(bg) }
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(14f), dpi(10f), dpi(14f), dpi(40f))
        }
        scroll.addView(list)
        setContentView(scroll)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            scroll.setOnApplyWindowInsetsListener { v, insets ->
                val sb = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                v.setPadding(sb.left, sb.top, sb.right, sb.bottom)
                insets
            }
        }
        build()
        if (intent?.getBooleanExtra(EXTRA_MIC, false) == true) requestMic()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) refreshStatus()
    }

    // ------------------------------------------------------------------ arayüz yardımcıları

    private var currentCard: LinearLayout? = null

    private fun header(title: String) {
        list.addView(TextView(this).apply {
            text = title
            setTextColor(accent)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dpi(6f), dpi(18f), 0, dpi(6f))
        })
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundRect(cardColor, dp(16f))
            setPadding(dpi(4f), dpi(4f), dpi(4f), dpi(4f))
        }
        list.addView(card)
        currentCard = card
    }

    private fun add(v: View) {
        currentCard?.addView(v) ?: list.addView(v)
    }

    private fun titleView(title: String, summary: String?): LinearLayout {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextView(this).apply {
            text = title
            setTextColor(textColor)
            textSize = 16f
        })
        if (summary != null) col.addView(TextView(this).apply {
            text = summary
            setTextColor(subColor)
            textSize = 13f
        })
        return col
    }

    private fun rowContainer(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dpi(14f), dpi(12f), dpi(14f), dpi(12f))
        minimumHeight = dpi(56f)
        isClickable = true
        background = android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(withAlpha(textColor, 30)), null,
            roundRect(Color.WHITE, dp(14f)))
    }

    private fun switch(title: String, summary: String?, key: String, def: Boolean) {
        val row = rowContainer()
        row.addView(titleView(title, summary), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val sw = Switch(this).apply { isChecked = prefs.sp.getBoolean(key, def) }
        sw.setOnCheckedChangeListener { _, c -> prefs.sp.edit().putBoolean(key, c).apply() }
        row.addView(sw)
        row.setOnClickListener { sw.toggle() }
        add(row)
    }

    private fun slider(title: String, key: String, min: Int, max: Int, def: Int, fmt: (Int) -> String) {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(14f), dpi(10f), dpi(14f), dpi(6f))
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        top.addView(TextView(this).apply { text = title; setTextColor(textColor); textSize = 16f },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val value = TextView(this).apply { setTextColor(accent); textSize = 15f; typeface = Typeface.DEFAULT_BOLD }
        top.addView(value)
        col.addView(top)
        val sb = SeekBar(this).apply {
            this.max = max - min
            progress = prefs.sp.getInt(key, def) - min
        }
        value.text = fmt(sb.progress + min)
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { value.text = fmt(p + min) }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) { prefs.sp.edit().putInt(key, (s?.progress ?: 0) + min).apply() }
        })
        col.addView(sb)
        add(col)
    }

    private fun action(title: String, summary: String?, f: () -> Unit): LinearLayout {
        val row = rowContainer()
        row.addView(titleView(title, summary), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(TextView(this).apply { text = "›"; textSize = 22f; setTextColor(subColor) })
        row.setOnClickListener { f() }
        add(row)
        return row
    }

    private fun button(text: String, primary: Boolean, f: () -> Unit): TextView = TextView(this).apply {
        this.text = text
        gravity = Gravity.CENTER
        textSize = 15f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(if (primary) Color.WHITE else accent)
        background = roundRect(if (primary) accent else Color.TRANSPARENT, dp(24f), if (primary) 0 else dpi(1.5f), accent)
        setPadding(dpi(18f), dpi(12f), dpi(18f), dpi(12f))
        setOnClickListener { f() }
    }

    // ------------------------------------------------------------------ sayfa

    private fun build() {
        list.addView(TextView(this).apply {
            text = "⌨️ Keybd"
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textColor)
            setPadding(dpi(6f), dpi(8f), 0, 0)
        })
        list.addView(TextView(this).apply {
            text = "Gelişmiş, gizlilik dostu klavye — internet izni yok, her şey cihazında kalır."
            textSize = 14f
            setTextColor(subColor)
            setPadding(dpi(6f), dpi(2f), 0, dpi(4f))
        })

        // Kurulum
        header("KURULUM")
        val setup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(14f), dpi(12f), dpi(14f), dpi(14f))
        }
        status1 = TextView(this).apply { textSize = 15f; setTextColor(textColor) }
        status2 = TextView(this).apply { textSize = 15f; setTextColor(textColor); setPadding(0, dpi(6f), 0, dpi(12f)) }
        setup.addView(status1)
        setup.addView(status2)
        val btns = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        btns.addView(button("1. Etkinleştir", true) {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dpi(8f) })
        btns.addView(button("2. Seç", true) {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        setup.addView(btns)
        val test = EditText(this).apply {
            hint = "Klavyeyi burada deneyin…"
            setTextColor(textColor)
            setHintTextColor(subColor)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 2
        }
        setup.addView(test, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dpi(10f) })
        add(setup)

        // Diller
        header("DİLLER VE DÜZENLER")
        val enabled = prefs.layouts.toMutableSet()
        for (id in Layouts.ALL) {
            val row = rowContainer()
            row.addView(titleView(Layouts.name(id), when (id) {
                Layouts.TR_Q -> "Standart Türkçe QWERTY"
                Layouts.TR_F -> "Türkçe F klavye"
                else -> "English QWERTY"
            }), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val cb = CheckBox(this).apply { isChecked = id in enabled }
            cb.setOnCheckedChangeListener { b, c ->
                if (c) enabled += id else enabled -= id
                if (enabled.isEmpty()) {
                    enabled += id
                    b.isChecked = true
                    toast("En az bir düzen açık olmalı")
                }
                prefs.sp.edit().putString(Prefs.LAYOUTS, Layouts.ALL.filter { it in enabled }.joinToString(",")).apply()
            }
            row.addView(cb)
            row.setOnClickListener { cb.toggle() }
            add(row)
        }
        add(TextView(this).apply {
            text = "🌐 tuşuna dokunarak düzenler arasında geçiş yapın; uzun basınca diğer klavyeler listelenir."
            textSize = 13f
            setTextColor(subColor)
            setPadding(dpi(14f), 0, dpi(14f), dpi(10f))
        })

        // Görünüm
        header("GÖRÜNÜM")
        val themeRow = action("Tema", Themes.get(this, prefs.theme).name) {}
        themeRow.setOnClickListener { pickTheme(themeRow) }
        slider("Klavye yüksekliği", Prefs.HEIGHT, 70, 140, 100) { "%$it" }
        slider("Yazı boyutu", Prefs.FONT_SCALE, 70, 140, 100) { "%$it" }
        slider("Tuş köşe yuvarlaklığı", Prefs.RADIUS, 0, 24, 8) { "${it}dp" }
        slider("Tuş aralığı", Prefs.GAP, 0, 10, 4) { "${it}dp" }
        switch("Tuş arka planları", "Kapalıysa düz (çerçevesiz) görünüm", Prefs.BORDERS, true)
        switch("Sayı satırı", "Harflerin üstünde sürekli rakam satırı", Prefs.NUMROW, false)
        switch("Tuş ipuçları", "Uzun basınca çıkan rakamı tuş köşesinde göster", Prefs.HINTS, true)
        switch("Basınca büyüt", "Basılan harfi balon içinde göster", Prefs.PREVIEW, true)
        switch("Emoji tuşu", "Alt satırda hızlı emoji tuşu", Prefs.EMOJI_KEY, true)

        // Yazma
        header("AKILLI YAZMA")
        switch("Kelime önerileri", "Yazarken üst şeritte öneriler", Prefs.SUGGEST, true)
        switch("Otomatik düzeltme", "Boşluk/noktalamada yazım hatalarını ve Türkçe karakterleri (gorusuruz → görüşürüz) düzelt. Geri silme ile geri alınır.", Prefs.AUTOCORRECT, true)
        switch("Sonraki kelime tahmini", "Alışkanlıklarınızdan sıradaki kelimeyi tahmin et", Prefs.NEXTWORD, true)
        switch("Emoji önerileri", "\"kalp\" yazınca ❤️ öner", Prefs.EMOJI_SUGGEST, true)
        switch("Otomatik büyük harf", "Cümle başlarında büyük harf", Prefs.AUTOCAP, true)
        switch("Çift boşluk = nokta", "İki kez boşluğa basınca \". \" ekle", Prefs.DOUBLESPACE, true)
        switch("Akıllı noktalama", "Öneriden sonra gelen noktalamadan önceki boşluğu kaldır", Prefs.SMART_PUNCT, true)
        switch("Kelimeleri öğren", "Yeni kelimeleri ve yazım alışkanlıklarını öğren (yalnızca cihazda)", Prefs.LEARN, true)
        action("Kısayollar (metin genişletme)", "Örn: \"slm\" → \"selam\"") { editShortcuts() }
        action("Öğrenilen verileri sil", "Öğrenilen kelimeleri ve tahminleri sıfırla") {
            AlertDialog.Builder(this).setTitle("Öğrenilenler silinsin mi?")
                .setMessage("Klavyenin öğrendiği tüm kelimeler ve tahminler silinecek.")
                .setPositiveButton("Sil") { _, _ ->
                    File(filesDir, "user_tr.txt").delete(); File(filesDir, "user_en.txt").delete()
                    File(filesDir, "bigram_tr.txt").delete(); File(filesDir, "bigram_en.txt").delete()
                    prefs.sp.edit().putLong(Prefs.DICT_RESET, System.currentTimeMillis()).apply()
                    toast("Silindi")
                }.setNegativeButton("Vazgeç", null).show()
        }

        // Hareketler
        header("HAREKETLER")
        switch("Kaydırarak yazma", "Parmağınızı kaldırmadan harflerin üzerinden geçerek yazın", Prefs.GLIDE, true)
        switch("Kaydırma izi", "Kaydırarak yazarken iz çiz", Prefs.GLIDE_TRAIL, true)
        switch("Boşlukta imleç kontrolü", "Boşluk tuşunda sağa-sola kaydırarak imleci taşıyın", Prefs.SPACE_CURSOR, true)
        switch("Silme tuşunda kaydırma", "⌫ tuşundan sola kaydırarak kelimeleri seçip silin", Prefs.DEL_SWIPE, true)
        slider("Uzun basma süresi", Prefs.LONGPRESS, 150, 700, 300) { "${it} ms" }

        // Geri bildirim
        header("SES VE TİTREŞİM")
        switch("Tuş titreşimi", null, Prefs.VIBRATE, true)
        slider("Titreşim süresi", Prefs.VIBRATE_MS, 1, 60, 12) { "${it} ms" }
        switch("Tuş sesi", null, Prefs.SOUND, false)
        slider("Ses düzeyi", Prefs.SOUND_VOL, 0, 100, 40) { "%$it" }

        // Pano / gizlilik
        header("PANO VE GİZLİLİK")
        switch("Pano geçmişi", "Kopyaladıklarınızı sakla (en fazla ${ClipHistory.MAX}, sabitlenenler hariç)", Prefs.CLIP, true)
        switch("Gizli mod", "Hiçbir şey öğrenilmez, pano kaydedilmez", Prefs.INCOGNITO, false)
        action("Mikrofon izni", "Sesli yazma (🎤) için gerekli") { requestMic() }

        // Hakkında
        header("İPUÇLARI")
        add(TextView(this).apply {
            text = """
• Harflere uzun basın: rakamlar ve aksanlı harfler (ş, ğ, é, ß…)
• Shift'e iki kez dokunun: BÜYÜK HARF kilidi
• Boşluğa uzun basın: klavye seçici
• Boşluk tuşunu aşağı kaydırın: klavyeyi gizle
• Öneriye uzun basın: öğrenilen kelimeyi unut
• Otomatik düzeltmeden hemen sonra ⌫: düzeltmeyi geri al
• ⋯ menüsü: emoji, pano, metin düzenleme, yazı stilleri, tek el modu, gizli mod, tema
• ?123 veya ☺ tuşuna uzun basın: bu ayarlar
            """.trimIndent()
            textSize = 14f
            setTextColor(textColor)
            setLineSpacing(0f, 1.25f)
            setPadding(dpi(14f), dpi(12f), dpi(14f), dpi(12f))
        })
        list.addView(TextView(this).apply {
            text = "Keybd 1.0.0"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(subColor)
            setPadding(0, dpi(20f), 0, 0)
        })
    }

    private fun refreshStatus() {
        if (!::status1.isInitialized) return
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val enabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: ""
        val selected = current.startsWith("$packageName/")
        status1.text = (if (enabled) "✅" else "⬜") + "  Keybd etkinleştirildi"
        status2.text = (if (selected) "✅" else "⬜") + "  Keybd varsayılan klavye olarak seçildi"
    }

    private fun pickTheme(row: LinearLayout) {
        val themes = Themes.all(this)
        val names = themes.map { it.name }.toTypedArray()
        val cur = themes.indexOfFirst { it.id == prefs.theme }.coerceAtLeast(0)
        AlertDialog.Builder(this).setTitle("Tema")
            .setSingleChoiceItems(names, cur) { d, which ->
                prefs.sp.edit().putString(Prefs.THEME, themes[which].id).apply()
                ((row.getChildAt(0) as LinearLayout).getChildAt(1) as TextView).text = themes[which].name
                d.dismiss()
            }.show()
    }

    private fun editShortcuts() {
        val map = prefs.shortcuts.toMutableMap()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(20f), dpi(8f), dpi(20f), 0)
        }
        val listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun fill() {
            listBox.removeAllViews()
            if (map.isEmpty()) listBox.addView(TextView(this).apply { text = "Kısayol yok"; setTextColor(subColor) })
            for ((k, v) in map.toSortedMap()) {
                val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                r.addView(TextView(this).apply { text = "$k  →  $v"; textSize = 15f; setTextColor(textColor) },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                r.addView(TextView(this).apply {
                    text = "✕"; textSize = 18f; setTextColor(Color.parseColor("#E5534B")); setPadding(dpi(12f), dpi(8f), dpi(4f), dpi(8f))
                    setOnClickListener { map.remove(k); prefs.shortcuts = map; fill() }
                })
                listBox.addView(r)
            }
        }
        val key = EditText(this).apply { hint = "Kısayol (örn: slm)"; setSingleLine() }
        val value = EditText(this).apply { hint = "Açılımı (örn: selam)" }
        val addBtn = button("Ekle", true) {
            val k = key.text.toString().trim().lowercase()
            val v = value.text.toString().trim()
            if (k.isNotEmpty() && v.isNotEmpty() && !k.contains(' ')) {
                map[k] = v
                prefs.shortcuts = map
                key.setText(""); value.setText("")
                fill()
            } else toast("Kısayol tek kelime olmalı")
        }
        container.addView(key)
        container.addView(value)
        container.addView(addBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dpi(6f); bottomMargin = dpi(12f)
        })
        container.addView(listBox)
        fill()
        AlertDialog.Builder(this).setTitle("Kısayollar")
            .setView(ScrollView(this).apply { addView(container) })
            .setPositiveButton("Tamam", null).show()
    }

    private fun requestMic() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            toast("Mikrofon izni zaten verilmiş")
            return
        }
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 7)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 7) toast(if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) "Mikrofon izni verildi 🎤" else "İzin verilmedi")
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    companion object {
        const val EXTRA_MIC = "mic"
    }
}
