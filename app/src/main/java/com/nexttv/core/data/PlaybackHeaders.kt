// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.data

/** Same precedence for live playback, data preloading and optional channel previews. */
internal fun playbackHeaders(source:Source?,defaultUserAgent:String,defaultReferer:String,routeHeaders:Map<String,String> = emptyMap()):Map<String,String>{
 val headers=linkedMapOf<String,String>()
 fun put(name:String,value:String){headers.keys.firstOrNull{it.equals(name,true)}?.let{headers.remove(it)};headers[name]=value}
 ChannelRepository.parseHeaders(source?.headers.orEmpty()).forEach{(k,v)->put(k,v)}
 put("User-Agent",source?.userAgent?.ifBlank{defaultUserAgent}?:defaultUserAgent)
 val referer=source?.referer?.ifBlank{defaultReferer}?:defaultReferer
 if(referer.isNotBlank())put("Referer",referer)
 routeHeaders.forEach{(k,v)->put(k,v)}
 return headers
}

/** Match source/route User-Agent in both capability probing and actual media requests. */
internal fun playbackUserAgent(headers:Map<String,String>,fallback:String):String = headers.entries.firstOrNull{it.key.equals("User-Agent",true)}?.value?:fallback
