package com.nexttv
import android.app.*
import android.content.Intent
import android.os.*
import com.nexttv.core.App
import com.nexttv.core.data.Source
import java.net.ServerSocket
import kotlin.concurrent.thread
/** Real subscription through a delayed local transport, no fabricated channel media. */
class SubmittedRefreshInstrumentation:Instrumentation(){
 override fun onCreate(args:Bundle?){super.onCreate(args);start()}
 override fun onStart(){val activity=startActivitySync(Intent().setClassName("com.nexttv","com.nexttv.core.tv.TvActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));val app=App.get();val old=app.prefs.autoUpdateEpg;val id="regression-delayed-source";var server:ServerSocket?=null;var result=Activity.RESULT_OK;var report=""
  try{
   val body=java.net.URL("http://192.0.2.115:8787/live.m3u").openConnection().apply{connectTimeout=8000;readTimeout=15000}.getInputStream().use{it.readBytes()}
   check(body.isNotEmpty());server=ServerSocket(0);val listener=server
   val accepted=java.util.concurrent.CountDownLatch(1)
   thread(isDaemon=true){listener.accept().use{socket->val reader=socket.getInputStream().bufferedReader();while(reader.readLine()?.isNotEmpty()==true){};accepted.countDown();Thread.sleep(3000);val out=socket.getOutputStream();out.write("HTTP/1.1 200 OK\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray());out.write(body);out.flush()}}
   runOnMainSync{app.prefs.autoUpdateEpg=false;app.channels.upsertSource(Source(id,"延迟刷新测试","http://127.0.0.1:${listener.localPort}/live.m3u"));app.refreshSources()}
   check(accepted.await(30,java.util.concurrent.TimeUnit.SECONDS));runOnMainSync{activity.finish()};waitForIdleSync()
   val end=SystemClock.uptimeMillis()+20000;while(SystemClock.uptimeMillis()<end&&(app.channels.channels.none{it.sourceId==id}||!app.sourceStatus.contains("成功")))SystemClock.sleep(100)
   check(app.channels.channels.any{it.sourceId==id});check(app.sourceStatus.contains("成功"));report="PASS P1-06 submitted real-source refresh completes after Activity/page disposal\n"
  }catch(t:Throwable){result=Activity.RESULT_CANCELED;report=android.util.Log.getStackTraceString(t)}
  finally{server?.close();runOnMainSync{app.channels.removeSource(id);app.prefs.autoUpdateEpg=old;if(!activity.isFinishing)activity.finish()}}
  finish(result,Bundle().apply{putString("stream",report)})
 }
}
