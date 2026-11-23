package com.nexttv

import android.app.*
import android.content.Intent
import android.os.*
import com.nexttv.core.App
import com.nexttv.core.data.Source
import kotlinx.coroutines.runBlocking

/** Explicit local-test setup. Intentionally persists the user-authorized real source between tests. */
class RealSourceFixtureInstrumentation:Instrumentation(){
 override fun onCreate(b:Bundle?){super.onCreate(b);start()}
 override fun onStart(){var activity:Activity?=null;var result=Activity.RESULT_OK;var report=""
 try{
  targetContext.getSharedPreferences("nexttv",0).edit().putBoolean("start_live",false).putBoolean("auto_update",false).putBoolean("auto_update_epg",false).putBoolean("previews",false).putInt("preload_mode",0).commit()
  activity=startActivitySync(Intent().setClassName("com.nexttv","com.nexttv.core.tv.TvActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
  val end=SystemClock.uptimeMillis()+20000
  while(!App.get().cacheReady&&SystemClock.uptimeMillis()<end)SystemClock.sleep(50)
  check(App.get().cacheReady)
  val app=App.get();val url="http://192.0.2.115:8787/live.m3u"
  for(source in app.channels.sources.toList())if(source.url!=url&&source.enabled)app.channels.upsertSource(source.copy(enabled=false))
  val fixed=app.channels.sources.firstOrNull{it.url==url}
  app.channels.upsertSource(fixed?.copy(enabled=true)?:Source("nexttv-real-test-source","真实测试源",url))
  app.prefs.epgUrl=""
  check(runBlocking{app.channels.refreshAll()}.ok>0)
  check(app.channels.channels.any{it.name=="CCTV1综合"}&&app.channels.channels.any{it.name=="CCTV2财经"})
  check(runBlocking{app.epg.refresh(true)}){app.epg.lastError};check(app.epg.hasData)
  report="PASS persisted fixed real subscription: ${app.channels.channels.size} channels, source configuration and actual EPG survive test completion\n"
 }catch(t:Throwable){result=Activity.RESULT_CANCELED;report=t.stackTraceToString()}finally{runOnMainSync{activity?.finish()}}
 finish(result,Bundle().apply{putString("stream",report)})
 }
}
