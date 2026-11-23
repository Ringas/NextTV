// SPDX-License-Identifier: GPL-3.0-only
// Next TV — Compose for TV implementation.
package com.nexttv.core.ui

import com.nexttv.core.io.*

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 台标 / 频道预览图加载器：内存 + 磁盘两级缓存，最多 3 个并发下载。
 * 没有引入第三方图片库，按缩略图尺寸做采样解码即可满足 4 列网格。
 */
object LogoLoader {

    private val diskDecodes=java.util.concurrent.atomic.AtomicInteger()
    private val downloads=java.util.concurrent.atomic.AtomicInteger()
    fun cacheStats():String="hits=${cache.hitCount()} misses=${cache.missCount()} evictions=${cache.evictionCount()} bytes=${cache.size()} max=${cache.maxSize()} diskDecodes=${diskDecodes.get()} downloads=${downloads.get()} queued=${executor.queue.size} active=${executor.activeCount}"
    private val dirLock=Any()
    private const val DISK_DIR = "logos"
    private const val DISK_LIMIT_BYTES = 64L * 1024 * 1024
    private const val DISK_TARGET_BYTES = 56L * 1024 * 1024
    private var diskBytes: Long? = null

    private val cache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val executor = java.util.concurrent.ThreadPoolExecutor(3,3,0,java.util.concurrent.TimeUnit.MILLISECONDS,java.util.concurrent.ArrayBlockingQueue<Runnable>(96))
    private fun enqueue(rejected:()->Unit,work:()->Unit){try{executor.execute(work)}catch(_:java.util.concurrent.RejectedExecutionException){main.post{rejected()}}}
    private val main = Handler(Looper.getMainLooper())

    @Volatile
    private var cacheDir: File? = null

    fun cached(url:String):Bitmap?=cache.get(url.trim())

    /** Compose bridge; preserves the source cache and executor, never mounts a View. */
    private class Flight {
        val clients=linkedMapOf<Any,(Bitmap?)->Unit>()
        @Volatile var abandoned=false
    }
    private val flights=hashMapOf<String,Flight>()
    fun trimMemory(){cache.evictAll()}
    fun fetch(context: Context,url:String,result:(Bitmap?)->Unit):()->Unit {
        val target=url.trim()
        if(target.isEmpty()){result(null);return {}}
        cache.get(target)?.let{result(it);return {}}
        val dir=cacheDir?:File(context.applicationContext.cacheDir,DISK_DIR).also{cacheDir=it}
        val token=Any();val disposed=java.util.concurrent.atomic.AtomicBoolean(false);var start=false
        val flight=synchronized(flights){flights.getOrPut(target){start=true;Flight()}.also{it.clients[token]={bitmap->if(!disposed.get())result(bitmap)}}}
        fun complete(bitmap:Bitmap?){
            val callbacks=synchronized(flights){if(flights[target]===flight)flights.remove(target);flight.clients.values.toList().also{flight.clients.clear()}}
            main.post{callbacks.forEach{it(bitmap)}}
        }
        if(start)enqueue({complete(null)}){
            if(flight.abandoned){complete(null);return@enqueue}
            val bitmap=cache.get(target)?:readDisk(dir,target)?:download(target){flight.abandoned}?.also{saveDisk(dir,target,it)}
            if(bitmap!=null&&!flight.abandoned)cache.put(target,bitmap)
            complete(bitmap)
        }
        return {disposed.set(true);synchronized(flights){flight.clients.remove(token);if(flight.clients.isEmpty()){flight.abandoned=true;if(flights[target]===flight)flights.remove(target)}}}
    }
    private fun diskFile(dir:File,url:String)=File(dir,logoDiskKey(url)+".png")
    private fun readDisk(dir: File, url: String): Bitmap? = runCatching {
        val file = diskFile(dir, url)
        if (file.exists()&&file.length()<=ImportPolicy.LOGO_BYTES) {file.setLastModified(System.currentTimeMillis());diskDecodes.incrementAndGet();ImportBudget("台标缓存",5000).use{budget->decode(budget.input(file.inputStream(),ImportPolicy.LOGO_BYTES).use{it.readBytes()})}} else null
    }.getOrNull()

    private fun saveDisk(dir: File, url: String, bitmap: Bitmap) {
        runCatching {
            synchronized(dirLock) {
                if (diskBytes == null) {
                    dir.mkdirs()
                    val files = dir.listFiles() ?: return
                    for (file in files) {
                        if (file.name.endsWith(".stage")) file.delete()
                    }
                    diskBytes = files.filter { it.extension == "png" }.sumOf { it.length() }
                }
            }

            val staged = File.createTempFile("logo-", ".stage", dir)
            try {
                staged.outputStream().use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                }
                val size = staged.length()
                if (size > ImportPolicy.LOGO_BYTES) return

                synchronized(dirLock) {
                    val destination = diskFile(dir, url)
                    val previousSize = destination.length()
                    if (staged.renameTo(destination)) {
                        diskBytes = checkNotNull(diskBytes) - previousSize + size
                        if (checkNotNull(diskBytes) > DISK_LIMIT_BYTES) trimDiskCache(dir)
                    }
                }
            } finally {
                staged.delete()
            }
        }
    }

    // Scan only when over budget. Leave headroom so nearby writes share one eviction pass.
    private fun trimDiskCache(dir: File) {
        val files = dir.listFiles()?.filter { it.extension == "png" } ?: return
        var total = files.sumOf { it.length() }
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= DISK_TARGET_BYTES) break
            val size = file.length()
            if (file.delete()) total -= size
        }
        diskBytes = total
    }

    private fun download(url: String,cancelled:()->Boolean={false}): Bitmap? = runCatching {ImportBudget("台标",15000,cancelled).use{budget->
        downloads.incrementAndGet()
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 10000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 9) FreeTV/1.0")
        }
        try {
            budget.watch{conn.disconnect()}
            if(conn.responseCode !in 200..299)throw java.io.IOException("台标 HTTP 错误")
            if((conn.getHeaderField("Content-Length")?.toLongOrNull()?:0)>ImportPolicy.LOGO_BYTES)throw ImportLimitException("台标文件过大")
            budget.input(conn.inputStream,ImportPolicy.LOGO_BYTES).use { decode(it.readBytes()) }
        } finally {
            conn.disconnect()
        }
    }}.getOrNull()

    private fun decode(bytes: ByteArray): Bitmap? {
        if (bytes.isEmpty()||bytes.size>ImportPolicy.LOGO_BYTES) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val sample=logoSampleSize(bounds.outWidth,bounds.outHeight)?:return null
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) }.getOrNull()
    }
}
