package com.nexttv
import android.app.*
import android.content.Intent
import android.os.*
import com.nexttv.core.App
import kotlinx.coroutines.runBlocking
class AuditRestoreGuideInstrumentation:Instrumentation(){
 override fun onCreate(b:Bundle?){super.onCreate(b);start()}
 override fun onStart(){val a=startActivitySync(Intent().setClassName("com.nexttv","com.nexttv.core.tv.TvActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));var status=Activity.RESULT_OK;var report=""
 try{
  val app=App.get();val prefs=targetContext.getSharedPreferences("nexttv",0);val original="http://192.0.2.115:8787/guide.xml"
  check(prefs.edit().putString("manual_epg_url",original).putBoolean("start_live",false).putBoolean("show_channel_logos",false).commit());check(runBlocking{app.epg.refresh(true)});val n=app.epg.programsForDay(app.channels.channels.first(),0).size;check(n>10);report="PASS audit fixture cleanup committed; original manual guide restored; real first-channel today programmes=$n\n"
 }catch(t:Throwable){status=Activity.RESULT_CANCELED;report=t.stackTraceToString()}finally{runOnMainSync{a.finish()}}
 finish(status,Bundle().apply{putString("stream",report)})
 }
}
