package com.nexttv
import android.app.*
import android.content.Intent
import android.os.*
import com.nexttv.core.App
import kotlinx.coroutines.runBlocking
class AuditRealGuideInstrumentation:Instrumentation(){
 override fun onCreate(b:Bundle?){super.onCreate(b);start()}
 override fun onStart(){val a=startActivitySync(Intent().setClassName("com.nexttv","com.nexttv.core.tv.TvActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));val app=App.get();var status=Activity.RESULT_OK;var report=""
 try{check(runBlocking{app.epg.refresh(true)});val c=app.channels.channels.first();val n=app.epg.programsForDay(c,0).size;report="PASS real guide refreshed; channels=${app.channels.channels.size}; first=${c.name}; total-programmes=${app.epg.programsFor(c).size}; today=$n; source-count=${app.channels.sources.size}\n"}catch(t:Throwable){status=Activity.RESULT_CANCELED;report=t.stackTraceToString()}finally{runOnMainSync{a.finish()}}
 finish(status,Bundle().apply{putString("stream",report)})
 }
}
