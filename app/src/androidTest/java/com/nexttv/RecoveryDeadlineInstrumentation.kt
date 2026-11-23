// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv
import android.app.*
import android.os.*
import com.nexttv.core.tv.PlaybackSession

/** Controlled stalls on a real stream; shortened production deadline, never substitute media. */
class RecoveryDeadlineInstrumentation:Instrumentation(){
 override fun onCreate(b:Bundle?){super.onCreate(b);start()}
 override fun onStart(){val d=RealPlaybackTestDriver(this);var result=Activity.RESULT_OK;var report="";var restore:(()->Unit)?=null
 try{
  d.openHome();d.click("CCTV1综合");check(d.awaitMain("real frame",45000){PlaybackSession.active?.rendered==true});val e=PlaybackSession.active!!;val original=e.app.channels.channels
  val field=e.app.channels.javaClass.getDeclaredField("channels").apply{isAccessible=true};val timeout=e.javaClass.getDeclaredField("stallTimeoutMillis").apply{isAccessible=true};val old=timeout.getLong(null)
  val watch=e.javaClass.getDeclaredMethod("watch",Int::class.javaPrimitiveType).apply{isAccessible=true}
  val one=e.current.copy(routes=listOf(e.current.playbackRoutes.first()))
  restore={d.main{timeout.setLong(null,old);field.set(e.app.channels,original)}}
  val times=mutableListOf<Long>()
  repeat(3){
   d.main{timeout.setLong(null,old);field.set(e.app.channels,listOf(one));e.tune(one)}
   check(d.awaitMain("round $it real first frame",45000){e.rendered})
   var serial=0;var attempts=1;var done=false
   d.main{timeout.setLong(null,4000);serial=e.tuneSerial;e.player.stop();watch.invoke(e,serial)}
   val begin=SystemClock.elapsedRealtime()
   while(SystemClock.elapsedRealtime()-begin<12000){
    d.main{if(e.tuneSerial!=serial){serial=e.tuneSerial;attempts++;e.player.stop();watch.invoke(e,serial)};if(e.player.playbackState!=androidx.media3.common.Player.STATE_IDLE)e.player.stop();done=e.error.startsWith("暂无可用频道")}
    if(done)break;SystemClock.sleep(20)
   }
   val elapsed=SystemClock.elapsedRealtime()-begin
   check(done&&attempts==2&&elapsed in 3600..6500){"deadline round=$it elapsed=$elapsed attempts=$attempts done=$done ${e.diagnostics()}"};times+=elapsed
  }
  report="PASS three controlled stalls share the 4000ms total channel budget, sole route retries once; elapsedMs=$times; real initial frames required in every round\n"
 }catch(t:Throwable){result=Activity.RESULT_CANCELED;report=t.stackTraceToString()}finally{restore?.invoke();d.close()}
 finish(result,Bundle().apply{putString("stream",report)})
 }
}
