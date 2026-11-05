package com.kiteplus.kite.mobile.data

import com.kiteplus.kite.mobile.api.model.ContentType
import com.kiteplus.kite.mobile.api.model.Field
import com.kiteplus.kite.mobile.data.remote.KiteGateway

/**
 * A content type as the site declares it, in the shape the editor needs.
 *
 * The kind is not a client-side constant: a site says which ones it has, which
 * taxonomies each carries, and which fields an item of that kind holds. Without
 * this the editor could only ever write a post with a title and a body, which
 * is not what the admin page offers.
 */
data class KindInfo(
    val kind: String,
    val label: String,
    val route: String,
    val taxonomies: List<String>,
    val newTerms: Map<String, List<String>>,
    /** The generated schema type: the site's own field definitions. */
    val fields: List<Field>,
) {
    /** The page prefix a slug sits after, e.g. `/posts/` for `/posts/:slug`. */
    val slugPrefix: String
        get() = if (route.contains(":slug")) route.substringBefore(":slug") else "/"

    /** The terms a new item of this kind starts with, copied per taxonomy. */
    fun startTerms(): Map<String, List<String>> = newTerms
}

/** Turns what `/content-types` answered into the list the editor works from. */
fun parseKinds(raw: String?): List<KindInfo> {
    val text = raw?.takeIf { it.isNotBlank() } ?: return emptyList()
    val types = runCatching {
        KiteGateway.gson.fromJson(text, Array<ContentType>::class.java).toList()
    }.getOrNull() ?: return emptyList()

    return types.mapNotNull { type ->
        type.kind.takeIf { it.isNotBlank() }?.let { kind ->
            KindInfo(
                kind = kind,
                label = type.label.ifBlank { kind },
                route = type.route,
                taxonomies = type.taxonomies.orEmpty(),
                newTerms = type.newTerms.orEmpty(),
                fields = type.fields.orEmpty().filter { it.key.isNotBlank() },
            )
        }
    }
}
