package com.kiteplus.kite.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kiteplus.kite.mobile.AppContainer
import com.kiteplus.kite.mobile.domain.SyncReport
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** Where the app is. One value, replaced rather than stacked: there are three
 *  screens and no deep hierarchy to model. */
sealed interface Screen {
    data object List : Screen
    data class Edit(val id: String) : Screen
    data object Settings : Screen
}


@Composable
/**
 * The app's shell: which screen is showing, the snackbar every screen shares, and the sync button's state.
 */
fun KiteRoot(container: AppContainer) {
    var screen by remember { mutableStateOf<Screen>(Screen.List) }
    var syncing by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    /**
     * Runs a sync and turns whatever happened into one sentence. Anything that escapes
     * the engine lands here as a message: an unreported exception in a phone app is a
     * crash, and being offline is not a crash.
     */
    suspend fun runSync() {
        syncing = true
        val report = try {
            container.syncEngine.sync()
        } catch (e: CancellationException) {
            throw e as Throwable
        } catch (e: Exception) {
            // Belt and braces. A failure to reach the server is a message, not
            // a reason for the process to die in the user's hand.
            SyncReport(error = e.message ?: e.javaClass.simpleName)
        } finally {
            syncing = false
        }
        snackbar.showSnackbar(describe(report))
    }

    // Opening the app is the most reliable moment to catch up; anything typed
    // offline is already in the database by then.
    LaunchedEffect(Unit) {
        if (container.settings.isConfigured) runSync()
    }

    BackHandler(enabled = screen !is Screen.List) { screen = Screen.List }

    Box(Modifier.fillMaxSize()) {
        when (val current = screen) {
            Screen.List -> ListScreen(
                container = container,
                syncing = syncing,
                onOpen = { screen = Screen.Edit(it) },
                onNew = {
                    scope.launch {
                        // Saved locally first, so the editor opens even with no
                        // connection and the text is never held in memory alone.
                        val id = container.repository.createDraft()
                        screen = Screen.Edit(id)
                    }
                },
                onSettings = { screen = Screen.Settings },
                onSync = { scope.launch { runSync() } },
            )

            is Screen.Edit -> EditorScreen(
                container = container,
                id = current.id,
                onBack = { screen = Screen.List },
            )

            Screen.Settings -> SettingsScreen(
                container = container,
                onBack = { screen = Screen.List },
                onSync = { scope.launch { runSync() } },
            )
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }
}

/**
 * Turns a report into the line the snackbar shows.
 */
private fun describe(report: SyncReport): String {
    report.blocked?.let { return it }
    val parts = buildList {
        if (report.pushed > 0) add("上传 ${report.pushed}")
        if (report.pulled > 0) add("下载 ${report.pulled}")
        if (report.conflicts > 0) add("冲突 ${report.conflicts}")
    }
    report.error?.let { return "同步中断：$it" }
    return if (parts.isEmpty()) "已是最新" else parts.joinToString(" · ")
}
