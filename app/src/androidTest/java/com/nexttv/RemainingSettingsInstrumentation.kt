package com.nexttv
import android.app.*
import android.content.Intent
import android.os.*
import com.nexttv.core.App
import com.nexttv.core.tv.*
class RemainingSettingsInstrumentation:Instrumentation(){
 override fun onCreate(b:Bundle?){super.onCreate(b);start()}
 override fun onStart(){val app=App.get();val prefs=targetContext.getSharedPreferences("nexttv",0);val saved=prefs.all;var activity:Activity?=null;var result=Activity.RESULT_OK;var report=""
 try{
  val bootstrap=startActivitySync(Intent().setClassName("com.nexttv","com.nexttv.core.tv.TvActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));Thread.sleep(3000);runOnMainSync{bootstrap.finish()};Thread.sleep(500)
  check(app.channels.channels.isNotEmpty())
  prefs.edit().putBoolean("start_live",true).putInt("preload_mode",3).apply()
  activity=startActivitySync(Intent().setClassName("com.nexttv","com.nexttv.core.tv.TvActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
  Thread.sleep(14000)
  var engine:PlaybackSession?=null;var count=0
  runOnMainSync{engine=PlaybackSession.active;check(engine!=null);val f=engine!!.javaClass.getDeclaredField("preloadPool");f.isAccessible=true;count=(f.get(engine) as DataPreloadPool).size()}
  check(count==2){"Expected two preheated neighbors, got $count"};report+="PASS direct live startup and two neighbor caches\n"
  for(mode in 0..2){runOnMainSync{app.prefs.preloadMode=mode;engine!!.applyPreloadSetting()};Thread.sleep(4500);runOnMainSync{val f=engine!!.javaClass.getDeclaredField("preloadPool");f.isAccessible=true;count=(f.get(engine) as DataPreloadPool).size()};check(count==if(mode==0)0 else 1);report+="PASS preheat mode $mode cache count $count\n"}
  runOnMainSync{engine!!.selectAutomaticAudio();check(engine!!.automaticAudio)};report+="PASS automatic audio selection\n"
 }catch(t:Throwable){result=Activity.RESULT_CANCELED;report+=android.util.Log.getStackTraceString(t)}finally{runOnMainSync{activity?.finish()};val e=prefs.edit().clear();saved.forEach{(k,v)->when(v){is String->e.putString(k,v);is Int->e.putInt(k,v);is Long->e.putLong(k,v);is Boolean->e.putBoolean(k,v);is Float->e.putFloat(k,v);is Set<*>->e.putStringSet(k,v.filterIsInstance<String>().toSet())}};e.commit()}
 finish(result,Bundle().apply{putString("stream",report)})
 }
}
