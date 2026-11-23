// SPDX-License-Identifier: GPL-3.0-only
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.nexttv
import android.app.*
import android.os.*
import com.nexttv.core.tv.PlaybackSession
/** Exercise every format-compatible AVC choice on the actual configured live source. */
class ManualAvcDecoderInstrumentation:Instrumentation(){
 override fun onCreate(b:Bundle?){super.onCreate(b);start()}
 override fun onStart(){val d=RealPlaybackTestDriver(this);var status=Activity.RESULT_OK;var report=""
 try{
  d.openHome();d.click("CCTV1综合");check(d.awaitMain("initial real frame",45000){PlaybackSession.active?.rendered==true});val e=PlaybackSession.active!!
  val c=e.app.channels.channels.first{it.name=="北京卫视"};d.main{e.tune(c)};check(d.awaitMain("real AVC frame",45000){e.current.id==c.id&&e.rendered&&e.player.videoFormat?.sampleMimeType=="video/avc"})
  var choices=emptyList<PlaybackSession.DecoderChoice>();d.main{choices=e.decoderChoices().filter{it.enabled}};check(choices.isNotEmpty())
  for(choice in choices){var before=0;val begin=SystemClock.elapsedRealtime();d.main{before=e.tuneSerial;check(e.selectDecoder(choice.id))}
   check(d.awaitMain("AVC ${choice.id}",45000){e.current.id==c.id&&e.tuneSerial>before&&e.rendered}){"${choice.id}: ${e.diagnostics()}"}
   check(e.diagnostics().contains("software=${choice.id=="software"} "));sendStatus(0,Bundle().apply{putString("stream","PASS real AVC manual ${choice.label}: ${choice.id}; recoveredMs=${SystemClock.elapsedRealtime()-begin}\n")})
  }
  report="AVC manual choices verified: ${choices.size}\n"
 }catch(t:Throwable){status=Activity.RESULT_CANCELED;report=t.stackTraceToString()}finally{d.close()}
 finish(status,Bundle().apply{putString("stream",report)})
 }
}
