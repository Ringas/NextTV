package com.nexttv.core.tv

import com.nexttv.resolutionLabel
import com.nexttv.audioChannelLabel
import com.nexttv.videoCodecLabel
import com.nexttv.transferRateLabel

/** One owner for each visible technical fact. No raw MIME/debug state in the viewing UI. */
internal fun playbackFacts(routeCount:Int,routeIndex:Int,height:Int,mime:String?,channels:Int,bytesPerSecond:Long?):List<String> = buildList {
 if(routeCount>1)add("线路 ${routeIndex+1}/$routeCount")
 if(height>0)add(resolutionLabel(height))
 videoCodecLabel(mime)?.let{add(it)}
 if(channels>0)add(audioChannelLabel(channels))
 if(bytesPerSecond!=null&&bytesPerSecond>=0)add(transferRateLabel(bytesPerSecond,showBytes=false))
}

/** Compact identity row: route, resolution, audio channels, actual receive speed. */
internal fun playbackIdentityFacts(routeCount:Int,routeIndex:Int,height:Int,channels:Int,bytesPerSecond:Long?):List<String> = listOf(
 "${routeIndex.coerceIn(0,(routeCount-1).coerceAtLeast(0))+1}/${routeCount.coerceAtLeast(1)}",
 resolutionLabel(height),audioChannelLabel(channels),transferRateLabel(bytesPerSecond)
)
