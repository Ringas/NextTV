package com.nexttv
import android.app.*
import android.os.Bundle
import com.nexttv.core.App
import com.nexttv.core.data.Source
import kotlinx.coroutines.runBlocking
/** Explicit emulator test preparation; no source is built into the production APK. */
class RealSourceSetupInstrumentation:Instrumentation(){
 override fun onCreate(b:Bundle?){super.onCreate(b);start()}
 override fun onStart(){waitForIdleSync();try{val app=App.get();listOf("epg-a","epg-b").forEach{app.channels.removeSource(it)};if(app.channels.sources.none{it.url=="http://192.0.2.115:8787/live.m3u"})app.channels.upsertSource(Source("real-regression","真实测试源","http://192.0.2.115:8787/live.m3u"));app.prefs.selectedGroup="";check(runBlocking{app.channels.refreshAll()}.ok>0);check(app.channels.channels.size>10);check(runBlocking{app.epg.refresh(true)});finish(Activity.RESULT_OK,Bundle().apply{putString("stream","PASS emulator prepared with real source: ${app.channels.channels.size} merged channels; no synthetic failure fixtures\n")})}catch(t:Throwable){finish(Activity.RESULT_CANCELED,Bundle().apply{putString("stream",t.stackTraceToString())})}}
}
