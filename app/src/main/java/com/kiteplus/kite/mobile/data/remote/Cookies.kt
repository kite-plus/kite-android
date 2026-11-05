package com.kiteplus.kite.mobile.data.remote

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/** One stored cookie, as text, so it survives a process restart. */
interface CookieStore {
    /**
     * The stored cookies as text; a cookie that no longer parses is dropped by the jar.
     */
    fun read(): List<String>
    /**
     * Replaces the stored cookies entirely.
     */
    fun write(values: List<String>)
}

/**
 * OkHttp keeps no cookies by itself, and this API's session *is* a cookie.
 * Without this the login would appear to succeed and every later call would be
 * anonymous.
 */
class PersistentCookieJar(private val store: CookieStore) : CookieJar {

    /**
     * Stores what the server set, replacing cookies of the same name.
     */
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val merged = (loadForRequest(url) + cookies)
            .associateBy { it.name }
            .values
            .map { it.toString() }
        store.write(merged)
    }

    /**
     * The cookies that apply to this request and have not expired.
     */
    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        store.read().mapNotNull { Cookie.parse(url, it) }
            .filter { it.matches(url) && it.expiresAt > System.currentTimeMillis() }

    /**
     * Forgets every stored cookie; used when signing out.
     */
    fun clear() = store.write(emptyList())
}
