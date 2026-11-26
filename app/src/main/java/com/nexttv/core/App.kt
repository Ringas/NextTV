// SPDX-License-Identifier: GPL-3.0-only
// Next TV — Compose for TV implementation.
package com.nexttv.core

import android.app.Application
import kotlinx.coroutines.*
import androidx.compose.runtime.*
import com.nexttv.core.data.ChannelRepository
import com.nexttv.core.data.EpgRepository
import com.nexttv.core.data.Prefs
import com.nexttv.core.data.RouteHealth

class App : Application() {

    lateinit var prefs: Prefs
        private set
    lateinit var channels: ChannelRepository
        private set
    lateinit var epg: EpgRepository
        private set

    var cacheReady by mutableStateOf(false);private set
    var cacheReadyMillis=0L;private set
    val workScope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    var sourceStatus by mutableStateOf("");private set
    var epgStatus by mutableStateOf("");private set
    var epgUpdating by mutableStateOf(false);private set
    private var sourceJob:Job?=null
    var updatingAll by mutableStateOf(false);private set
    var updateStatus by mutableStateOf("");private set
    private var epgJob:Job?=null
    fun refreshSources(){if(updatingAll)return;sourceJob?.cancel();sourceJob=workScope.launch{
        sourceStatus="正在更新订阅…"
        try{val result=channels.refreshAll();sourceStatus=result.storageWarning.ifBlank{"${result.ok} 个成功，${result.failed} 个失败"};if(prefs.autoUpdateEpg)epg.refresh()}
        catch(e:CancellationException){throw e}catch(_:Exception){sourceStatus="订阅更新失败，已保留缓存"}
    }}
    fun refreshEpg(){if(epgUpdating||updatingAll)return;epgUpdating=true;epgJob=workScope.launch{
        epgStatus="正在更新节目单…"
        try{val ok=epg.refresh(true);epgStatus=epg.lastError.ifBlank{if(ok)"节目单已更新"else"节目单更新失败"}}
        catch(e:CancellationException){throw e}catch(_:Exception){epgStatus="节目单更新失败，已保留缓存"}
        finally{epgUpdating=false}
    }}
    /** Explicit manual update always refreshes both stages, independent of automatic preferences. */
    fun refreshSubscriptionsAndGuide(){
        if(updatingAll)return
        updatingAll=true
        workScope.launch {
            try {
                sourceJob?.join();epgJob?.join()
                updateStatus="正在更新订阅…"
                var sourceResult="订阅更新失败，已保留缓存"
                try { val result=channels.refreshAll();sourceResult=if(channels.sources.none{it.enabled})"尚无启用的订阅"else result.storageWarning.ifBlank{"订阅 ${result.ok} 个成功，${result.failed} 个失败"} }
                catch(e:CancellationException){throw e}catch(_:Exception){}
                sourceStatus=sourceResult
                updateStatus="正在更新节目单…"
                var guideResult="节目单更新失败，已保留缓存"
                try {val ok=epg.refresh(true);guideResult=epg.lastError.ifBlank{if(ok)"节目单已更新"else"节目单未更新"}}
                catch(e:CancellationException){throw e}catch(_:Exception){}
                epgStatus=guideResult
                updateStatus="$sourceResult；$guideResult"
            } finally {updatingAll=false}
        }
    }
    override fun onCreate() {
        super.onCreate()
        val previousCrashHandler=Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread,error ->
            try { java.io.File(filesDir,"last-crash.txt").writeText(
                "Next TV ${com.nexttv.BuildConfig.VERSION_NAME} | Android ${android.os.Build.VERSION.RELEASE} | ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} | ${java.util.Date()}\n"+
                com.nexttv.core.data.redactSensitive(android.util.Log.getStackTraceString(error)).take(64000)) } catch(_:Exception) {}
            previousCrashHandler?.uncaughtException(thread,error)
        }
        instance = this
        prefs = Prefs(this)
        com.nexttv.core.data.RouteFailureJournal.attach(this)
        RouteHealth.attach(this)
        com.nexttv.core.tv.AppMotionScale.install(this)
        channels = ChannelRepository(this, prefs)
        epg = EpgRepository(this, prefs)
        val started=android.os.SystemClock.elapsedRealtime()
        workScope.launch { try { withContext(Dispatchers.IO) {
        com.nexttv.core.tv.AppMotionScale.refresh(this@App)
        prefs.migrateSensitiveSettings()
        channels.loadCache()
        if(channels.sources.isEmpty()){
        val old=getSharedPreferences("iptv_prefs",0)
        channels.importLegacyCache(old.getString("playlist_text","").orEmpty(),old.getString("source_url","").orEmpty(),
            old.getStringSet("favorite_urls",emptySet()).orEmpty(),getSharedPreferences("tv_experience",0).getString("last_url","").orEmpty())
        }
        } } finally {cacheReadyMillis=android.os.SystemClock.elapsedRealtime()-started;cacheReady=true} }
    }

    override fun onTrimMemory(level:Int){super.onTrimMemory(level);if(level>=TRIM_MEMORY_RUNNING_LOW){com.nexttv.core.ui.LogoLoader.trimMemory();com.nexttv.core.tv.PlaybackSession.active?.trimMemory()}}

    companion object {
        internal var epgRefreshMillis=30*60_000L
        private lateinit var instance: App
        fun get(): App = instance
    }
}
