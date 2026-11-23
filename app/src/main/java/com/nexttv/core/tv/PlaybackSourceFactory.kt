// SPDX-License-Identifier: GPL-3.0-only
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.nexttv.core.tv

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.nexttv.core.App
import com.nexttv.core.data.Channel
import com.nexttv.core.data.Program
import com.nexttv.core.data.playbackHeaders
import com.nexttv.core.data.playbackUserAgent
import com.nexttv.core.data.replayAddress
import com.nexttv.resolveStream

internal class PlaybackSourceFactory(
 private val app: App,
 private val transfer: TransferListener,
 private val isCurrent: (String, Int, Long?) -> Boolean,
) {
 suspend fun create(channel:Channel,index:Int,replay:Program?=null,onPreloadHttpFailure:((Exception)->Unit)?=null):androidx.media3.exoplayer.source.MediaSource{
  val route=channel.playbackRoutes[index]
  val src=app.channels.sources.firstOrNull{it.id==route.sourceId}
  val headers=playbackHeaders(src,app.prefs.defaultUserAgent,app.prefs.defaultReferer,route.headers)
  val ua=playbackUserAgent(headers,app.prefs.defaultUserAgent)
  if(!com.nexttv.supportsHttpPlayback(route.url))throw UnsupportedPlaybackProtocol()
  val resolved=resolveStream(replay?.let{replayAddress(route,it,System.currentTimeMillis())}?:route.url,headers)
  val http=DefaultHttpDataSource.Factory().setUserAgent(ua).setAllowCrossProtocolRedirects(true).setDefaultRequestProperties(headers).setConnectTimeoutMs(8000).setReadTimeoutMs(10000)
  val expectedReplay=replay?.start
  http.setTransferListener(object:androidx.media3.datasource.TransferListener {
   override fun onTransferInitializing(s:androidx.media3.datasource.DataSource,d:androidx.media3.datasource.DataSpec,n:Boolean){}
   override fun onTransferStart(s:androidx.media3.datasource.DataSource,d:androidx.media3.datasource.DataSpec,n:Boolean){}
   override fun onTransferEnd(s:androidx.media3.datasource.DataSource,d:androidx.media3.datasource.DataSpec,n:Boolean){}
   override fun onBytesTransferred(s:androidx.media3.datasource.DataSource,d:androidx.media3.datasource.DataSpec,n:Boolean,bytes:Int){if(isCurrent(channel.id,index,expectedReplay))transfer.onBytesTransferred(s,d,n,bytes)}
  })
  val factory=DefaultMediaSourceFactory(http)
  if(onPreloadHttpFailure!=null)factory.setLoadErrorHandlingPolicy(object:androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy(){
   override fun getRetryDelayMsFor(info:androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.LoadErrorInfo):Long {
    if(!isConfirmedPreloadFailure(info.exception))return super.getRetryDelayMsFor(info)
    onPreloadHttpFailure(info.exception)
    return C.TIME_UNSET
   }
  })
  return factory.createMediaSource(MediaItem.Builder().setMediaId("${channel.id}:$index:${System.nanoTime()}").setUri(resolved.url).setMimeType(resolved.mime).build())
 }
}

internal fun isConfirmedPreloadFailure(error:Throwable):Boolean =
  generateSequence(error){it.cause}.take(12).any {
   val status=when(it){
    is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException->it.responseCode
    is com.nexttv.StreamHttpStatusException->it.status
    else->null
   }
   status==404||status==410
  }
