package com.kanani.tv

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

data class UserInfo(
    val username: String? = null,
    val status: String? = null,
    @SerializedName("exp_date") val expDate: String? = null,
    val auth: Int = 0
)
data class LoginResponse(@SerializedName("user_info") val userInfo: UserInfo? = null)

data class Category(
    @SerializedName("category_id") val id: String,
    @SerializedName("category_name") val name: String
)

data class Item(
    @SerializedName("stream_id") val streamId: Int? = null,
    @SerializedName("series_id") val seriesId: Int? = null,
    @SerializedName("category_id") val categoryId: String? = null,
    val name: String = "",
    @SerializedName("stream_icon") val icon: String? = null,
    val cover: String? = null,
    val rating: String? = null,
    val added: String? = null,
    @SerializedName("container_extension") val ext: String? = null
) {
    /** نوع العنصر (فيلم/مسلسل/قناة)؛ بيتعبّى وقت التحميل عشان الأقسام المختلطة (أطفال، الأكثر مشاهدة) */
    @Transient var kind: Tab? = null
    val id get() = streamId ?: seriesId ?: 0
    val poster get() = icon ?: cover
}

data class Episode(
    val id: String = "",
    val title: String? = null,
    @SerializedName("episode_num") val num: String? = null,
    @SerializedName("container_extension") val ext: String? = null
)
data class SeriesInfo(val episodes: Map<String, List<Episode>>? = null)

interface XtreamApi {
    @GET("player_api.php")
    suspend fun login(@Query("username") u: String, @Query("password") p: String): LoginResponse

    @GET("player_api.php")
    suspend fun categories(
        @Query("username") u: String, @Query("password") p: String, @Query("action") action: String
    ): List<Category>

    @GET("player_api.php")
    suspend fun streams(
        @Query("username") u: String, @Query("password") p: String, @Query("action") action: String
    ): List<Item>

    @GET("player_api.php")
    suspend fun seriesInfo(
        @Query("username") u: String, @Query("password") p: String,
        @Query("action") action: String, @Query("series_id") id: Int
    ): SeriesInfo

    @GET("player_api.php")
    suspend fun epg(
        @Query("username") u: String, @Query("password") p: String,
        @Query("action") action: String, @Query("stream_id") id: Int, @Query("limit") limit: Int
    ): EpgResponse
}

object Api {
    private var cached: Pair<String, XtreamApi>? = null

    // الـ baseUrl بيجي من Config (الهوست مخفي عن المستخدم)
    @Synchronized
    fun get(): XtreamApi {
        val base = Config.current.host.trimEnd('/') + "/"
        cached?.let { if (it.first == base) return it.second }
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS) // قوائم القنوات والأفلام ممكن تكون كبيرة
            .build()
        val api = Retrofit.Builder().baseUrl(base).client(client)
            .addConverterFactory(GsonConverterFactory.create()).build()
            .create(XtreamApi::class.java)
        cached = base to api
        return api
    }

    private val host get() = Config.current.host.trimEnd('/')

    // الأفضل تمرير الروابط عبر وسيطك بتوكن مؤقت بدل user/pass بالرابط.
    fun liveUrl(u: String, p: String, id: Int) = "$host/live/$u/$p/$id.m3u8"
    fun movieUrl(u: String, p: String, id: Int, ext: String?) = "$host/movie/$u/$p/$id.${ext ?: "mp4"}"
    fun seriesUrl(u: String, p: String, id: String, ext: String?) = "$host/series/$u/$p/$id.${ext ?: "mp4"}"
}

object Session {
    var username = ""
    var password = ""
    var expDate: String? by androidx.compose.runtime.mutableStateOf<String?>(null)
}

enum class LoginResult { OK, BAD_CREDENTIALS, NETWORK }

object Auth {
    suspend fun login(u: String, p: String): LoginResult = try {
        val info = Api.get().login(u, p).userInfo
        if (info != null && info.auth == 1) {
            Session.username = u; Session.password = p; Session.expDate = info.expDate
            LoginResult.OK
        } else LoginResult.BAD_CREDENTIALS
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        LoginResult.NETWORK
    }

    private fun prefs(c: Context) = c.getSharedPreferences("auth", Context.MODE_PRIVATE)
    fun save(c: Context, u: String, p: String) = prefs(c).edit().putString("u", u).putString("p", p).apply()
    fun clear(c: Context) = prefs(c).edit().clear().apply()
    fun saved(c: Context): Pair<String, String>? {
        val s = prefs(c)
        val u = s.getString("u", null); val p = s.getString("p", null)
        return if (u != null && p != null) u to p else null
    }
}
