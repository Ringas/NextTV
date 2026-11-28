// SPDX-License-Identifier: GPL-3.0-only
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.nexttv.core.tv

import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import com.nexttv.sameHttpOrigin

/**
 * Credentials are scoped to the origin they were configured for. Every open
 * request (playlist, playlist segment, retry) gets headers only when its URI
 * still points at that origin; a cross-host segment or redirect never sees them.
 */
internal class OriginScopedHttpDataSourceFactory(
    private val inner: HttpDataSource.Factory,
    private val allowedOriginUrl: String,
    private val headers: Map<String, String>,
) : HttpDataSource.Factory {
    override fun createDataSource(): HttpDataSource {
        val delegate = inner.createDataSource()
        return object : HttpDataSource {
            override fun open(dataSpec: DataSpec): Long {
                if (sameHttpOrigin(dataSpec.uri.toString(), allowedOriginUrl)) {
                    headers.forEach { (key, value) -> delegate.setRequestProperty(key, value) }
                    return delegate.open(dataSpec)
                }
                delegate.clearAllRequestProperties()
                return delegate.open(dataSpec.withRequestHeaders(emptyMap()))
            }

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int = delegate.read(buffer, offset, length)
            override fun close() = delegate.close()
            override fun getUri() = delegate.uri
            override fun getResponseCode(): Int = delegate.responseCode
            override fun getResponseHeaders(): Map<String, List<String>> = delegate.responseHeaders
            override fun setRequestProperty(name: String, value: String) = delegate.setRequestProperty(name, value)
            override fun clearRequestProperty(name: String) = delegate.clearRequestProperty(name)
            override fun clearAllRequestProperties() = delegate.clearAllRequestProperties()
            override fun addTransferListener(transferListener: TransferListener) = delegate.addTransferListener(transferListener)
        }
    }

    override fun setDefaultRequestProperties(defaultRequestProperties: Map<String, String>): HttpDataSource.Factory = this
}
