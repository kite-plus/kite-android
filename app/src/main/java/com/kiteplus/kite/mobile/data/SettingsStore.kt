package com.kiteplus.kite.mobile.data

import android.content.Context
import com.kiteplus.kite.mobile.data.remote.CookieStore
import com.kiteplus.kite.mobile.data.remote.KiteGateway
import com.kiteplus.kite.mobile.data.remote.PersistentCookieJar
import com.kiteplus.kite.mobile.domain.SyncSettings
import androidx.core.content.edit

/**
 * The few things this app has to remember between launches: where the server
 * is, the sync watermark, and the session cookie.
 *
 * It deliberately does not store the password. The cookie is the session; when
 * it expires the app asks for the password again rather than keeping one on the
 * phone to replay.
 */
class SettingsStore(context: Context) : CookieStore, SyncSettings {

    private val prefs = context.getSharedPreferences("kite", Context.MODE_PRIVATE)

    /** Where Kite listens on the LAN, e.g. `http://192.168.1.20:1717`. */
    var serverUrl: String
        get() = prefs.getString(KEY_URL, "").orEmpty()
        set(value) {
            val clean = normalize(value)
            if (clean != serverUrl) {
                prefs.edit { putString(KEY_URL, clean) }
                synchronized(this) { cached = null }
            }
        }

    /**
     * Accepts what people actually type. `10.0.0.5:1717` is a complete address
     * to a person, but the HTTP client wants a scheme, and without one it fails
     * deep inside with a message that says nothing about the address they
     * typed.
     */
    private fun normalize(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isEmpty()) return ""
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        return "http://$trimmed"
    }

    /** The account last used to sign in, only so the field can be prefilled. */
    var userName: String
        get() = prefs.getString(KEY_USER, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_USER, value.trim()) }

    /**
     * The newest `updated_at` the server has confirmed, sent back as
     * `updated_from` so a sync pulls only what changed.
     *
     * Kept as the server's own RFC 3339 string rather than a parsed instant:
     * formatting it again would drop sub-second digits and re-pull a window.
     */
    override var syncWatermark: String?
        get() = prefs.getString(KEY_WATERMARK, null)
        set(value) = prefs.edit { putString(KEY_WATERMARK, value) }

    /**
     * The content type a new article takes, as `/content-types` reported it.
     * Cached because it is a property of the site, not of a session.
     */
    override var defaultKind: String?
        get() = prefs.getString(KEY_KIND, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit { putString(KEY_KIND, value) }

    /** The types themselves, so the editor can show their fields while offline. */
    override var contentTypesJson: String?
        get() = prefs.getString(KEY_TYPES, null)
        set(value) = prefs.edit { putString(KEY_TYPES, value) }

    /**
     * The content types the site declared, learned while online and kept for offline use.
     */
    val kinds: List<KindInfo> get() = parseKinds(contentTypesJson)

    /**
     * Whether there is an address to talk to at all.
     */
    val isConfigured: Boolean get() = serverUrl.isNotBlank()

    // --- session cookie ----------------------------------------------------

    /**
     * The stored cookies, as text, so a session survives the process.
     */
    override fun read(): List<String> =
        prefs.getString(KEY_COOKIES, null)
            ?.split('\n')
            ?.filter { it.isNotBlank() }
            .orEmpty()

    /**
     * Replaces the stored cookies; a login writes here.
     */
    override fun write(values: List<String>) {
        prefs.edit { putString(KEY_COOKIES, values.joinToString("\n")) }
    }

    /**
     * Drops the session cookies without touching the address or the name.
     */
    fun forgetSession() = write(emptyList())

    // --- api ---------------------------------------------------------------

    private val cookieJar = PersistentCookieJar(this)

    @Volatile
    private var cached: KiteGateway? = null

    /**
     * The client for the configured server, or null when none is configured.
     * Rebuilt whenever the address changes, since a Retrofit instance is bound
     * to one base URL.
     */
    override fun api(): KiteGateway? = client(httpLog = false)

    /**
     * Builds a gateway for the configured address. The address is validated here and
     * not in the HTTP layer, so a typo comes back as "地址格式不对" instead of a stack
     * trace from deep inside Retrofit.
     */
    fun client(httpLog: Boolean): KiteGateway? {
        val url = serverUrl
        if (url.isBlank()) return null
        cached?.let { return it }
        return synchronized(this) {
            cached ?: runCatching { KiteGateway.create(url, cookieJar, httpLog) }
                .getOrElse { throw IllegalArgumentException("地址格式不对：" + url) }
                .also { cached = it }
        }
    }

    /**
     * Signs out: forget the cookies and drop the cached client with them.
     */
    fun clearSession() {
        cookieJar.clear()
        prefs.edit { remove(KEY_COOKIES) }
    }

    private companion object {
        const val KEY_URL = "server_url"
        const val KEY_USER = "user_name"
        const val KEY_WATERMARK = "sync_watermark"
        const val KEY_KIND = "default_kind"
        const val KEY_TYPES = "content_types"
        const val KEY_COOKIES = "cookies"
    }
}
