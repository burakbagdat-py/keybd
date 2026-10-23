package com.keybd.pro

object EmojiData {
    class Category(val icon: String, val name: String, val items: List<String>)

    private fun split(s: String) = s.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

    @Volatile private var cache: List<Category>? = null
    @Volatile private var toneCache: Map<String, List<String>>? = null

    /**
     * Tüm Unicode emojileri (assets/emoji_all.txt, Emoji 18.0). Cihazın yazı tipinde
     * olmayanlar (eski Android sürümleri) otomatik gizlenir; ardından kaomoji ve simgeler.
     */
    fun categories(ctx: android.content.Context): List<Category> {
        cache?.let { return it }
        val paint = android.graphics.Paint()
        val out = ArrayList<Category>()
        try {
            var title: String? = null
            ctx.assets.open("emoji_all.txt").bufferedReader().useLines { lines ->
                for (line in lines) {
                    if (line.startsWith("#")) { title = line.substring(1); continue }
                    val t = title ?: continue
                    val items = split(line).filter { paint.hasGlyph(it) }
                    val sp = t.indexOf(' ')
                    out += Category(t.substring(0, sp), t.substring(sp + 1), items)
                }
            }
        } catch (_: Exception) {
        }
        out += extras
        cache = out
        return out
    }

    /** Ten rengi varyantları (uzun basınca). */
    fun tones(ctx: android.content.Context): Map<String, List<String>> {
        toneCache?.let { return it }
        val paint = android.graphics.Paint()
        val m = HashMap<String, List<String>>()
        try {
            ctx.assets.open("emoji_tones.txt").bufferedReader().useLines { lines ->
                for (line in lines) {
                    val p = line.split('\t')
                    if (p.size == 2) split(p[1]).filter { paint.hasGlyph(it) }.takeIf { it.isNotEmpty() }?.let { m[p[0]] = it }
                }
            }
        } catch (_: Exception) {
        }
        toneCache = m
        return m
    }

    private val extras: List<Category> by lazy {
        listOf(
            Category("(^‿^)", "Kaomoji", listOf(
                "(^‿^)", "(◕‿◕)", "(｡◕‿◕｡)", "(✿◠‿◠)", "ヽ(•‿•)ノ", "(≧▽≦)", "(*^▽^*)", "(＾▽＾)", "٩(◕‿◕)۶", "(•̀ᴗ•́)و",
                "( ˘ ³˘)♥", "(♥ω♥)", "(づ｡◕‿‿◕｡)づ", "(っ˘ω˘ς)", "ʕ•ᴥ•ʔ", "(=^･ω･^=)", "U・ᴥ・U", "(•ω•)", "(・_・;)", "(；一_一)",
                "(¬_¬)", "(ಠ_ಠ)", "ಠ‿ಠ", "(╯°□°)╯︵ ┻━┻", "┬─┬ノ( º _ ºノ)", "¯\\_(ツ)_/¯", "(⊙_⊙)", "(°ロ°)", "(T_T)", "(╥﹏╥)",
                "(ಥ﹏ಥ)", "(>_<)", "(-_-) zzz", "(￣ω￣)", "(￣▽￣)ノ", "(^_^)/", "( ͡° ͜ʖ ͡°)", "(•_•)", "( •_•)>⌐■-■", "(⌐■_■)",
                "ᕦ(ò_óˇ)ᕤ", "(ง'̀-'́)ง", "(ﾉ◕ヮ◕)ﾉ*:･ﾟ✧", "✧*｡٩(ˊᗜˋ*)و✧*｡", "(*≧ω≦*)", "(〃＾▽＾〃)", "(´｡• ω •｡`)", "(｡•́︿•̀｡)", "(｡T ω T｡)", "(っ◔◡◔)っ ♥",
            )),
            Category("✦", "Simgeler", split("""
★ ☆ ✦ ✧ ✩ ✪ ✫ ✬ ✭ ✮ ✯ ✰ ⁂ ❂ ☀ ☼ ☾ ☽ ♡ ♥ ❥ ❦ ❧ ღ ✿ ❀ ❁ ✾ ❃ ❋ ✤ ✣ ♪ ♫ ♬ ♩ ☎ ☏ ✉ ✂ ✎ ✏ ✐ ✓ ✔ ✕ ✖ ✗ ✘
☐ ☑ ☒ ← ↑ → ↓ ↔ ↕ ⇐ ⇑ ⇒ ⇓ ⇔ ➔ ➜ ➤ ➥ ➦ ➧ ➨ ➲ ▲ △ ▼ ▽ ◀ ◁ ▶ ▷ ● ○ ◎ ◉ ◆ ◇ ■ □ ▪ ▫ ◐ ◑ ⬤ ∞ ≈ ≠ ≤ ≥ ± ×
÷ √ ∑ ∏ ∆ ∇ ∫ ∂ π µ Ω α β γ δ λ σ θ φ ψ ° ′ ″ ‰ ¼ ½ ¾ ⅓ ⅔ € £ ¥ ₺ $ ¢ ₹ ₽ ₿ © ® ™ § ¶ † ‡ • … ※ ⌘ ⌥ ⏎ ⌫
「 」 『 』 【 】 《 》 〈 〉 « » ‹ › “ ” ‘ ’ „""")),
        )
    }

