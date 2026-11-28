// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.data

import java.security.MessageDigest

/**
 * Stable, non-reversible identity for a playback route. The UI still labels a
 * route from ChannelRoute; persisted journals and health maps only see this hash.
 */
internal object RouteKeys {
    fun of(route: ChannelRoute): String {
        val text = buildString {
            append(route.sourceId).append('\u0000')
            append(route.url.trim()).append('\u0000')
            route.headers.entries
                .sortedBy { it.key.lowercase() }
                .forEach { append(it.key.lowercase()).append('=').append(it.value).append('\u0001') }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
