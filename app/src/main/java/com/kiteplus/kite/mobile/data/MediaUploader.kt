package com.kiteplus.kite.mobile.data

import com.kiteplus.kite.mobile.api.model.Media
import com.kiteplus.kite.mobile.data.remote.ApiFailure
import com.kiteplus.kite.mobile.data.remote.requireBody
import com.kiteplus.kite.mobile.domain.SyncSettings
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

private const val NO_ADDRESS = "还没填写服务端地址"

/**
 * Sends a picture to the server and hands back the link the markdown wants.
 *
 * Uploading needs the item to exist on the server already — a bundle is a
 * folder, and a folder needs a name — so the caller pushes a local draft first.
 * That is also why this is not part of sync: a photo is uploaded now, or it is
 * not uploaded, but it is never queued behind an edit.
 */
class MediaUploader(private val settings: SyncSettings) {

    /**
     * Sends one file to an existing article and returns what the server made of it.
     * 
     * The server refuses a create without an id, so a caller holding an unsaved draft
     * has to push it first; that rule lives in the editor, which knows the article is
     * still local. The part name is fixed at "file" by the server.
     */
    suspend fun upload(
        contentId: String,
        fileName: String,
        mime: String,
        bytes: ByteArray,
    ): Media {
        val api = settings.api() ?: throw ApiFailure(0, NO_ADDRESS)

        val request = bytes.toRequestBody(mime.toMediaTypeOrNull())
        // The server names this part: anything but "file" is a 400.
        val part = MultipartBody.Part.createFormData("file", fileName, request)

        return api.uploadMedia(contentId, part).requireBody()
    }
}
