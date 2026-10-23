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
    private lateinit var btn1: TextView
    private lateinit var btn2: TextView
    private lateinit var readyText: TextView
    private var awaitingPick = false
    private var updateStatus: TextView? = null
    private var checking = false
    private var statsView: TextView? = null
    private var themeEditor: ThemeEditor? = null
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
        if (prefs.sp.getBoolean(AUTO_UPDATE, true) &&
            System.currentTimeMillis() - prefs.sp.getLong(LAST_CHECK, 0) > 6 * 3600_000L) checkUpdate(silent = true)
    }

    private val statsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != null && key.startsWith("st_")) runOnUiThread { refreshStats() }
    }

    override fun onPause() {
        super.onPause()
        prefs.sp.unregisterOnSharedPreferenceChangeListener(statsListener)
    }

    override fun onResume() {
        super.onResume()
        prefs.sp.registerOnSharedPreferenceChangeListener(statsListener)
        refreshStatus()
        refreshStats()
        // Kurulum izni verilirken Android uygulamayı yeniden başlatır; güncellemeye kaldığı yerden devam et.
        if (prefs.sp.getBoolean(PENDING_UPDATE, false) &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || packageManager.canRequestPackageInstalls())) {
            prefs.sp.edit().putBoolean(PENDING_UPDATE, false).apply()
            checkUpdate(silent = false, install = true)
        }
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
            text = "Gelişmiş, gizlilik dostu klavye — yazdıklarınız asla cihazınızdan çıkmaz."
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
        btn1 = button("1. Etkinleştir", true) { openEnableSettings() }
        btns.addView(btn1, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dpi(8f) })
        btn2 = button("2. Seç", true) {
            if (!isEnabled()) {
                toast("Önce 1. adımda Keybd'yi etkinleştirin")
                openEnableSettings()
            } else showPicker()
        }
        btns.addView(btn2, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        setup.addView(btns)
        readyText = TextView(this).apply {
            textSize = 14f
            setTextColor(subColor)
            setPadding(0, dpi(10f), 0, 0)
        }
        setup.addView(readyText)
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
        action("🎨 Kendi temanı oluştur", "Renkleri seç, arka plana fotoğrafını koy") {
            val ed = ThemeEditor(this, prefs, pickImage = {
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"), REQ_IMAGE)
            }, onApplied = {
                ((themeRow.getChildAt(0) as LinearLayout).getChildAt(1) as TextView).text = "Özel temam"
                toast("Tema uygulandı 🎨")
            })
            themeEditor = ed
            ed.show()
        }
        action("🖤 Venom Simbiyot temasını uygula", "Canlı dokunaçlar, sıvı gibi yayılan tuşlar, simbiyot iplikleri, ıslak sesler ve titreşim") {
            prefs.sp.edit().putString(Prefs.THEME, "venom").putString(Prefs.SOUND_PACK, "venom").putBoolean(Prefs.SOUND, true).apply()
            ((themeRow.getChildAt(0) as LinearLayout).getChildAt(1) as TextView).text = "Venom Simbiyot"
            toast("Simbiyot bağlandı 🕷️ Venom sesleri de açıldı (Ses ayarlarından değiştirebilirsiniz)")
        }
        switch("Kompakt mod", "Daha alçak tuşlar, dar aralıklar ve ince üst menü", Prefs.COMPACT, false)
        slider("Klavye yüksekliği (tuş boyu)", Prefs.HEIGHT, 60, 150, 100) { "%$it" }
        slider("Klavye genişliği (tuş eni)", Prefs.KB_WIDTH, 50, 100, 100) { "%$it" }
        slider("Alt boşluk (klavyeyi yukarı al)", Prefs.BOTTOM_PAD, 0, 48, 0) { "${it}dp" }
        action("↺ Boyutları varsayılana döndür", "Yükseklik, genişlik, yazı, köşe, aralık, menü ve tek el ayarları") { resetKeys(Prefs.SIZE_KEYS, "Boyutlar sıfırlandı") }
        slider("Yazı boyutu", Prefs.FONT_SCALE, 70, 140, 100) { "%$it" }
        slider("Tuş köşe yuvarlaklığı", Prefs.RADIUS, 0, 24, 8) { "${it}dp" }
        slider("Tuş aralığı", Prefs.GAP, 0, 10, 4) { "${it}dp" }
        switch("Tuş arka planları", "Kapalıysa düz (çerçevesiz) görünüm", Prefs.BORDERS, true)
        switch("Sayı satırı", "Harflerin üstünde sürekli rakam satırı", Prefs.NUMROW, false)
        switch("Tuş ipuçları", "Uzun basınca çıkan rakamı tuş köşesinde göster", Prefs.HINTS, true)
        switch("Basınca büyüt", "Basılan harfi balon içinde göster", Prefs.PREVIEW, true)
        switch("Emoji tuşu", "Alt satırda hızlı emoji tuşu", Prefs.EMOJI_KEY, true)

        header("ÜST MENÜ")
        val posNames = arrayOf("Üstte (şerit)", "Solda (yan menü)", "Sağda (yan menü)")
        val posRow = action("Araç çubuğu konumu", posNames[prefs.toolbarPos.coerceIn(0, 2)]) {}
        posRow.setOnClickListener {
            AlertDialog.Builder(this).setTitle("Araç çubuğu konumu")
                .setSingleChoiceItems(posNames, prefs.toolbarPos.coerceIn(0, 2)) { d, w ->
                    prefs.sp.edit().putInt(Prefs.TOOLBAR_POS, w).apply()
                    ((posRow.getChildAt(0) as LinearLayout).getChildAt(1) as TextView).text = posNames[w]
                    d.dismiss()
                }.show()
        }
        slider("Menü yüksekliği", Prefs.STRIP_HEIGHT, 30, 64, 44) { "${it}dp" }
        switch("Menüyü gizle", "Üst şeridi ince bir çizgiye indirir; dokununca açılır (araç çubuğunda ▾ ile de gizlenir)", Prefs.STRIP_COLLAPSED, false)
        add(TextView(this).apply {
            text = "İpucu: araç çubuğundaki ⇕ düğmesiyle klavyeyi yazarken canlı olarak büyütüp küçültebilirsiniz."
            textSize = 13f
            setTextColor(subColor)
            setPadding(dpi(14f), 0, dpi(14f), dpi(10f))
        })

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
        switch("Yukarı kaydırınca rakam/simge", "Harfe dokunup yukarı kaydırın: uzun basmadan köşedeki rakamı yazar", Prefs.SWIPE_UP, true)
        switch("Dokunma düzeltmesi", "Parmaklar hedefin biraz altına dokunur; bunu telafi ederek yanlış tuşa basmayı azaltır", Prefs.TOUCH_FIX, true)

        // Geri bildirim
        header("SES VE TİTREŞİM")
        switch("Tuş titreşimi", null, Prefs.VIBRATE, true)
        slider("Titreşim süresi", Prefs.VIBRATE_MS, 1, 60, 12) { "${it} ms" }
        switch("Tuş sesi", null, Prefs.SOUND, false)
        val packRow = action("Ses paketi", SoundPack.name(prefs.soundPack)) {}
        packRow.setOnClickListener {
            val packs = SoundPack.PACKS
            val preview = SoundPack(this)
            AlertDialog.Builder(this).setTitle("Ses paketi (dokununca dinle)")
                .setSingleChoiceItems(packs.map { it.second }.toTypedArray(), packs.indexOfFirst { it.first == prefs.soundPack }.coerceAtLeast(0)) { _, w ->
                    val id = packs[w].first
                    prefs.sp.edit().putString(Prefs.SOUND_PACK, id).putBoolean(Prefs.SOUND, true).apply()
                    ((packRow.getChildAt(0) as LinearLayout).getChildAt(1) as TextView).text = packs[w].second
                    if (id != SoundPack.SYSTEM) {
                        // Yükleme bitince örnek bir dizi çal: tuş, tuş, boşluk, enter
                        preview.onReady = {
                            list.post { preview.play(SoundPack.Kind.KEY, 0.8f) }
                            list.postDelayed({ preview.play(SoundPack.Kind.KEY, 0.8f) }, 170)
                            list.postDelayed({ preview.play(SoundPack.Kind.SPACE, 0.8f) }, 350)
                            list.postDelayed({ preview.play(SoundPack.Kind.ENTER, 0.8f) }, 570)
                        }
                        preview.load(id)
                    }
                }
                .setPositiveButton("Tamam", null)
                .setOnDismissListener { list.postDelayed({ preview.release() }, 2500) }
                .show()
        }
        slider("Ses düzeyi", Prefs.SOUND_VOL, 0, 100, 40) { "%$it" }

        // Pano / gizlilik
        header("YAZMA İSTATİSTİKLERİ")
        statsView = TextView(this).apply {
            textSize = 15f
            setTextColor(textColor)
            setLineSpacing(0f, 1.3f)
            setPadding(dpi(14f), dpi(12f), dpi(14f), dpi(8f))
        }
        add(statsView!!)
        action("İstatistikleri sıfırla", null) {
            AlertDialog.Builder(this).setTitle("İstatistikler sıfırlansın mı?")
                .setPositiveButton("Sıfırla") { _, _ -> Stats.reset(prefs.sp); refreshStats() }
                .setNegativeButton("Vazgeç", null).show()
        }
        refreshStats()

        header("YEDEKLEME")
        action("💾 Yedek oluştur", "Ayarlar, kısayollar, öğrenilen kelimeler, tema ve istatistikler tek dosyada") {
            val name = "keybd-yedek-" + java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US).format(java.util.Date()) + ".json"
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json").putExtra(Intent.EXTRA_TITLE, name), REQ_BACKUP)
        }
        action("📂 Yedekten geri yükle", "Yeni telefonda yedek dosyasını seçin") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*"), REQ_RESTORE)
        }

        header("GÜNCELLEME")
        val up = rowContainer()
        val upStatus = TextView(this).apply { text = "Sürüm ${BuildConfig.VERSION_NAME}"; textSize = 13f; setTextColor(subColor) }
        updateStatus = upStatus
        val upCol = titleView("Güncellemeleri denetle", null).apply { addView(upStatus) }
        up.addView(upCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        up.addView(TextView(this).apply { text = "⟳"; textSize = 22f; setTextColor(accent) })
        up.setOnClickListener { checkUpdate(silent = false) }
        add(up)
        switch("Otomatik denetle", "Uygulama açılınca yeni sürüm var mı bak (klavyenin kendisi internete hiç bağlanmaz)", AUTO_UPDATE, true)

        header("PANO VE GİZLİLİK")
        switch("Pano geçmişi", "Kopyaladıklarınızı sakla (en fazla ${ClipHistory.MAX}, sabitlenenler hariç)", Prefs.CLIP, true)
        switch("Gizli mod", "Hiçbir şey öğrenilmez, pano kaydedilmez", Prefs.INCOGNITO, false)
        action("Mikrofon izni", "Sesli yazma (🎤) için gerekli") { requestMic() }

        // Hakkında
        header("SIFIRLAMA")
        action("↺ Boyutları sıfırla", "Klavye çok büyük/küçük olduysa: tüm boyut ayarlarını varsayılana döndürür") {
            resetKeys(Prefs.SIZE_KEYS, "Boyutlar sıfırlandı")
        }
        action("↺ Görünümü sıfırla", "Boyutlar + tema, tuş görünümü ve özel tema renkleri") {
            resetKeys(Prefs.LOOK_KEYS, "Görünüm sıfırlandı")
        }
        action("↺ Tüm ayarları sıfırla", "Arşiv, pano, kısayollar, öğrenilen kelimeler ve istatistikler korunur") {
            AlertDialog.Builder(this).setTitle("Tüm ayarlar sıfırlansın mı?")
                .setMessage("Tüm ayarlar fabrika değerlerine döner.\n\nKorunanlar: arşiv, pano geçmişi, kısayollar, son emojiler, öğrenilen kelimeler ve istatistikler.")
                .setPositiveButton("Sıfırla") { _, _ ->
                    val e = prefs.sp.edit()
                    for (k in prefs.sp.all.keys) if (k !in Prefs.KEEP_ON_RESET && !k.startsWith("st_")) e.remove(k)
                    e.putLong(Themes.CUSTOM_VER, System.currentTimeMillis()).apply()
                    toast("Tüm ayarlar sıfırlandı")
                    recreate()
                }.setNegativeButton("Vazgeç", null).show()
        }

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
• Harfe dokunup yukarı kaydırın: rakam/simge
• Metni seçip Shift'e basın: küçük → Baş Harfler → BÜYÜK
• Kısayollarda {tarih} {saat} {gün} {yarın} kullanın (trh → bugünün tarihi)
• E-posta yazarken @ sonrası gmail.com, hotmail.com… önerilir
            """.trimIndent()
            textSize = 14f
            setTextColor(textColor)
            setLineSpacing(0f, 1.25f)
            setPadding(dpi(14f), dpi(12f), dpi(14f), dpi(12f))
        })
        list.addView(TextView(this).apply {
            text = "Keybd ${BuildConfig.VERSION_NAME}"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(subColor)
            setPadding(0, dpi(20f), 0, 0)
        })
    }

    private fun isEnabled(): Boolean {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        return imm.enabledInputMethodList.any { it.packageName == packageName }
    }

    private fun isSelected(): Boolean {
        val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: ""
        return current.startsWith("$packageName/")
    }

    private fun openEnableSettings() {
        awaitingPick = true
        try {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun showPicker() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
    }

    /** Kurulum sihirbazı: duruma göre sıradaki adımı öne çıkarır, gerekirse seçiciyi kendisi açar. */
    /** Verilen ayarları siler (varsayılana döner) ve ekranı yeniler. */
    private fun resetKeys(keys: List<String>, msg: String) {
        AlertDialog.Builder(this).setTitle("Sıfırlansın mı?")
            .setMessage("Seçilen ayarlar varsayılan değerlerine dönecek.")
            .setPositiveButton("Sıfırla") { _, _ ->
                val e = prefs.sp.edit()
                for (k in keys) e.remove(k)
                e.putLong(Themes.CUSTOM_VER, System.currentTimeMillis()).apply()
                toast("$msg ✓")
                recreate()
            }.setNegativeButton("Vazgeç", null).show()
    }

    private fun refreshStatus() {
        if (!::status1.isInitialized) return
        val enabled = isEnabled()
        val selected = isSelected()
        status1.text = (if (enabled) "✅" else "⬜") + "  Keybd etkinleştirildi"
        status2.text = (if (selected) "✅" else "⬜") + "  Keybd varsayılan klavye olarak seçildi"
        btn1.text = if (enabled) "✓ Etkin" else "1. Etkinleştir"
        btn1.alpha = if (enabled) 0.45f else 1f
        btn2.text = if (selected) "✓ Seçili" else "2. Seç"
        btn2.alpha = if (!enabled || selected) 0.45f else 1f
        readyText.text = when {
            !enabled -> "Adım 1: Açılan listede \"Keybd\" anahtarını açın ve uyarıyı onaylayın. (Android her klavye için bu uyarıyı gösterir.)"
            !selected -> "Adım 2: Çıkan listeden \"Keybd\"i seçin."
            else -> "🎉 Keybd hazır! Aşağıdaki alana dokunup deneyebilirsiniz. Klavyeyi değiştirmek için 🌐 tuşuna uzun basın."
        }
        // Kullanıcı etkinleştirip geri döndüyse seçiciyi otomatik aç.
        if (enabled && !selected && awaitingPick && hasWindowFocus()) {
            awaitingPick = false
            list.postDelayed({ showPicker() }, 300)
        }
    }

    // ------------------------------------------------------------------ güncelleme

    private fun checkUpdate(silent: Boolean, install: Boolean = false) {
        if (checking) return
        checking = true
        updateStatus?.text = "Denetleniyor…"
        Thread {
            val result = try {
                Result.success(Updater.fetchLatest())
            } catch (e: Exception) {
                Result.failure(e)
            }
            runOnUiThread {
                checking = false
                if (isFinishing) return@runOnUiThread
                prefs.sp.edit().putLong(LAST_CHECK, System.currentTimeMillis()).apply()
                val r = result.getOrNull()
                when {
                    result.isFailure -> {
                        updateStatus?.text = "Denetlenemedi (internet bağlantısını kontrol edin)"
                        if (!silent) toast("Güncelleme denetlenemedi")
                    }
                    r == null || r.versionCode <= Updater.currentVersionCode() -> {
                        updateStatus?.text = "En güncel sürümü kullanıyorsunuz ✓"
                        if (!silent) toast("Keybd güncel")
                    }
                    else -> {
                        updateStatus?.text = "Yeni sürüm var: ${r.name}"
                        if (install) startUpdate(r) else offerUpdate(r)
                    }
                }
            }
        }.start()
    }

    private fun offerUpdate(r: Updater.Release) {
        val notes = r.notes.lines()
            .filterNot { it.startsWith("Co-Authored-By:") || it.startsWith("Claude-Session:") }
            .joinToString("\n").trim()
            .ifBlank { "Hata düzeltmeleri ve iyileştirmeler." }.take(1500)
        AlertDialog.Builder(this)
            .setTitle("Yeni sürüm: ${r.name}")
            .setMessage("Kullandığınız: ${BuildConfig.VERSION_NAME}\n\n$notes\n\nAyarlarınız ve öğrenilen kelimeler korunur.")
            .setPositiveButton("Güncelle") { _, _ -> startUpdate(r) }
            .setNegativeButton("Sonra", null)
            .show()
    }

    private fun startUpdate(r: Updater.Release) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(this)
                .setTitle("İzin gerekli")
                .setMessage("Keybd'nin kendini güncelleyebilmesi için açılan ekranda \"Bu kaynaktan izin ver\" seçeneğini açıp geri dönün; güncelleme kendiliğinden devam eder. (Bu izin yalnızca bir kez istenir.)")
                .setPositiveButton("Ayarları aç") { _, _ ->
                    prefs.sp.edit().putBoolean(PENDING_UPDATE, true).apply()
                    startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:$packageName")))
                }
                .setNegativeButton("Vazgeç", null)
                .show()
            return
        }
        val progress = TextView(this).apply {
            text = "İndiriliyor… %0"
            textSize = 16f
            setTextColor(textColor)
            setPadding(dpi(24f), dpi(20f), dpi(24f), dpi(8f))
        }
        val dlg = AlertDialog.Builder(this).setTitle("Keybd güncelleniyor").setView(progress).setCancelable(false).show()
        Thread {
            try {
                val apk = Updater.download(this, r) { p -> runOnUiThread { progress.text = "İndiriliyor… %$p" } }
                runOnUiThread { progress.text = "Kuruluyor… Onay ekranı açılacak." }
                Updater.install(this, apk)
                runOnUiThread { list.postDelayed({ if (!isFinishing) dlg.dismiss() }, 1500) }
            } catch (e: Exception) {
                runOnUiThread {
                    dlg.dismiss()
                    toast("Güncelleme başarısız: ${e.message}")
                }
            }
        }.start()
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
        container.addView(TextView(this).apply {
            text = "Açılımda {tarih}, {saat}, {gün}, {yarın} kullanabilirsiniz."
            textSize = 13f
            setTextColor(subColor)
            setPadding(0, 0, 0, dpi(6f))
        })
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

    private fun refreshStats() {
        val v = statsView ?: return
        val sp = prefs.sp
        fun n(k: String) = sp.getLong(k, 0L)
        val since = n(Stats.SINCE)
        val fmt = java.text.NumberFormat.getIntegerInstance(java.util.Locale("tr", "TR"))
        val days = if (since > 0) ((System.currentTimeMillis() - since) / 86_400_000L + 1) else 0
        val wpm = Stats.wpm(sp)
        v.text = if (n(Stats.WORDS) == 0L && n(Stats.CHARS) == 0L) "Henüz istatistik yok. Klavyeyle yazdıkça burada görünecek. 📊"
        else buildString {
            append("✍️ Toplam kelime: ${fmt.format(n(Stats.WORDS))}\n")
            append("📅 Bugün: ${fmt.format(n(Stats.todayKey()))} kelime\n")
            append("🔤 Harf/karakter: ${fmt.format(n(Stats.CHARS))}\n")
            append("⚡ Hız: ${if (wpm > 0) "$wpm kelime/dakika" else "hesaplanıyor…"}\n")
            append("🩹 Otomatik düzeltme seni ${fmt.format(n(Stats.AUTOCORRECT))} kez kurtardı\n")
            append("👆 Seçilen öneri: ${fmt.format(n(Stats.PICKS))}\n")
            append("〰️ Kaydırarak yazılan: ${fmt.format(n(Stats.GLIDE))}\n")
            append("😀 Gönderilen emoji: ${fmt.format(n(Stats.EMOJI))}\n")
            append("↶ Geri getirilen silme: ${fmt.format(n(Stats.RESTORE))}\n")
            append("🧮 Hesaplama: ${fmt.format(n(Stats.CALC))}   🎡 Çark: ${fmt.format(n(Stats.WHEEL))}")
            if (days > 0) append("\n⏳ $days gündür Keybd kullanıyorsun")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        when (requestCode) {
            REQ_IMAGE -> themeEditor?.onImagePicked(uri)
            REQ_BACKUP -> try {
                contentResolver.openOutputStream(uri)?.use { Backup.export(this, it) }
                toast("Yedek kaydedildi 💾")
            } catch (e: Exception) {
                toast("Yedek kaydedilemedi")
            }
            REQ_RESTORE -> AlertDialog.Builder(this).setTitle("Yedek geri yüklensin mi?")
                .setMessage("Mevcut ayarlar ve öğrenilen kelimeler yedektekilerle değiştirilecek.")
                .setPositiveButton("Geri yükle") { _, _ ->
                    val ok = try {
                        contentResolver.openInputStream(uri)?.use { Backup.import(this, it) } ?: false
                    } catch (e: Exception) {
                        false
                    }
                    if (ok) {
                        toast("Yedek geri yüklendi ✓")
                        recreate()
                    } else toast("Bu dosya geçerli bir Keybd yedeği değil")
                }.setNegativeButton("Vazgeç", null).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 7) toast(if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) "Mikrofon izni verildi 🎤" else "İzin verilmedi")
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    companion object {
        const val EXTRA_MIC = "mic"
        const val AUTO_UPDATE = "auto_update"
        const val LAST_CHECK = "last_update_check"
        const val PENDING_UPDATE = "pending_update"
        const val REQ_IMAGE = 23
        const val REQ_BACKUP = 21
        const val REQ_RESTORE = 22
    }
}
