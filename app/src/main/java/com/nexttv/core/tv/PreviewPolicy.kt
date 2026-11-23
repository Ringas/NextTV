package com.nexttv.core.tv
import com.nexttv.core.data.ChannelRoute
import com.nexttv.supportsHttpPlayback
/** Prefer supplied HLS routes that need no extra type probe. Never changes the main selected route. */
internal fun previewRouteOrder(routes:List<ChannelRoute>):List<ChannelRoute> = routes.filter{supportsHttpPlayback(it.url)}.sortedBy{if(it.url.substringBefore('?').substringBefore('#').endsWith(".m3u8",true))0 else 1}

/** Spread new stream startup; already started visible channels keep playing concurrently. */
internal fun previewStartDelay(slot:Int):Long=slot.coerceAtLeast(0).toLong()*2200L
