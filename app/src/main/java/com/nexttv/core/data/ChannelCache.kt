// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.data

import android.content.Context
import com.nexttv.BuildConfig
import com.nexttv.core.io.*
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.PushbackInputStream

internal class ChannelCache(private val context: Context) {
    private val channelsFile: File get() = File(context.filesDir, "channels.json")
    private val secrets = SecretStore(context)

    /**
     * Release builds encrypt cache files at rest. Debug keeps the historical plain
     * format so the device instrumentation suite can inspect fixtures directly.
     * Reads always accept both formats, which also migrates existing installs.
     */
    private fun shouldEncrypt(): Boolean = !BuildConfig.DEBUG

    private data class Opened(val stream: InputStream, val encrypted: Boolean)

    private fun openRead(file: File): Opened {
        val raw = android.util.AtomicFile(file).openRead()
        val probe = PushbackInputStream(raw, SecretStore.FILE_MAGIC.size + SecretStore.IV_BYTES)
        val magic = ByteArray(SecretStore.FILE_MAGIC.size)
        val count = probe.read(magic)
        if (count == magic.size && magic.contentEquals(SecretStore.FILE_MAGIC)) {
            val iv = ByteArray(SecretStore.IV_BYTES)
            var offset = 0
            while (offset < iv.size) {
                val read = probe.read(iv, offset, iv.size - offset)
                if (read <= 0) throw IOException("缓存加密头不完整")
                offset += read
            }
            return Opened(secrets.decryptStream(probe, iv) ?: throw IOException("缓存无法解密"), true)
        }
        if (count > 0) probe.unread(magic, 0, count)
        return Opened(probe, false)
    }

    internal data class CacheLoad(val channels: List<Channel>, val legacyFormat: Boolean, val plaintextAtRest: Boolean)

    fun loadChannels(): CacheLoad? {
        val f = channelsFile
        if (!f.exists()&&!File(f.path+".bak").exists()) return null
        val list=ArrayList<Channel>()
        var legacy=false
        val opened=openRead(f)
        ImportBudget("频道缓存",10000).use{budget->
            android.util.JsonReader(BoundedJsonReader(budget.input(opened.stream,ImportPolicy.CHANNEL_CACHE_BYTES).reader(),ImportPolicy.CHANNEL_CACHE_RECORDS*3)).use{reader->
                reader.beginArray()
                while(reader.hasNext()){
                    budget.check();if(list.size>=ImportPolicy.CHANNEL_CACHE_RECORDS)throw ImportLimitException("频道缓存数量超过上限")
                    val fields=hashMapOf<String,String>();val headers=hashMapOf<String,String>();var number=0;var days:Int?=null;var hasCatchup=false
                    reader.beginObject();while(reader.hasNext()){
                        val key=reader.nextName()
                        when(key){
                            "number"->number=reader.nextInt()
                            "catchupDays"->if(reader.peek()==android.util.JsonToken.NULL)reader.nextNull()else days=reader.nextInt()
                            "headers"->{reader.beginObject();while(reader.hasNext()){val k=reader.nextName();headers[k]=reader.nextString();if(headers.size>M3uParser.MAX_HEADERS)throw ImportLimitException("频道请求头过多")};reader.endObject()}
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
        return CacheLoad(list, legacy, !opened.encrypted)
    }

    fun saveChannels(records: List<Channel>) {
        ImportBudget("频道缓存",10000).use{budget->
            if(records.size>ImportPolicy.CHANNEL_CACHE_RECORDS)throw ImportLimitException("频道缓存数量超过上限")
            val atomic=android.util.AtomicFile(channelsFile);val output=atomic.startWrite()
            try{
                val base=budget.output(output,ImportPolicy.CHANNEL_CACHE_BYTES)
                val encrypted=shouldEncrypt()
                val sink=if(encrypted) secrets.encryptStream(base) ?: throw IOException("缓存无法加密") else base
                val writer=android.util.JsonWriter(java.io.OutputStreamWriter(sink,Charsets.UTF_8));writer.beginArray()
                records.forEach{c->
                    budget.check()
                    if(c.headers.size>M3uParser.MAX_HEADERS)throw ImportLimitException("频道请求头过多")
                    writer.beginObject().name("id").value(c.id).name("name").value(c.name).name("tvgId").value(c.tvgId).name("logo").value(c.logo).name("group").value(c.group).name("url").value(c.url).name("sourceId").value(c.sourceId).name("number").value(c.number.toLong()).name("catchup").value(c.catchup).name("catchupSource").value(c.catchupSource)
                    if(c.catchupDays!=null)writer.name("catchupDays").value(c.catchupDays.toLong())
                    writer.name("headers").beginObject();c.headers.forEach{(k,v)->writer.name(k).value(v)};writer.endObject().endObject()}
                writer.endArray();writer.flush();if(encrypted)sink.close();budget.check();output.fd.sync();atomic.finishWrite(output)
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
                    val base=budget.output(stream,ImportPolicy.PLAYLIST_BYTES)
                    val encrypted=shouldEncrypt()
                    val sink=if(encrypted) secrets.encryptStream(base) ?: throw IOException("播放源缓存无法加密") else base
                    input.copyTo(sink)
                    if(encrypted)sink.close()
                    budget.check();stream.fd.sync();cache.finishWrite(stream)
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
                budget.input(openRead(f).stream, ImportPolicy.PLAYLIST_BYTES)
                    .bufferedReader().use { M3uParser.parse(it, id, base, budget::check).channels }
            }
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

}
