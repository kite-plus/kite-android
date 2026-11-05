package com.kiteplus.kite.mobile.domain

import com.kiteplus.kite.mobile.data.remote.KiteGateway

/**
 * What the sync engine needs from the app, and nothing more.
 *
 * It exists so the engine can be tested on a plain JVM: the real implementation
 * reads SharedPreferences, which only exists on a device, while a test supplies
 * a client pointed at a local server and remembers the watermark in a field.
 */
interface SyncSettings {
    /** The client for the configured server, or null when none is configured. */
    fun api(): KiteGateway?

    /** The newest `updated_at` the server has confirmed. */
    var syncWatermark: String?

    /**
     * The content type a new item starts as, learned from `/content-types`.
     * Null until the site has been asked.
     */
    var defaultKind: String?

    /**
     * The types themselves as JSON, kept so the editor can show an item's
     * fields while offline.
     */
    var contentTypesJson: String?
}
