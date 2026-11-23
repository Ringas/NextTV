package com.nexttv
import android.app.*
import android.content.Intent
import android.os.*
import com.nexttv.core.App
import com.nexttv.core.tv.TvActivity
import kotlinx.coroutines.runBlocking
class EpgPeriodicInstrumentation:Instrumentation(){
 override fun onCreate(b:Bundle?){super.onCreate(b);start()}
 override fun onStart(){waitForIdleSync();val app=App.get();val url=app.prefs.epgUrl;val auto=app.prefs.autoUpdateEpg;val startup=app.prefs.autoUpdateOnStart;var activity:Activity?=null
 try{app.prefs.epgUrl="http://192.0.2.115:8787/guide.xml";app.prefs.autoUpdateEpg=true;app.prefs.autoUpdateOnStart=false;check(runBlocking{app.epg.refresh(true)});val before=app.epg.updatedAt;App.epgRefreshMillis=1500;activity=startActivitySync(Intent(targetContext,TvActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));val until=SystemClock.uptimeMillis()+30000;while(app.epg.updatedAt<=before&&SystemClock.uptimeMillis()<until)SystemClock.sleep(100);check(app.epg.updatedAt>before);finish(Activity.RESULT_OK,Bundle().apply{putString("stream","PASS foreground periodic EPG refresh completes real XMLTV update with startup refresh disabled\n")})}catch(t:Throwable){finish(Activity.RESULT_CANCELED,Bundle().apply{putString("stream",t.stackTraceToString())})}finally{App.epgRefreshMillis=1800000;app.prefs.epgUrl=url;app.prefs.autoUpdateEpg=auto;app.prefs.autoUpdateOnStart=startup;runOnMainSync{activity?.finish()}}}
}
