package com.nexttv
import android.app.*
import android.content.*
import android.os.*
import com.nexttv.core.App
import com.nexttv.core.data.*
import kotlinx.coroutines.runBlocking
import java.io.File
/** Real Android parsers/preferences with isolated metadata; no synthetic media playback. */
class SixOptimizationInstrumentation:Instrumentation(){
 override fun onCreate(b:Bundle?){super.onCreate(b);start()}
 override fun onStart(){var result=Activity.RESULT_OK;val lines=mutableListOf<String>();var activity:Activity?=null
 try{
 activity=startActivitySync(Intent().setClassName("com.nexttv","com.nexttv.core.tv.TvActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
 val deadline=SystemClock.uptimeMillis()+15000;while(!App.get().cacheReady&&SystemClock.uptimeMillis()<deadline)SystemClock.sleep(50);check(App.get().cacheReady)
 val dir=File(targetContext.cacheDir,"six-${System.nanoTime()}").apply{mkdirs()}
 val isolated=object:ContextWrapper(targetContext){override fun getApplicationContext():Context=this;override fun getFilesDir()=dir;override fun getCacheDir()=dir;override fun getSharedPreferences(n:String,m:Int)=baseContext.getSharedPreferences(dir.name+n,m)}
 try{
 val prefs=Prefs(isolated);val repo=ChannelRepository(isolated,prefs);val source=File(dir,"source.m3u")
 fun playlist(names:List<String>){source.writeText("#EXTM3U\n"+names.joinToString(""){"#EXTINF:-1 group-title=\"测试\",$it\nhttp://127.0.0.1/live/$it\n"})}
 playlist(listOf("Alpha","Beta","Gamma"));repo.upsertSource(Source("isolated","测试",source.path,kind=Source.KIND_FILE));check(runBlocking{repo.refreshAll()}.ok==1)
 val original=repo.channels.associate{it.name to it.number};val alpha=repo.channels.first{it.name=="Alpha"};prefs.toggleFavorite(alpha.id)
 check(prefs.favorites===prefs.favorites);check(prefs.isFavorite(alpha.id))
 isolated.getSharedPreferences("nexttv",0).edit().putStringSet("favorites",setOf("external")).commit();check(prefs.favorites==setOf("external"));prefs.toggleFavorite(alpha.id)
 lines+="PASS favorite snapshot reused and invalidated by external SharedPreferences edit"
 playlist(listOf("Gamma","Beta","Alpha","Delta"));check(runBlocking{repo.refreshAll()}.ok==1);check(repo.channels.filter{it.name!="Delta"}.all{original[it.name]==it.number});check(repo.channels.last().number>original.values.max())
 lines+="PASS channel numbers stable across reorder and append"
 repo.upsertSource(repo.sources.single().copy(enabled=false));check(repo.channels.isEmpty());runBlocking{repo.refreshAll()};repo.upsertSource(repo.sources.single().copy(enabled=true));check(repo.channels.size==4)
 lines+="PASS disabled source retains raw cache through refresh and re-enable"
 val restored=ChannelRepository(isolated,prefs);restored.loadCache();check(restored.channels.map{it.id to it.number}==repo.channels.map{it.id to it.number})
 lines+="PASS streaming channel cache round trip preserves identities and stable numbers"
 val xml=File(dir,"guide.xml");prefs.epgUrl=xml.path;val now=System.currentTimeMillis();val fmt=java.text.SimpleDateFormat("yyyyMMddHHmmss Z",java.util.Locale.US)
 xml.writeText("<tv><channel id=\"alpha\"><display-name>Alpha</display-name></channel><programme channel=\"alpha\" start=\"${fmt.format(java.util.Date(now-60000))}\" stop=\"${fmt.format(java.util.Date(now+60000))}\"><title>当前节目</title></programme></tv>")
 val epg=EpgRepository(isolated,prefs);check(runBlocking{epg.refresh(true)});val cached=EpgRepository(isolated,prefs);cached.loadCache();check(cached.nowNext(alpha).first?.title=="当前节目")
 val list=cached.programsFor(alpha);check(list===cached.programsFor(alpha));cached.loadCache();check(list===cached.programsFor(alpha))
 lines+="PASS streaming EPG cache aliases, programme lookup cache and repeated cache load"
 restored.removeSource("isolated");check(restored.channels.isEmpty());check(!prefs.isFavorite(alpha.id));check(prefs.isFavorite("external"))
 lines+="PASS explicit source deletion removes only proven source favorites"
 }finally{dir.deleteRecursively()}
 }catch(t:Throwable){result=Activity.RESULT_CANCELED;lines+=t.stackTraceToString()}finally{runOnMainSync{activity?.finish()}}
 finish(result,Bundle().apply{putString("stream",lines.joinToString("\n")+"\n")})
 }
}
