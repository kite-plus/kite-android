package com.kiteplus.kite.mobile.domain

import com.kiteplus.kite.mobile.data.local.ContentDao
import com.kiteplus.kite.mobile.data.remote.ApiFailure
import com.kiteplus.kite.mobile.data.remote.KiteGateway
import com.kiteplus.kite.mobile.data.remote.requireBody
import kotlinx.coroutines.CancellationException
import java.io.IOException

/**
 * Moves edits between this phone and the server.
 *
 * This class is the order and the excuses, nothing else: up first, down second,
 * and every way a phone can fail turned into a report instead of an exception.
 * The two directions live in [Pusher] and [Puller]; what to do when both sides
 * changed the same item lives in [ConflictResolver].
 *
 * The order is fixed and both halves are idempotent: pulling first would
 * overwrite text typed offline before it ever had a chance to be sent.
 */
class SyncEngine(
    private val dao: ContentDao,
    private val settings: SyncSettings,
) {
    private val pusher = Pusher(dao, ConflictResolver(dao))
    private val puller = Puller(dao, settings)

    suspend fun sync(): SyncReport {
        val api = settings.api() ?: return SyncReport(blocked = "未填写服务端地址")

        // A new article has to name a content type, and the server decides
        // which ones exist. Learned once, then remembered for offline editing.
        if (settings.defaultKind == null || settings.contentTypesJson == null) learnContentTypes(api)

        // A session that has quietly expired would otherwise turn every write
        // into an unexplained 401, so it is checked once up front.
        val session = try {
            api.session().requireBody()
        } catch (e: ApiFailure) {
            return SyncReport(
                blocked = if (e.isUnauthenticated) "需要重新登录" else "连接失败：${e.message}",
            )
        } catch (e: IOException) {
            // Being off the network is ordinary life for a phone, not a bug:
            // it has to come back as a report, never as an exception.
            return SyncReport(blocked = unreachable(e))
        } catch (e: CancellationException) {
            throw e
        }
        if (!session.authenticated) return SyncReport(blocked = "需要重新登录")

        return try {
            val pushed = pusher.run(api)
            val pulled = puller.run(api)
            SyncReport(
                pushed = pushed.pushed,
                conflicts = pushed.conflicts,
                pulled = pulled.pulled,
                error = pushed.error ?: pulled.error,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            SyncReport(error = unreachable(e))
        } catch (e: ApiFailure) {
            SyncReport(error = e.message ?: "服务端拒绝了这次同步")
        }
    }

    private fun unreachable(e: IOException): String =
        "连不上服务器（地址或网络不对）：${e.message ?: e.javaClass.simpleName}"

    /** Asks the site which kinds it declares, so a create names a real one. */
    private suspend fun learnContentTypes(api: KiteGateway) {
        val page = try {
            api.contentTypes().requireBody()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return
        }
        page.items.firstOrNull()?.kind?.takeIf { it.isNotBlank() }?.let {
            if (settings.defaultKind == null) settings.defaultKind = it
        }
        runCatching { KiteGateway.gson.toJson(page.items) }.getOrNull()
            ?.takeIf { it.isNotBlank() && it != "[]" }
            ?.let { settings.contentTypesJson = it }
    }
}
