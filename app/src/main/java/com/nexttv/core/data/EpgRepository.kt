// SPDX-License-Identifier: GPL-3.0-only
// Next TV — Compose for TV implementation.
package com.nexttv.core.data

import com.nexttv.core.io.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

import androidx.compose.runtime.*
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone

/**
 * XMLTV 电子节目单。支持远程 URL 与本地文件，解析结果落盘缓存。
 */
class EpgRepository(private val context: Context, private val prefs: Prefs) {
    var revision by mutableIntStateOf(0);private set
    private val refreshLock=Mutex()
    private val lookup = java.util.concurrent.ConcurrentHashMap<String, Triple<Channel,String,List<Program>>>()
    @Volatile private var lookupContext = ""

    private data class Feed(val parsed:XmltvFeed,val time:Long)
    private val feeds=java.util.concurrent.ConcurrentHashMap<String,Feed>()
    private val aggregate=EpgAggregateLimiter()
    var loaded=false;private set
    var updatedAt=0L;private set
    var lastError="";private set
    private fun sources()=com.nexttv.core.App.get().channels.sources.filter{it.enabled&&it.epgUrl.isNotBlank()}
    private fun urls()=(listOf(prefs.epgUrl)+sources().map{it.epgUrl}).filter{it.isNotBlank()}.distinct()
    private fun file(url:String):File {val name=java.security.MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString(""){"%02x".format(it)};return File(context.filesDir,"epg_feeds/$name.json")}
    private fun updateState(){lookup.clear();val activeUrls=urls().toSet();feeds.keys.retainAll(activeUrls);aggregate.retain(activeUrls);val active=activeUrls.mapNotNull{feeds[it]};loaded=active.any{it.parsed.programmes.isNotEmpty()};updatedAt=active.maxOfOrNull{it.time}?:0L;revision++}
    fun loadCache(){
        val before=feeds.toMap()
        urls().take(16).filterNot{feeds.containsKey(it)}.forEach{url->val f=file(url);if(f.exists()||File(f.path+".bak").exists())runCatching{
            if(f.length()>ImportPolicy.EPG_CACHE_BYTES)throw ImportLimitException("节目单缓存过大")
            val value=ImportBudget("节目单缓存",10000).use{budget->
                android.util.JsonReader(BoundedJsonReader(budget.input(android.util.AtomicFile(f).openRead(),ImportPolicy.EPG_CACHE_BYTES).reader())).use{reader->
                    var count=0;var chars=0L;var time=0L
                    val map=hashMapOf<String,List<Program>>()
                    val alias=hashMapOf<String,String>();val names=hashMapOf<String,String>()
                    fun text():String {val v=reader.nextString();chars+=v.length;if(v.length>ImportPolicy.FIELD_CHARS||chars>ImportPolicy.TEXT_CHARS)throw ImportLimitException("节目文本超过上限");return v}
                    reader.beginObject()
                    while(reader.hasNext()) {budget.check();val field=reader.nextName();when(field){
                        "updatedAt"->time=reader.nextLong()
                        "channels"->{reader.beginObject();while(reader.hasNext()){
                            val id=reader.nextName();if(id.length>512||map.size>=ImportPolicy.EPG_CHANNELS)throw ImportLimitException("节目频道超过上限")
                            val entries=ArrayList<Program>();reader.beginArray()
                            while(reader.hasNext()){
                                if(++count>ImportPolicy.PROGRAMMES)throw ImportLimitException("节目数量超过上限")
                                var channel=id;var title="";var desc="";var start=0L;var end=0L
                                reader.beginObject();while(reader.hasNext())when(reader.nextName()){
                                    "c"->channel=text();"t"->title=text();"d"->desc=text();"s"->start=reader.nextLong();"e"->end=reader.nextLong();else->reader.skipValue()
                                };reader.endObject();entries.add(Program(channel,title,desc,start,end))
                            };reader.endArray();entries.sortBy{it.start};map[id]=entries
                        };reader.endObject()}
                        "alias","names"->{val destination=if(field=="alias")alias else names
                            reader.beginObject();while(reader.hasNext()){
                                if(destination.size>=20000)throw ImportLimitException("节目别名超过上限")
                                val key=reader.nextName();chars+=key.length;if(key.length>ImportPolicy.FIELD_CHARS)throw ImportLimitException("节目字段过长");destination[key]=text()
                            };reader.endObject()}
                        else->reader.skipValue()
                    }};reader.endObject();Feed(XmltvFeed(map,alias,names),time)
                }
            }
            if(!aggregate.admit(url,programmeCount(value.parsed),epgTextWeight(value.parsed.programmes,value.parsed.aliases,value.parsed.displayNames)))throw ImportLimitException("节目单总节目数量超过上限")
            synchronized(feeds){if((feeds[url]?.time?:0)<=value.time)feeds[url]=value}
        }}
        if(before!=feeds||feeds.keys.any{it !in urls()}||!loaded)updateState()
    }
    val hasData:Boolean get()=loaded
    suspend fun refresh(force:Boolean=false):Boolean=refreshLock.withLock{withContext(Dispatchers.IO){
        val manual=prefs.epgUrl;val requested=urls()
        if(requested.size>16){lastError="节目单来源超过16个，请减少后重试";return@withContext false}
        if(requested.isEmpty()){updateState();lastError="没有单独订阅或播放源内置节目单";return@withContext false}
        if(requested.any{!feeds.containsKey(it)})loadCache()
        val outcomes=coroutineScope{
            val gate=Semaphore(EPG_CONCURRENCY)
            requested.map{url->async{
                gate.withPermit{refreshOne(url,manual,force)}
            }}.awaitAll()
        }
        val success=outcomes.any{it.ok}
        val manualError=outcomes.firstOrNull{it.url==manual&&!it.ok}?.error.orEmpty()
        val sourceError=outcomes.firstOrNull{it.url!=manual&&!it.ok}?.error.orEmpty()
        updateState();if(prefs.epgUrl==manual)lastError=manualError.ifBlank{if(sourceError.isNotBlank())"部分源内置节目单更新失败：$sourceError" else ""}
        success
    }}

