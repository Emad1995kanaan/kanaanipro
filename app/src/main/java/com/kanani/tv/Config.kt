package com.kanani.tv

import android.content.Context
import android.util.Base64
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * الهوست مش مكتوب كنص صريح بالتطبيق: مخزّن مشفّر (Base64) ويتفك وقت التشغيل،
 * وكمان بيقدر ينجاب من ملف إعدادات على دومينك (اختياري) فتغيّر السيرفر بدون تحديث التطبيق.
 *
 * لتفعيل الإعدادات عن بعد: حط رابط ملف config.json في CONFIG_URL، مثال محتواه:
 * { "host": "http://amlive.cc:80", "app_name": "KANANI" }
 * وإذا تركته فاضي التطبيق بيستخدم الهوست الأساسي مباشرة.
 */
object Config {
    private const val CONFIG_URL = ""
    private const val HOST_B64 = "aHR0cDovL2FtbGl2ZS5jYzo4MA=="

    private val defaultHost: String
        get() = String(Base64.decode(HOST_B64, Base64.DEFAULT)).trim()

    data class Remote(val host: String = "", val app_name: String = "KANANI")

    @Volatile
    var current: Remote = Remote(host = defaultHost)
        private set

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).build()

    suspend fun load(context: Context) = withContext(Dispatchers.IO) {
        if (CONFIG_URL.isBlank()) return@withContext
        val prefs = context.getSharedPreferences("cfg", Context.MODE_PRIVATE)
        fun apply(body: String): Boolean {
            val r = runCatching { Gson().fromJson(body, Remote::class.java) }.getOrNull()
            if (r != null && r.host.isNotBlank()) { current = r; return true }
            return false
        }
        try {
            client.newCall(Request.Builder().url(CONFIG_URL).build()).execute().use { res ->
                if (res.isSuccessful) {
                    val body = res.body?.string().orEmpty()
                    if (apply(body)) { prefs.edit().putString("last", body).apply(); return@withContext }
                }
            }
        } catch (_: Exception) { /* نرجع للمخزّن أو للهوست الأساسي */ }
        prefs.getString("last", null)?.let { apply(it) }
    }
}
