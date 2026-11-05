package com.kiteplus.kite.mobile.domain

/** What a sync run did, in terms the UI can show in one line. */
data class SyncReport(
    val pushed: Int = 0,
    val pulled: Int = 0,
    val conflicts: Int = 0,
    /** Set when nothing could be attempted: no address, or no session. */
    val blocked: String? = null,
    /** The first failure, when the run could not finish. */
    val error: String? = null,
) {
    val didSomething: Boolean get() = pushed > 0 || pulled > 0 || conflicts > 0
}