    private data class RefreshOutcome(val url:String,val ok:Boolean,val error:String)

    /** 单个节目单源：独立 30 s 预算，失败只影响该源，不再被全局 60 s 排队饿死。 */
    private suspend fun refreshOne(url:String,manual:String,force:Boolean):RefreshOutcome{
        try{
            val cached=feeds[url]
            if(!force&&cached!=null&&System.currentTimeMillis()-cached.time<STALE_MS)return RefreshOutcome(url,true,"")
            val job=currentCoroutineContext()[Job]
            ImportBudget("节目单",ImportPolicy.IMPORT_MS,cancelled={job?.isActive==false}).use{budget->
                val stream=open(url,budget)?:error("无法打开节目单")
                val parsed=stream.use { readXmltvFeed(budget.input(decodedInput(budget.input(it,ImportPolicy.EPG_WIRE_BYTES)),ImportPolicy.EPG_XML_BYTES), budget, ::normalize) }
                if(parsed.programmes.isEmpty())error("节目单为空")
                budget.check()
                if(!aggregate.admit(url,programmeCount(parsed),epgTextWeight(parsed.programmes,parsed.aliases,parsed.displayNames)))throw ImportLimitException("节目单总节目数量超过上限")
                val value=Feed(parsed,System.currentTimeMillis())
                // Reserve first, then publish in memory; cache persistence must not reopen the cap race.
                synchronized(feeds){feeds[url]=value}
                saveCache(url,value,budget)
                budget.check()
            }
            return RefreshOutcome(url,true,"")
        }catch(e:kotlinx.coroutines.CancellationException){throw e}
        catch(e:Exception){return RefreshOutcome(url,false,e.message?:if(url==manual)"单独节目单刷新失败"else"内置节目单刷新失败")}
    }

    private fun open(url: String,budget:ImportBudget): java.io.InputStream? {
        if (url.startsWith("content://") || url.startsWith("/") || url.startsWith("file:")) {
            val u = url.removePrefix("file://")
            return if (u.startsWith("content://")) {
                context.contentResolver.openInputStream(android.net.Uri.parse(u))
            } else {
                (if(url.startsWith("file:"))File(java.net.URI(url))else File(u)).inputStream()
            }
        }
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        val source=sources().firstOrNull{it.epgUrl==url}
        ChannelRepository.parseHeaders(source?.headers.orEmpty()).forEach{(k,v)->conn.setRequestProperty(k,v)}
        conn.setRequestProperty("User-Agent",source?.userAgent?.ifBlank{prefs.defaultUserAgent}?:prefs.defaultUserAgent)
        val referer=source?.referer?.ifBlank{prefs.defaultReferer}?:prefs.defaultReferer
        if(referer.isNotBlank())conn.setRequestProperty("Referer",referer)
        conn.setRequestProperty("Accept-Encoding","identity")
        budget.watch{conn.disconnect()}
        val status = conn.responseCode
        if (status !in 200..299) {
            conn.disconnect()
            throw IllegalStateException("HTTP $status")
        }
        if((conn.getHeaderField("Content-Length")?.toLongOrNull()?:0)>ImportPolicy.EPG_WIRE_BYTES)throw ImportLimitException("节目单下载大小超过上限")
        return object:java.io.FilterInputStream(conn.inputStream){override fun close(){try{super.close()}finally{conn.disconnect()}}}
    }

