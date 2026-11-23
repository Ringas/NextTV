@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.nexttv

import android.app.*
import android.os.*
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlaybackException
import com.nexttv.core.tv.PlaybackSession

/** Inject an output error, then require real source video/audio to resume on the SAME route. */
class AudioDecoderRecoveryInstrumentation:Instrumentation(){
 override fun onCreate(b:Bundle?){super.onCreate(b);start()}
 override fun onStart(){var result=Activity.RESULT_OK;var report="";val driver=RealPlaybackTestDriver(this)
 try{
  val unsupported=Format.Builder().setSampleMimeType("audio/x-not-installed").build()
  val rejected=Tracks.Group(TrackGroup(unsupported),false,intArrayOf(C.FORMAT_UNSUPPORTED_SUBTYPE),booleanArrayOf(false))
  check(com.nexttv.core.tv.unsupportedAudioFormat(Tracks(com.google.common.collect.ImmutableList.of(rejected)))==unsupported)
  check(com.nexttv.core.tv.unsupportedAudioFormat(Tracks.EMPTY)==null)
  val supported=Tracks.Group(TrackGroup(Format.Builder().setSampleMimeType("audio/mp4a-latm").build()),false,intArrayOf(C.FORMAT_HANDLED),booleanArrayOf(false))
  check(com.nexttv.core.tv.unsupportedAudioFormat(Tracks(com.google.common.collect.ImmutableList.of(rejected,supported)))==null)
  val rejectedVideo=Format.Builder().setSampleMimeType("video/x-not-installed").setWidth(1920).setHeight(1080).build()
  val videoGroup=Tracks.Group(TrackGroup(rejectedVideo),false,intArrayOf(C.FORMAT_UNSUPPORTED_SUBTYPE),booleanArrayOf(false))
  val mixed=Tracks(com.google.common.collect.ImmutableList.of(videoGroup,supported))
  check(com.nexttv.core.tv.unsupportedTrackFormat(mixed,C.TRACK_TYPE_VIDEO)==rejectedVideo)
  check(com.nexttv.core.tv.unsupportedAudioFormat(mixed)==null)
  check(com.nexttv.core.tv.unsupportedTrackFormat(Tracks.EMPTY,C.TRACK_TYPE_VIDEO)==null)
  driver.openHome();driver.click("CCTV1综合");check(driver.awaitMain("real frame",45000){PlaybackSession.active?.let{it.rendered&&it.player.audioFormat!=null}==true})
  val engine=PlaybackSession.active!!;val id=engine.current.id;val route=engine.routeIndex;val serial=engine.tuneSerial
  driver.main{
   val target=engine.player;val method=engine.javaClass.declaredMethods.first{it.name=="listen"}.apply{isAccessible=true}
   val listener=method.invoke(engine,target,serial)as Player.Listener
   listener.onPlayerError(ExoPlaybackException.createForRenderer(IllegalStateException("simulated audio output rejection"),"AudioTrack",1,target.audioFormat!!,C.FORMAT_HANDLED,false,PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED))
  }
  check(driver.awaitMain("PCM retry real frame",45000){engine.tuneSerial>serial&&engine.rendered&&engine.player.audioFormat!=null})
  check(engine.current.id==id&&engine.routeIndex==route&&engine.diagnostics().contains("pcm=true"));check(engine.diagnostics().contains("audioRetries=1"))
  report="PASS simulated audio-output error retries PCM once on the same route and resumes actual video/audio; ${engine.diagnostics()}\n"
 }catch(t:Throwable){result=Activity.RESULT_CANCELED;report=t.stackTraceToString()}finally{driver.close()}
 finish(result,Bundle().apply{putString("stream",report)})
 }
}
