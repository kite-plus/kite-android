package com.kiteplus.kite.mobile

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A crash that only exists on the phone is a crash nobody can fix. This writes
 * the stack into a file the device's file manager can reach, so a startup
 * failure can be read back instead of guessed at.
 *
 * The file is appended, not replaced: two crashes in a row are usually two
 * different failures, and the first one is the interesting one.
 */
object CrashLog {

    /**
     * `/sdcard/Android/data/<package>/files/logs/crash.txt`, or the app's
     * internal storage when no shared volume is mounted.
     */
    fun locate(context: Context): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(File(base, "logs").apply { mkdirs() }, "crash.txt")
    }

    /** Installs the handler. Call before anything else starts. */
    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val trace = StringWriter()
                error.printStackTrace(PrintWriter(trace))
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                locate(app).appendText(
                    buildString {
                        append("=== ").append(stamp)
                        append(" · ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                        append(" · API ").append(Build.VERSION.SDK_INT)
                        append(" · thread=").append(thread.name).append('\n')
                        append(trace)
                        append('\n')
                    },
                )
            }
            // Hand over to the platform so the process still dies the way it
            // would have: swallowing it here would leave a half-dead app.
            previous?.uncaughtException(thread, error)
        }
    }

    /** Everything recorded so far, for a "what went wrong" screen. */
    fun read(context: Context): String = runCatching {
        locate(context).readText()
    }.getOrDefault("")
}