    /** 归一化：去掉分辨率后缀、空格与常见修饰，便于和 xmltv 显示名匹配 */
    fun normalize(name: String): String {
        var s = name.lowercase(Locale.ROOT)
        s = s.replace(SPACE_PATTERN, "")
        s = s.replace(QUALITY_PATTERN, "")
        s = s.replace("-", "").replace("_", "").replace("·", "").replace(".", "")
        return s
    }

    fun programsFor(channel:Channel):List<Program>{
        val contextKey=revision.toString()+"|"+prefs.epgUrl+"|"+com.nexttv.core.App.get().channels.revision
        if(lookupContext!=contextKey)synchronized(lookup){if(lookupContext!=contextKey){lookup.clear();lookupContext=contextKey}}
        lookup[channel.id]?.takeIf{it.first===channel&&it.second==contextKey}?.let{return it.third}
        val result=resolvePrograms(channel)
        lookup[channel.id]=Triple(channel,contextKey,result)
        return result
    }
    private fun resolvePrograms(channel: Channel): List<Program> = resolveChannelProgrammes(
        channel, prefs.epgUrl, sources().associate { it.id to it.epgUrl },
        findFeed = { feeds[it]?.parsed }, normalizeName = ::normalize,
    )

    /** 当前节目与下一个节目 */
    fun nowNext(channel: Channel): Pair<Program?, Program?> {
        return programmeWindow(programsFor(channel),System.currentTimeMillis())
    }

    fun availableDays(channel:Channel):List<Int> {
        val today=java.time.LocalDate.now()
        return (programsFor(channel).map{java.time.temporal.ChronoUnit.DAYS.between(today,java.time.Instant.ofEpochMilli(it.start).atZone(java.time.ZoneId.systemDefault()).toLocalDate()).toInt()}+0).distinct().sorted()
    }
    fun programsForDay(channel: Channel, dayOffset: Int): List<Program> {
        val zone = ZoneId.systemDefault()
        val date = LocalDate.now(zone).plusDays(dayOffset.toLong())
        val (start, end) = programmeDayBounds(date, zone)
        return programsFor(channel).filter { it.stop > start && it.start < end }
    }

    private fun programmeCount(feed:XmltvFeed):Int=feed.programmes.values.sumOf{it.size}
    private fun saveCache(url:String,feed:Feed,budget:ImportBudget){
        val f=file(url);f.parentFile?.mkdirs();val atomic=android.util.AtomicFile(f);val output=atomic.startWrite()
        try{
            val writer=android.util.JsonWriter(java.io.OutputStreamWriter(budget.output(output,ImportPolicy.EPG_CACHE_BYTES),Charsets.UTF_8))
            writer.beginObject().name("updatedAt").value(feed.time).name("channels").beginObject()
            feed.parsed.programmes.forEach{(id,list)->writer.name(id).beginArray();list.forEach{p->budget.check();writer.beginObject().name("c").value(p.channelKey).name("t").value(p.title).name("d").value(p.desc).name("s").value(p.start).name("e").value(p.stop).endObject()};writer.endArray()}
            writer.endObject()
            for((key,map) in listOf("alias" to feed.parsed.aliases,"names" to feed.parsed.displayNames)){writer.name(key).beginObject();map.forEach{(k,v)->writer.name(k).value(v)};writer.endObject()}
            writer.endObject();writer.flush();budget.check();atomic.finishWrite(output)
        }catch(e:Exception){atomic.failWrite(output);throw e}
    }

    companion object {
        private val SPACE_PATTERN=Regex("""\s+""")
        private val QUALITY_PATTERN=Regex("""1080[pi]|720[pi]|2160[pi]|480[pi]|576[pi]|4k|uhd|高清|超高清|标清|超清|高码""")
        private const val STALE_MS = 6 * 3600_000L
        /** 同时下载/解析的节目单数量，避免 16 个源串行排队。 */
        private const val EPG_CONCURRENCY = 3
    }
}


internal fun programmeDayBounds(date: LocalDate, zone: ZoneId): Pair<Long, Long> =
    date.atStartOfDay(zone).toInstant().toEpochMilli() to
        date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
