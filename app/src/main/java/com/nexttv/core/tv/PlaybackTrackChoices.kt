// SPDX-License-Identifier: GPL-3.0-only
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.nexttv.core.tv

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Tracks
import com.nexttv.core.tv.PlaybackSession.AudioChoice
import com.nexttv.core.tv.PlaybackSession.DecoderChoice

internal fun videoDecoderChoices(context:Context,format:Format?,memoryClass:Int):List<DecoderChoice>{
  val clear=format!=null&&format.drmInitData==null&&format.cryptoType==C.CRYPTO_TYPE_NONE
  val mime=format?.sampleMimeType
  val codecs=if(clear&&mime!=null)runCatching{androidx.media3.exoplayer.mediacodec.MediaCodecUtil.getDecoderInfos(mime,false,false).filter{it.isFormatSupported(context,format)}}.getOrDefault(emptyList())else emptyList()
  val hardware=codecs.any{it.hardwareAccelerated&&!softwareCodec(it)}
  val software=clear&&format!=null&&softwareVideoBudget(mime,format.width,format.height,format.frameRate,memoryClass,Runtime.getRuntime().availableProcessors(),false)&&codecs.any{softwareCodec(it)}
  return listOf(
   DecoderChoice("hardware","硬件解码",if(hardware)"使用硬件，降低处理器占用"else if(format==null)"加载视频后可选"else "当前视频暂不可用",hardware),
   DecoderChoice("software","软件解码",if(software)"硬解异常时可尝试"else if(format==null)"加载视频后可选"else "当前视频暂不可用",software)
  )
 }

internal fun playerAudioChoices(tracks:Tracks):List<AudioChoice> {
 val choices=buildList {
  tracks.groups.forEachIndexed{groupIndex,g->if(g.type==C.TRACK_TYPE_AUDIO)for(i in 0 until g.length){
   val f=g.getTrackFormat(i)
   val language=f.language?.takeIf{it.isNotBlank()&&it!="und"}?.let{java.util.Locale.forLanguageTag(it).getDisplayLanguage(java.util.Locale.SIMPLIFIED_CHINESE)}
   val label=com.nexttv.audioCodecLabel(f.sampleMimeType)?:"音轨 ${size+1}"
   val role=when{(f.roleFlags and C.ROLE_FLAG_DESCRIBES_VIDEO)!=0->"无障碍解说";(f.roleFlags and C.ROLE_FLAG_COMMENTARY)!=0->"解说";else->null}
   val detail=listOfNotNull(language?.takeIf{it.isNotBlank()&&it!=f.language},f.channelCount.takeIf{it>0}?.let{com.nexttv.audioChannelLabel(it)},role,if(!g.isTrackSupported(i))"当前设备暂不支持"else null).joinToString(" · ")
   add(AudioChoice("$groupIndex:$i",label,detail,g.isTrackSelected(i),g.isTrackSupported(i),g,i))
  }}
 }
 val counts=choices.groupingBy{it.label}.eachCount()
 val seen=mutableMapOf<String,Int>()
 return choices.map{choice->if((counts[choice.label]?:0)>1){val n=(seen[choice.label]?:0)+1;seen[choice.label]=n;choice.copy(label="${choice.label} $n")}else choice}
 }
