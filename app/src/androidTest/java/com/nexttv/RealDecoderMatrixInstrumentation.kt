@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.nexttv

import android.app.*
import android.os.*
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import com.nexttv.core.App
import com.nexttv.core.tv.*
import org.json.*

/** Real fixed-source samples; failures never count as another channel's success. */
class RealDecoderMatrixInstrumentation:Instrumentation(){
 override fun onCreate(b:Bundle?){super.onCreate(b);start()}
 override fun onStart(){var result=Activity.RESULT_OK;var report="";val driver=RealPlaybackTestDriver(this)
 try{
  driver.openHome();driver.click("CCTV1综合");check(driver.awaitMain("real initial frame",45000){PlaybackSession.active?.rendered==true});val engine=PlaybackSession.active!!
  val samples=JSONArray();var passed=0
  for(name in listOf("CCTV1综合","北京卫视","CHC影迷电影","凤凰中文","明珠台","重温经典","TVB新闻","CGTN英语")){
   val target=App.get().channels.channels.firstOrNull{it.name==name}
   if(target==null){samples.put(JSONObject().put("name",name).put("status","NOT_IN_SOURCE"));continue}
   val start=SystemClock.elapsedRealtime();driver.main{engine.tune(target)}
   val ok=driver.awaitMain("real sample",30000){engine.current.id==target.id&&engine.rendered&&engine.player.audioFormat!=null}
   val sample=JSONObject().put("requested",name).put("actual",engine.current.name).put("status",if(ok)"REAL_VIDEO_AUDIO"else"NOT_CONFIRMED").put("wait_ms",SystemClock.elapsedRealtime()-start)
   driver.main{val video=engine.player.videoFormat;val audio=engine.player.audioFormat;sample.put("video",video?.sampleMimeType).put("codecs",video?.codecs).put("width",video?.width).put("height",video?.height).put("fps",video?.frameRate).put("audio",audio?.sampleMimeType).put("sample_rate",audio?.sampleRate).put("channels",audio?.channelCount).put("runtime",engine.diagnostics())}
   if(ok)passed++;samples.put(sample);sendStatus(0,Bundle().apply{putString("stream","REAL_CODEC_SAMPLE=$sample\n")})
  }
  val caps=JSONArray()
  for(mime in listOf("video/avc","video/hevc","video/mpeg2","video/av01","video/x-vnd.on2.vp9","audio/mp4a-latm","audio/mpeg","audio/mpeg-L2","audio/ac3","audio/eac3","audio/opus")){
   val row=JSONObject().put("mime",mime);val list=JSONArray();runCatching{MediaCodecUtil.getDecoderInfos(mime,false,false).forEach{list.put(JSONObject().put("name",it.name).put("hardware",it.hardwareAccelerated).put("software",it.softwareOnly))}};row.put("device_advertised_decoders",list);caps.put(row)
  }
  check(passed>=4){"Insufficient confirmed real video/audio samples: $samples"}
  report="REAL_CODEC_MATRIX="+JSONObject().put("confirmed",passed).put("samples",samples).put("capabilities_not_playback_proof",caps)+"\nPASS real source codec sample matrix: $passed confirmed video/audio channels; unsupported/unavailable samples retained explicitly\n"
 }catch(t:Throwable){result=Activity.RESULT_CANCELED;report=t.stackTraceToString()}finally{driver.close()}
 finish(result,Bundle().apply{putString("stream",report)})
 }
}
