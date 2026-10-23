# ⌨️ Keybd — Gelişmiş Android Klavye

Telefonunuzdaki varsayılan klavyenin yerine kullanabileceğiniz, Türkçe'ye özel olarak
geliştirilmiş, çok özellikli ve **gizlilik dostu** bir klavye. Yazdığınız hiçbir şey
cihazınızdan çıkmaz: klavye servisi internete hiç bağlanmaz, internet yalnızca ayarlar
uygulamasındaki güncelleme denetimi için kullanılır.

## 📲 Kurulum (telefon)

1. En son sürümün APK'sını indirin:
   **[Keybd.apk (son sürüm)](https://github.com/burakbagdat-py/keybd/releases/latest/download/Keybd.apk)**
2. Dosyayı açın. "Bilinmeyen kaynaklardan yükleme" izni sorulursa tarayıcınıza/dosya
   yöneticinize izin verin ve **Yükle**'ye dokunun.
3. **Keybd** uygulamasını açın; kurulum sihirbazı sizi yönlendirir:
   - **1. Etkinleştir** → listeden *Keybd*'yi açın (Android'in her klavye için gösterdiği
     standart uyarıyı onaylayın).
   - Geri döndüğünüzde klavye seçici **otomatik açılır** → *Keybd*'yi seçin. Bitti 🎉
4. Sesli yazma kullanacaksanız ayarlardan **Mikrofon izni** verin.

## 🔄 Güncellemeler

Keybd kendini güncelleyebilir. Ayarlar uygulaması açıldığında (en fazla 6 saatte bir)
yeni sürüm olup olmadığına bakar; **Ayarlar → Güncelleme → Güncellemeleri denetle** ile
elle de denetleyebilirsiniz. *Güncelle*'ye basınca yeni APK indirilir ve Android'in
yükleyicisi açılır. İlk seferde Android, Keybd'nin uygulama yüklemesine izin vermenizi
ister.

Güncelleme eskisinin üzerine kurulur: **ayarlarınız, öğrenilen kelimeler, pano geçmişi
ve klavye seçimi korunur** (tüm sürümler aynı anahtarla imzalanır).