    /** Yazılan kelimeye göre emoji önerisi. */
    private val keywords: Map<String, String> = mapOf(
        "kalp" to "❤️", "aşk" to "❤️", "ask" to "❤️", "sevgi" to "🥰", "seviyorum" to "😍", "love" to "❤️", "heart" to "❤️",
        "haha" to "😂", "hahaha" to "😂", "jsjs" to "😂", "lol" to "😂", "gül" to "🌹", "güldüm" to "😂", "komik" to "😂",
        "mutlu" to "😊", "happy" to "😊", "üzgün" to "😢", "sad" to "😢", "ağla" to "😭", "ağlıyorum" to "😭", "cry" to "😭",
        "kızgın" to "😡", "sinir" to "😤", "angry" to "😡", "tamam" to "👍", "ok" to "👌", "okay" to "👌", "evet" to "👍",
        "hayır" to "👎", "no" to "🙅", "yes" to "✅", "teşekkürler" to "🙏", "teşekkür" to "🙏", "sağol" to "🙏", "thanks" to "🙏",
        "lütfen" to "🙏", "please" to "🙏", "alkış" to "👏", "bravo" to "👏", "tebrikler" to "🎉", "kutlama" to "🎉", "parti" to "🥳",
        "party" to "🥳", "doğumgünü" to "🎂", "pasta" to "🎂", "birthday" to "🎂", "hediye" to "🎁", "gift" to "🎁",
        "ateş" to "🔥", "fire" to "🔥", "harika" to "🤩", "süper" to "👌", "cool" to "😎", "güneş" to "☀️", "sun" to "☀️",
        "yağmur" to "🌧️", "kar" to "❄️", "gece" to "🌙", "uyku" to "😴", "uykum" to "😴", "sleep" to "😴", "kahve" to "☕",
        "coffee" to "☕", "çay" to "🍵", "tea" to "🍵", "pizza" to "🍕", "yemek" to "🍽️", "food" to "🍔", "bira" to "🍺",
        "beer" to "🍺", "araba" to "🚗", "car" to "🚗", "uçak" to "✈️", "ev" to "🏠", "home" to "🏠", "para" to "💰",
        "money" to "💰", "kedi" to "🐱", "cat" to "🐱", "köpek" to "🐶", "dog" to "🐶", "çiçek" to "🌸", "flower" to "🌸",
        "futbol" to "⚽", "maç" to "⚽", "gol" to "⚽", "müzik" to "🎵", "music" to "🎵", "telefon" to "📱", "phone" to "📱",
        "düşünüyorum" to "🤔", "hmm" to "🤔", "think" to "🤔", "selam" to "👋", "merhaba" to "👋", "hi" to "👋", "hello" to "👋",
        "öp" to "😘", "öptüm" to "😘", "kiss" to "😘", "korku" to "😱", "şok" to "😱", "wow" to "😮", "vay" to "😮",
        "türkiye" to "🇹🇷", "bayrak" to "🇹🇷", "kral" to "👑", "king" to "👑", "güç" to "💪", "strong" to "💪", "zaman" to "⏰",
        "okul" to "🏫", "kitap" to "📚", "book" to "📚", "hasta" to "🤒", "sick" to "🤒", "yıldız" to "⭐", "star" to "⭐",
    )

    fun forWord(word: String): String? = keywords[word.lowercase(java.util.Locale("tr", "TR"))] ?: keywords[word.lowercase()]
}
