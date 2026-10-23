package com.keybd.pro

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * GitHub sürümlerinden (Releases) güncelleme denetler, APK'yı indirir ve Android'in
 * kendi yükleyicisiyle kurar. Aynı imzalı sürüm eskisinin üzerine kurulduğu için
 * ayarlar, öğrenilen kelimeler ve klavye seçimi korunur.
 */
object Updater {
    class Release(val versionCode: Int, val name: String, val notes: String, val apkUrl: String, val size: Long)

    private const val API = "https://api.github.com/repos/" + BuildConfig.UPDATE_REPO + "/releases/latest"

    /** Etiket "v1.N" → sürüm kodu N+1 (bkz. app/build.gradle.kts). */
    fun versionCodeOf(tag: String): Int? =
        Regex("""^v?1\.(\d+)$""").find(tag.trim())?.groupValues?.get(1)?.toIntOrNull()?.plus(1)

    /** Ağ çağrısı yapar; ana iş parçacığında çağırmayın. */
    fun fetchLatest(): Release? {
        val c = URL(API).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 15000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", "Keybd-Updater")
        try {
            if (c.responseCode == 404) return null
            if (c.responseCode != 200) throw IllegalStateException("GitHub yanıtı: ${c.responseCode}")
            val o = JSONObject(c.inputStream.bufferedReader().readText())
            val tag = o.optString("tag_name")
            val code = versionCodeOf(tag) ?: return null
            val assets = o.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) {
                    return Release(code, o.optString("name", tag), o.optString("body").trim(),
                        a.getString("browser_download_url"), a.optLong("size"))
                }
            }
            return null
        } finally {
            c.disconnect()
        }
    }

    fun currentVersionCode(): Int = BuildConfig.VERSION_CODE

    /** APK'yı indirir; ilerleme 0..100 olarak bildirilir. */
    fun download(ctx: Context, r: Release, progress: (Int) -> Unit): File {
        val out = File(ctx.cacheDir, "update.apk")
        var url = URL(r.apkUrl)
        var c: HttpURLConnection
        var redirects = 0
        while (true) {
            c = url.openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 15000
            c.readTimeout = 30000
            c.setRequestProperty("User-Agent", "Keybd-Updater")
            val code = c.responseCode
            if (code in 300..399 && redirects++ < 5) {
                url = URL(url, c.getHeaderField("Location"))
                c.disconnect()
                continue
            }
            if (code != 200) throw IllegalStateException("İndirme hatası: $code")
            break
        }
        val total = c.contentLengthLong.takeIf { it > 0 } ?: r.size
        c.inputStream.use { inp ->
            out.outputStream().use { o ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                var last = -1
                while (true) {
                    val n = inp.read(buf)
                    if (n < 0) break
                    o.write(buf, 0, n)
                    done += n
                    if (total > 0) {
                        val p = (done * 100 / total).toInt()
                        if (p != last) { last = p; progress(p) }
                    }
                }
            }
        }
        c.disconnect()
        return out
    }

    /** Kurulumu başlatır; Android kullanıcıdan onay ister. */
    fun install(ctx: Context, apk: File) {
        val pi = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(ctx.packageName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = pi.createSession(params)
        pi.openSession(id).use { s ->
            apk.inputStream().use { inp ->
                s.openWrite("base.apk", 0, apk.length()).use { o ->
                    inp.copyTo(o)
                    s.fsync(o)
                }
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(ctx, id, Intent(ctx, UpdateReceiver::class.java), flags)
            s.commit(pending.intentSender)
        }
    }
}

/** Yükleyicinin sonucunu alır; gerekirse sistemin onay ekranını açar. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    ctx.startActivity(confirm)
                } catch (_: Exception) {
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {}
            PackageInstaller.STATUS_FAILURE_ABORTED -> Toast.makeText(ctx, "Güncelleme iptal edildi", Toast.LENGTH_SHORT).show()
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""
                Toast.makeText(ctx, "Güncelleme kurulamadı $msg", Toast.LENGTH_LONG).show()
            }
        }
    }
}
