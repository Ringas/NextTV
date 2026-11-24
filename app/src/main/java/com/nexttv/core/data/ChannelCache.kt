// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.data

import android.content.Context
import com.nexttv.core.io.*
import java.io.File

internal class ChannelCache(private val context: Context) {
    private val channelsFile: File get() = File(context.filesDir, "channels.json")

    fun loadChannels(): Pair<List<Channel>, Boolean>? {
        val f = channelsFile
        if (!f.exists()&&!File(f.path+".bak").exists()) return null
        val list=ArrayList<Channel>()
        var legacy=false
        ImportBudget("频道缓存",10000).use{budget->
            android.util.JsonReader(BoundedJsonReader(budget.input(android.util.AtomicFile(f).openRead(),ImportPolicy.CHANNEL_CACHE_BYTES).reader(),ImportPolicy.CHANNEL_CACHE_RECORDS*3)).use{reader->
                reader.beginArray()
                while(reader.hasNext()){
                    budget.check();if(list.size>=ImportPolicy.CHANNEL_CACHE_RECORDS)throw ImportLimitException("频道缓存数量超过上限")
                    val fields=hashMapOf<String,String>();val headers=hashMapOf<String,String>();var number=0;var days:Int?=null;var hasCatchup=false
                    reader.beginObject();while(reader.hasNext()){
                        val key=reader.nextName()
                        when(key){
                            "number"->number=reader.nextInt()
                            "catchupDays"->if(reader.peek()==android.util.JsonToken.NULL)reader.nextNull()else days=reader.nextInt()
                            "headers"->{reader.beginObject();while(reader.hasNext()){val k=reader.nextName();headers[k]=reader.nextString();if(headers.size>100)throw ImportLimitException("频道请求头过多")};reader.endObject()}
                            "id","name","tvgId","logo","group","url","sourceId","catchup","catchupSource"->{if(key=="catchup")hasCatchup=true;fields[key]=if(reader.peek()==android.util.JsonToken.NULL){reader.nextNull();""}else reader.nextString()}
                            else->reader.skipValue()
                        }
                    };reader.endObject()
                    if(list.isEmpty())legacy=!hasCatchup
                    fun text(key:String)=fields[key].orEmpty()
                    require(text("id").isNotEmpty()&&text("url").isNotEmpty())
                    list.add(Channel(id=text("id"),name=text("name"),tvgId=text("tvgId"),logo=text("logo"),group=text("group"),url=text("url"),sourceId=text("sourceId"),headers=headers,number=number,catchup=text("catchup"),catchupSource=text("catchupSource"),catchupDays=days))
                };reader.endArray()
            }
        }
        return list to legacy
    }

    fun saveChannels(records: List<Channel>) {
        ImportBudget("频道缓存",10000).use{budget->
            if(records.size>ImportPolicy.CHANNEL_CACHE_RECORDS)throw ImportLimitException("频道缓存数量超过上限")
            val atomic=android.util.AtomicFile(channelsFile);val output=atomic.startWrite()
            try{
                val writer=android.util.JsonWriter(java.io.OutputStreamWriter(budget.output(output,ImportPolicy.CHANNEL_CACHE_BYTES),Charsets.UTF_8));writer.beginArray()
                records.forEach{c->budget.check();writer.beginObject().name("id").value(c.id).name("name").value(c.name).name("tvgId").value(c.tvgId).name("logo").value(c.logo).name("group").value(c.group).name("url").value(c.url).name("sourceId").value(c.sourceId).name("number").value(c.number.toLong()).name("catchup").value(c.catchup).name("catchupSource").value(c.catchupSource)
                    if(c.catchupDays!=null)writer.name("catchupDays").value(c.catchupDays.toLong())
                    writer.name("headers").beginObject();c.headers.forEach{(k,v)->writer.name(k).value(v)};writer.endObject().endObject()}
                writer.endArray();writer.flush();budget.check();output.fd.sync();atomic.finishWrite(output)
                check(!File(channelsFile.path + ".new").exists()) { "频道缓存未提交" }
            }catch(e:Exception){atomic.failWrite(output);throw e}
        }
    }

    fun writeSourceCache(id: String, input: java.io.InputStream): Boolean =
        runCatching {
            ImportBudget("播放源缓存",10000).use { budget ->
                val cache=android.util.AtomicFile(File(context.filesDir,"pl_$id.m3u"))
                val stream=cache.startWrite()
                try {
                    input.copyTo(budget.output(stream,ImportPolicy.PLAYLIST_BYTES));budget.check();stream.fd.sync();cache.finishWrite(stream)
                } catch(t:Throwable){cache.failWrite(stream);throw t}
                CacheStore.trimSourceCaches(context, id)
            }
        }.isSuccess

    fun deleteSourceCache(id: String) {
        val base = File(context.filesDir, "pl_$id.m3u")
        base.delete()
        File(base.path + ".bak").delete()
        File(base.path + ".new").delete()
    }

    fun readSourceCache(id: String, base: String): List<Channel>? {
        val f = File(context.filesDir, "pl_$id.m3u")
        if (!f.exists()&&!File(f.path+".bak").exists()) return null
        return runCatching {
            ImportBudget("播放源缓存", 10000).use { budget ->
                budget.input(android.util.AtomicFile(f).openRead(), ImportPolicy.PLAYLIST_BYTES)
                    .bufferedReader().use { M3uParser.parse(it, id, base, budget::check).channels }
            }
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

}
