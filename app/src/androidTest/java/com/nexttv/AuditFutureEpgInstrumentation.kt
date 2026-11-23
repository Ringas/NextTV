package com.nexttv
import android.app.*
import android.content.Intent
import android.os.*
import com.nexttv.core.App
import kotlinx.coroutines.runBlocking
import java.net.ServerSocket
import java.text.SimpleDateFormat
import java.util.*
class AuditFutureEpgInstrumentation:Instrumentation(){
 override fun onCreate(b:Bundle?){super.onCreate(b);start()}
 override fun onStart(){val activity=startActivitySync(Intent().setClassName("com.nexttv","com.nexttv.core.tv.TvActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));val app=App.get();val old=app.prefs.epgUrl;var server:ServerSocket?=null;var report="";var status=Activity.RESULT_OK
 try{
  val c=app.channels.channels.first();val id=c.tvgId.ifBlank{c.name};val f=SimpleDateFormat("yyyyMMddHHmmss Z",Locale.US).apply{timeZone=TimeZone.getTimeZone("UTC")};val now=System.currentTimeMillis()
  val xml="<tv><channel id=\"$id\"><display-name>${c.name}</display-name></channel><programme channel=\"$id\" start=\"${f.format(Date(now+3600000))}\" stop=\"${f.format(Date(now+7200000))}\"><title>未来节目审查探针</title></programme></tv>".toByteArray()
  server=ServerSocket(0);val listener=server
  Thread{try{listener.accept().use{s->val input=s.getInputStream().bufferedReader();while(input.readLine()?.isNotEmpty()==true){};s.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: ${xml.size}\r\nConnection: close\r\n\r\n".toByteArray()+xml)}}catch(_:Exception){}}.apply{isDaemon=true;start()}
  app.prefs.epgUrl="http://127.0.0.1:${listener.localPort}/future.xml";check(runBlocking{app.epg.refresh(true)})
  val current=app.epg.nowNext(c).first;report="PASS ${if(current!=null&&current.start>now)"REPRO"else"NOT_REPRO"} future-only guide returns future programme as current=${current?.title} (EPG-only local fixture, no media substitution)\n"
 }catch(t:Throwable){status=Activity.RESULT_CANCELED;report=t.stackTraceToString()}finally{server?.close();app.prefs.epgUrl=old;targetContext.getSharedPreferences("nexttv",0).edit().putString("manual_epg_url",old).commit();runOnMainSync{activity.finish()}}
 finish(status,Bundle().apply{putString("stream",report)})
 }
}