Yeni sürümler otomatik yayınlanır: depoya yapılan her gönderimde GitHub Actions uygulamayı
test edip derler, sürüm numarasını artırır (1.N) ve
[Releases](https://github.com/burakbagdat-py/keybd/releases) sayfasında yayınlar.

## ✨ Özellikler

### Yazma
- **Türkçe Q**, **Türkçe F** ve **İngilizce** düzenler, 🌐 tuşuyla anında geçiş
- **Akıllı kelime önerileri** (45.000 Türkçe + 35.000 İngilizce kelimelik sözlük)
- **Otomatik düzeltme** — klavye yakınlığına duyarlı yazım hatası düzeltmesi
- **Türkçe karakter düzeltme** — `gorusuruz` → `görüşürüz`, `cok` → `çok`, `tesekkurler` → `teşekkürler`
- Otomatik düzeltmeden hemen sonra **⌫ ile geri alma**
- **Kelime öğrenme** — yeni kelimeleri ve alışkanlıklarınızı öğrenir (yalnızca cihazda)
- **Sonraki kelime tahmini** — en çok yazdığınız kelime dizilerini öğrenir
- **Kaydırarak yazma (glide typing)** — parmağınızı kaldırmadan harflerin üzerinden geçin
- **Emoji önerileri** — `kalp` yazınca ❤️, `haha` yazınca 😂
- **Kısayollar / metin genişletme** — `slm` → `selam`, kendi kısayollarınızı ekleyin
- Otomatik büyük harf, **çift boşluk = nokta**, akıllı noktalama
- Silerek bir kelimeye dönünce o kelime için öneriler yeniden açılır
- **Silineni geri getirme** — yanlışlıkla sildiğin metin şeritte **↶** düğmesiyle tek dokunuşta geri gelir
- **Kelimeye dokununca öneri** — metinde geriye dönüp bir kelimeye dokununca o kelimenin
  düzeltmeleri şeritte çıkar; seçince kelime değişir
- **Satır içi hesap makinesi** — `125*4=` yazınca şeritte `= 500` çıkar, dokununca eklenir
  (`+ − × ÷ % ^` ve parantez; `1,5+2` gibi virgüllü sayılar)
- **Canlı matematik** — `=` yazmana gerek yok: `1x1`, `12/4`, `7-2` yazarken sonuç şeritte belirir
  (tarih ve telefon numaraları gibi girdiler hesaplanmaz)

### Hareketler
- **Boşluk tuşunda kaydırma** → imleci sağa/sola taşı
- **⌫ tuşundan sola kaydırma** → kelimeleri seçip tek seferde sil
- Basılı tutulan ⌫ hızlanır, sonra kelime kelime siler
- **Uzun basma menüleri** — rakamlar, aksanlı harfler (ş ğ é ß ñ…), semboller, `.com` / `.com.tr`
- Boşluk tuşunu aşağı kaydır → klavyeyi gizle
- Shift'e çift dokunma → **BÜYÜK HARF kilidi**

### Araç çubuğu (⋯)
- 😀 **Emoji paneli** — 11 kategori, son kullanılanlar, **kaomoji** ( ͡° ͜ʖ ͡°) ve özel simgeler
- 🔍 **Emoji arama** — klavyeden yazarak ara: `kalp` → ❤️, `pizza` → 🍕, `türkiye` → 🇹🇷;
  sonuçlar klavyenin üstünde büyük ızgarada, **Enter / Tümü** ile tam ekran emoji panelinde
- **Tüm güncel emojiler (Unicode Emoji 18.0)** — iPhone'daki emoji setinin aynısı; emojiye uzun
  basınca **ten rengi** seçimi. Telefonunun yazı tipinin çizemediği çok yeni emojiler gizlenir
- 🗂 **Arşiv** — sık kullandığın metinleri (adres, IBAN, hazır cevaplar…) sakla: seçili metni,
  panodakini ya da ✎ ile yazarak ekle; dokununca yapıştır. Pano panelinde her öğede 🗂 düğmesi
- 🧮 **Hesap makinesi paneli** — sonucu tek dokunuşla metne ekle
- 🎡 **Karar çarkı** — seçtiğin veya kopyaladığın metindeki seçeneklerden çark oluşturur
  (`pizza mı burger mi?`, `Sinema, tiyatro veya konser`, alt alta liste…), çevir ve sonucu
  sohbete gönder. Metin seçince şeritte **🎡 Çark yap** düğmesi de çıkar.
  **＋ Ekle** ile elle seçenek yaz, her seçeneğin **% kazanma oranını** −/+ ile ayarla
  (dilimler orana göre büyür), ⚖ ile eşitle
- ⇕ **Canlı boyutlandırma** — yazarken klavyenin yüksekliğini, genişliğini ve yazı boyutunu −/+ ile ayarla
- 📋 **Pano geçmişi** — son 40 kopya, **sabitleme**, son kopyalananı tek dokunuşla yapıştırma
- ✥ **Metin düzenleme paneli** — ok tuşları, seçim modu, tümünü seç, kopyala/kes/yapıştır, geri al/yinele
- 𝔸 **Süslü yazı stilleri** — 𝐊𝐚𝐥ı𝐧, 𝐼𝑡𝑎𝑙𝑖𝑘, 𝓔𝓵 𝔂𝓪𝔃ı𝓼ı, 𝕲𝖔𝖙𝖎𝖐, 𝔻𝕠𝕦𝕓𝕝𝕖, Ⓓⓐⓘⓡⓔ, 🅺🅰🆁🅴, Ｇｅｎｉş, ᴋüçüᴋ, s̶t̶r̶i̶k̶e̶ …
- ◧ **Tek el modu** (sağ/sol)
- 🕶 **Gizli mod** — hiçbir şey öğrenilmez veya kaydedilmez
- ◐ Hızlı tema değiştirme, **123** sayı tuş takımı, ⚙ ayarlar
- 🎤 **Sesli yazma** (Türkçe/İngilizce, cihazın konuşma tanıma servisiyle)

### Görünüm
- 12 tema: **Material You** (Android 12+ duvar kağıdı renkleri), Koyu, Açık, AMOLED Siyah,
  Okyanus, Gün Batımı, Orman, Pembe, Gece Moru, Retro, **Venom Simbiyot** ve **Özel temam**
- 🖤 **Venom Simbiyot** — siyah-beyaz, organik kenarlı ve "damlayan" tuşlar, arka planda
  dokunaçlar, her dokunuşta saçılan simbiyot dikenleri
- 🎨 **Kendi temanı oluştur** — renkleri seç (hazır takımlar, renk tekerleği, HEX), istersen
  arka plana kendi fotoğrafını koy; tuş saydamlığı ve karartma ayarı, canlı önizleme
- Ayarlanabilir klavye yüksekliği **ve genişliği** (tuş boyu/eni), yazı boyutu, tuş köşe yuvarlaklığı ve aralığı
- **Kompakt mod** — daha alçak tuşlar, dar aralıklar, ince üst menü
- **Üst menü** — açılır/kapanır (▾ ile ince çizgiye iner), yüksekliği ayarlanır, istersen
  **sol veya sağ yan menüye** taşınır
- Sağ alttaki tuş her alanda sade bir **Enter oku**; mikrofon düğmesi emoji değil düz simge
- İsteğe bağlı sayı satırı, tuş ipuçları, tuş önizleme balonu, çerçevesiz görünüm
- Alana göre akıllı düzen: e-posta alanında `@`, adres alanında `/` ve `.com`,
  sayı/telefon alanlarında sayı tuş takımı, şifre alanlarında öğrenme kapalı + sayı satırı

### İstatistik ve yedekleme
- 📊 **Yazma istatistikleri** — toplam/bugünkü kelime, dakikada kelime hızı, otomatik
  düzeltmenin seni kaç kez kurtardığı, kaydırarak yazılan kelimeler, emojiler… (yalnızca cihazda)
- 💾 **Yedekleme / geri yükleme** — ayarlar, kısayollar, öğrenilen kelimeler, tema fotoğrafı,
  sabitlenen pano öğeleri ve istatistikler tek bir dosyada; telefon değiştirince geri yükle

### Geri bildirim
- Ayarlanabilir titreşim süresi, tuş sesi ve ses düzeyi
- 🔊 **Ses paketleri** (seçerken dinlenebilir): Mekanik (tıkırtılı mavi switch), Mekanik (tok/derin),
  Daktilo (satır sonunda zil 🔔), Yumuşak, Baloncuk, Piyano notaları ve **Komik 🤡**
  (boing, ördek, düdük, korna… her tuşta farklı). Sesler tamamen sentezlenmiştir (`tools/gen_sounds.py`)
- Ayarlanabilir uzun basma süresi

## 🛠️ Kaynak koddan derleme

Gereksinimler: JDK 17+, Android SDK (platform 35).

```bash
./gradlew testReleaseUnitTest   # motor testleri (düzeltme, glide, öğrenme…)
./gradlew assembleRelease       # app/build/outputs/apk/release/app-release.apk
```

GitHub'a her gönderimde **Actions** sekmesindeki *APK Derle ve Yayınla* iş akışı APK'yı
derler ve yeni bir sürüm olarak yayınlar; uygulamadaki güncelleyici bu sürümleri okur.

### Proje yapısı

| Dosya | Görev |
|---|---|
| `KeybdService.kt` | Klavye servisi: tuş işleme, öneriler, otomatik düzeltme, paneller |
| `KeyboardView.kt` | Klavyenin çizimi, dokunma, uzun basma menüleri, glide izi |
| `Layouts.kt` | Türkçe Q/F, İngilizce, sembol ve sayı düzenleri |
| `Dictionary.kt` | Sözlük, düzeltme motoru, öğrenme ve tahmin |
| `GlideDecoder.kt` | Kaydırarak yazma çözücüsü |
| `SuggestionStrip.kt` / `Panels.kt` | Öneri şeridi, araç çubuğu, emoji/pano/düzenleme/stil panelleri |
| `SettingsActivity.kt` | Kurulum sihirbazı ve ayarlar |
| `Updater.kt` | GitHub sürümlerinden güncelleme denetleme, indirme ve kurma |
| `Calc.kt` | Hesap makinesi ve çark seçeneklerini ayıklama |
| `WheelView.kt` | Dönen karar çarkı |
| `EmojiSearch.kt` | Emoji arama (CLDR anahtar kelimeleri) |
| `ThemeEditor.kt` | Kendi temanı oluşturma ve renk seçici |
| `Stats.kt` / `Backup.kt` | Yazma istatistikleri, yedekleme ve geri yükleme |
| `SoundPack.kt` | Tuş sesi paketleri |
| `Archive.kt` / `Tools.kt` | Arşiv ve araç çubuğu düğmeleri |
| `tools/gen_sounds.py`, `tools/gen_emoji.py` | Ses ve emoji varlıklarını üreten betikler |

Sözlük verileri [hermitdave/FrequencyWords](https://github.com/hermitdave/FrequencyWords)
(CC BY-SA 4.0), emoji anahtar kelimeleri [Unicode CLDR](https://github.com/unicode-org/cldr-json)
(Unicode lisansı) kaynağından türetilmiştir.
