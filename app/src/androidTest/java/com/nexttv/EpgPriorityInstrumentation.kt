package com.nexttv
import android.app.*
import android.os.*
import com.nexttv.core.App
import com.nexttv.core.data.Source
import com.nexttv.core.data.Channel
import kotlinx.coroutines.runBlocking
import java.net.ServerSocket
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
class EpgPriorityInstrumentation:Instrumentation(){
 override fun onCreate(b:Bundle?){super.onCreate(b);start()}
 override fun onStart(){val activity=startActivitySync(android.content.Intent().setClassName("com.nexttv","com.nexttv.core.tv.TvActivity").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();val app=App.get();val saved=app.prefs.sourcesJson;val manual=app.prefs.epgUrl;val server=ServerSocket(0);val port=server.localPort;val manualBroken=java.util.concurrent.atomic.AtomicBoolean(false)
 val date=SimpleDateFormat("yyyyMMddHHmmss Z",Locale.US).apply{timeZone=TimeZone.getTimeZone("UTC")};val start=date.format(Date(System.currentTimeMillis()-3600000));val end=date.format(Date(System.currentTimeMillis()+3600000))
 fun xml(title:String,id:String="one")="<?xml version=\"1.0\"?><tv><channel id=\"$id\"><display-name>$id</display-name></channel><programme start=\"$start\" stop=\"$end\" channel=\"$id\"><title>$title</title></programme></tv>"
 val thread=Thread{while(!server.isClosed){try{server.accept().use{sock->val path=sock.getInputStream().bufferedReader().readLine().orEmpty();val title=when{path.contains("/manual")->"手动优先";path.contains("/b.xml")->"来源B";else->"来源A"};val bad=manualBroken.get()&&path.contains("/manual");val body=if(bad)""else xml(title);sock.getOutputStream().write(("HTTP/1.1 ${if(bad)"503 Unavailable"else"200 OK"}\r\nContent-Length: ${body.toByteArray().size}\r\nConnection: close\r\n\r\n"+body).toByteArray())}}catch(_:Exception){}}}.apply{isDaemon=true;start()}
 fun cleanup(){server.close();thread.join(500);app.prefs.sourcesJson=saved;app.channels.loadSources();app.prefs.epgUrl=manual;targetContext.getSharedPreferences("nexttv",0).edit().putString("sources",saved).putString("manual_epg_url",manual).commit();app.epg.loadCache();runOnMainSync{activity.finish()}}
 try{
 app.prefs.sourcesJson="[]";app.channels.loadSources();app.prefs.epgUrl="";app.channels.upsertSource(Source("epg-a","A","http://127.0.0.1:$port/a.m3u",epgUrl="http://127.0.0.1:$port/a.xml"));app.channels.upsertSource(Source("epg-b","B","http://127.0.0.1:$port/b.m3u",epgUrl="http://127.0.0.1:$port/b.xml"))
 val a=Channel("a","one","one","","","http://example.test/a","epg-a");val b=a.copy(id="b",sourceId="epg-b")
 check(runBlocking{app.epg.refresh(true)});check(app.prefs.epgUrl.isBlank());check(app.epg.programsFor(a).first().title=="来源A");check(app.epg.programsFor(b).first().title=="来源B")
 app.prefs.epgUrl="http://127.0.0.1:$port/manual.xml";check(runBlocking{app.epg.refresh(true)});check(app.epg.programsFor(a).first().title=="手动优先");check(app.epg.programsFor(b).first().title=="手动优先")
 manualBroken.set(true);runBlocking{app.epg.refresh(true)};check(app.epg.programsFor(a).first().title=="手动优先");check(app.epg.lastError.contains("503"));app.prefs.epgUrl="";check(app.epg.programsFor(a).first().title=="来源A");check(app.epg.programsFor(b).first().title=="来源B")
 cleanup();finish(Activity.RESULT_OK,Bundle().apply{putString("stream","PASS intrinsic EPG does not backfill settings; same tvg-id stays source-isolated; standalone EPG wins; failed refresh retains proper cache; removing standalone falls back to each source\n")})
 }catch(t:Throwable){cleanup();finish(Activity.RESULT_CANCELED,Bundle().apply{putString("stream",t.stackTraceToString())})}finally{}}
}
