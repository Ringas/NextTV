// SPDX-License-Identifier: GPL-3.0-only
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.nexttv.core.tv

import android.content.Context
import androidx.media3.common.Format
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import com.nexttv.NextTvRenderersFactory

internal data class DecoderPreferences(val videoSoftwareMime:String?=null,val audioSoftwareMime:String?=null,val preferMp2Software:Boolean=false,val forcePcm:Boolean=false,val discouragedVideoNames:Set<String> = emptySet(),val videoHardwareOnly:Boolean=false){
 val defaultSelection get()=videoSoftwareMime==null&&audioSoftwareMime==null&&!preferMp2Software&&!forcePcm&&!videoHardwareOnly
}
/** Some platform aliases report neither flag; known system software names are still software. */
internal fun knownSoftwareDecoder(name:String,declaredSoftware:Boolean)=declaredSoftware||name.startsWith("OMX.google.",true)||name.startsWith("c2.android.",true)
internal fun softwareCodec(info:androidx.media3.exoplayer.mediacodec.MediaCodecInfo)=knownSoftwareDecoder(info.name,info.softwareOnly)

/** Device capabilities remain authoritative; this budget only gates an additional forced retry. */
internal fun softwareVideoBudget(mime:String?,width:Int,height:Int,fps:Float,heapMb:Int,cores:Int,encrypted:Boolean):Boolean {
 if(encrypted||width<=0||height<=0||width.toLong()*height>1920L*1080||height>1080||heapMb<192)return false
 val rate=if(fps>0)fps else 30f
 return when(mime){
  "video/avc","video/mpeg2","video/mp4v-es"->cores>=2&&rate<=60f
  "video/hevc","video/x-vnd.on2.vp9"->cores>=4&&rate<=30f
  "video/av01"->cores>=8&&heapMb>=256&&rate<=30f
  else->false
 }
}
internal object DecoderCompatibility {
 fun renderers(context:Context,preferences:DecoderPreferences=DecoderPreferences(),preferredName:(String)->String?={null}):DefaultRenderersFactory =
  NextTvRenderersFactory(context,preferences.preferMp2Software,preferences.forcePcm).setEnableDecoderFallback(true)
   .setMediaCodecSelector{mime,secure,tunnel->
    val codecs=MediaCodecUtil.getDecoderInfos(mime,secure,tunnel)
    val preferred=preferredName(mime)
    if(mime.startsWith("video/")&&preferences.videoHardwareOnly)codecs.filter{it.hardwareAccelerated&&!softwareCodec(it)}
    else if(mime.startsWith("video/")&&preferences.videoSoftwareMime!=null){if(!secure&&!tunnel&&mime==preferences.videoSoftwareMime)codecs.filter{softwareCodec(it)}else emptyList()}

    else if(!secure&&!tunnel&&mime==preferences.audioSoftwareMime)codecs.filter{softwareCodec(it)}
    else codecs.sortedBy{(if(it.name in preferences.discouragedVideoNames)10 else 0)+when{softwareCodec(it)->4;it.hardwareAccelerated->0;else->2}+(if(it.name==preferred)0 else 1)}
   }
 fun softwareSupports(context:Context,format:Format):Boolean {
  if(format.drmInitData!=null||format.cryptoType!=androidx.media3.common.C.CRYPTO_TYPE_NONE)return false
  val mime=format.sampleMimeType?:return false
  return runCatching{MediaCodecUtil.getDecoderInfos(mime,false,false).any{softwareCodec(it)&&it.isFormatSupported(context,format)}}.getOrDefault(false)
 }
}

/** Missing tracks are valid; advertised tracks with no supported or selected alternative need recovery. */
internal fun unsupportedTrackFormat(tracks:androidx.media3.common.Tracks,type:Int):Format? {
 var first:Format?=null
 for(group in tracks.groups)if(group.type==type){
  for(i in 0 until group.length){if(group.isTrackSelected(i)||group.isTrackSupported(i))return null;if(first==null)first=group.getTrackFormat(i)}
 }
 return first
}

internal fun unsupportedAudioFormat(tracks:androidx.media3.common.Tracks):Format?=unsupportedTrackFormat(tracks,androidx.media3.common.C.TRACK_TYPE_AUDIO)
