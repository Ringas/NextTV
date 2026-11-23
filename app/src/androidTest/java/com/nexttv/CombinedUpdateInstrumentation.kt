// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv

import android.app.*
import android.os.*
import com.nexttv.core.App

/** Debug internal integration: real repositories, manual update ignores automatic-update switches. */
class CombinedUpdateInstrumentation:Instrumentation(){
 override fun onCreate(arguments:Bundle?){super.onCreate(arguments);start()}
 override fun onStart(){startActivitySync(android.content.Intent().setClassName("com.nexttv","com.nexttv.core.tv.TvActivity").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));var status=Activity.RESULT_OK;var report="";val app=App.get();val before=app.prefs.autoUpdateEpg
 try {
  val deadline=SystemClock.uptimeMillis()+120000
  while(!app.cacheReady&&SystemClock.uptimeMillis()<deadline)SystemClock.sleep(100)
  check(app.channels.sources.any{it.enabled}){"No real subscription fixture; not a pass"}
  val updateStarted=SystemClock.elapsedRealtime()
  runOnMainSync{app.prefs.autoUpdateEpg=false;app.refreshSubscriptionsAndGuide();app.refreshSubscriptionsAndGuide()}
  while(app.updatingAll&&SystemClock.uptimeMillis()<deadline)SystemClock.sleep(100)
  val updateMs=SystemClock.elapsedRealtime()-updateStarted
  check(!app.updatingAll){"Combined update timeout"}
  check(app.sourceStatus.contains("成功")){app.sourceStatus}
  check(app.epg.hasData){"No actual EPG after manual update: ${app.epgStatus}"}
  check(app.updateStatus.contains(app.sourceStatus)&&app.updateStatus.contains(app.epgStatus)){app.updateStatus}
  report="PASS manual update executes subscription then real EPG with auto-update OFF; repeated click coalesces; durationMs=$updateMs; combined result=${app.updateStatus}\n"
 }catch(t:Throwable){status=Activity.RESULT_CANCELED;report=android.util.Log.getStackTraceString(t)}finally{runOnMainSync{app.prefs.autoUpdateEpg=before}}
 finish(status,Bundle().apply{putString("stream",report)})
 }
}
