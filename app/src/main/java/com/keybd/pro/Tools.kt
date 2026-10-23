package com.keybd.pro

/** Araç çubuğundaki (üst şerit veya yan menü) düğmeler. */
object Tools {
    class Tool(val id: String, val icon: String, val desc: String, val action: (KeybdService) -> Unit)

    val all: List<Tool> = listOf(
        Tool("emoji", "☺", "Emoji") { it.showEmojiPanel() },
        Tool("emojisearch", "🔍", "Emoji ara") { it.startEmojiSearch() },
        Tool("archive", "🗂", "Arşiv") { it.showArchivePanel() },
        Tool("clip", "📋", "Pano") { it.showClipboardPanel() },
        Tool("calc", "🧮", "Hesap makinesi") { it.showCalcPanel() },
        Tool("wheel", "🎡", "Karar çarkı") { it.showWheelPanel() },
        Tool("edit", "✥", "Metin düzenleme") { it.showEditPanel() },
        Tool("font", "𝔸", "Yazı stilleri") { it.showFontPanel() },
        Tool("resize", "⇕", "Klavye boyutu") { it.startResize() },
        Tool("onehand", "◧", "Tek el modu") { it.toggleOneHand() },
        Tool("incognito", "🕶", "Gizli mod") { it.toggleIncognito() },
        Tool("theme", "◐", "Tema değiştir") { it.cycleTheme() },
        Tool("numpad", "123", "Sayı tuşları") { it.showNumpad() },
        Tool("collapse", "▾", "Menüyü gizle") { it.setStripCollapsed(true) },
        Tool("settings", "⚙", "Ayarlar") { it.openSettings() },
    )
}
