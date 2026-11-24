package com.nexttv.core.io

import java.io.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.CancellationException
import java.util.zip.GZIPInputStream

class ImportLimitException(message:String):IOException(message)
object ImportPolicy {
 const val PLAYLIST_BYTES=8L*1024*1024
 const val CHANNELS=20000
 const val CHANNEL_CACHE_BYTES=24L*1024*1024
 const val CHANNEL_CACHE_RECORDS=40000
 const val LINE_CHARS=16384
 const val EPG_WIRE_BYTES=8L*1024*1024
 const val EPG_XML_BYTES=16L*1024*1024
 const val EPG_CACHE_BYTES=24L*1024*1024
 /** All EPG source files together must stay below this; 16 x 24 MB is not a safe budget. */
 const val EPG_CACHE_TOTAL_BYTES=96L*1024*1024
 /** Per-source playlist backups can grow to 8 MB each; cap their aggregate. */
 const val PLAYLIST_CACHE_TOTAL_BYTES=48L*1024*1024
 const val PROGRAMMES=50000
 const val TOTAL_PROGRAMMES=75000
 const val EPG_CHANNELS=5000
 const val TEXT_CHARS=8*1024*1024
 const val FIELD_CHARS=8192
 const val LOGO_BYTES=4L*1024*1024
 const val LOGO_PIXELS=16L*1024*1024
 const val IMPORT_MS=30000L
}
/** Shared monotonic deadline for download, decompression and parsing. Closes blocked network IO. */
class ImportBudget(private val label:String,private val timeoutMs:Long=ImportPolicy.IMPORT_MS,private val cancelled:()->Boolean={false}):Closeable {
 private val start=System.nanoTime()
 private val closers=mutableListOf<()->Unit>()
 @Volatile private var closed=false
 @Volatile private var aborted=false
 private fun expired()=(System.nanoTime()-start)/1_000_000>=timeoutMs
 private val watchdog=timer.scheduleWithFixedDelay({if(!closed&&!aborted&&(expired()||cancelled())){aborted=true;closeStreams()}},100,100,TimeUnit.MILLISECONDS)
 fun check(){if(cancelled()||Thread.currentThread().isInterrupted)throw CancellationException("$label 已取消");if(expired())throw ImportLimitException("$label 超过处理时限")}
 fun watch(close:()->Unit){synchronized(closers){closers.add(close)};check()}
 private fun closeStreams(){val all=synchronized(closers){closers.toList()};all.forEach{runCatching{it()}}}
 fun input(input:InputStream,limit:Long):InputStream {watch{input.close()};return object:FilterInputStream(input){
  var count=0L
  private fun added(n:Int):Int{if(n>0){count+=n;if(count>limit)throw ImportLimitException("$label 内容超过大小上限")};check();return n}
  override fun read():Int{check();val n=try{`in`.read()}catch(e:IOException){check();throw e};added(if(n<0)0 else 1);return n}
  override fun read(b:ByteArray,off:Int,len:Int):Int{if(len==0)return 0;check();val n=try{`in`.read(b,off,minOf(len.toLong(),limit-count+1).coerceAtLeast(1).toInt())}catch(e:IOException){check();throw e};return added(n)}
  override fun skip(n:Long):Long{val buffer=ByteArray(4096);var left=n;while(left>0){val read=read(buffer,0,minOf(left,buffer.size.toLong()).toInt());if(read<0)break;left-=read};return n-left}
 }}
 fun output(output:OutputStream,limit:Long):OutputStream=object:FilterOutputStream(output){var count=0L
  override fun write(b:Int){check();if(++count>limit)throw ImportLimitException("$label 缓存超过大小上限");out.write(b)}
  override fun write(b:ByteArray,off:Int,len:Int){check();count+=len;if(count>limit)throw ImportLimitException("$label 缓存超过大小上限");out.write(b,off,len)}
 }
 fun text(input:InputStream,limit:Long):String=this.input(input,limit).bufferedReader(Charsets.UTF_8).use{it.readText()}
 override fun close(){closed=true;watchdog.cancel(false);closeStreams()}
 companion object {private val timer=Executors.newScheduledThreadPool(2){r->Thread(r,"NextTV-import-deadline").apply{isDaemon=true}}}
}
fun decodedInput(raw:InputStream):InputStream {val b=if(raw.markSupported())raw else BufferedInputStream(raw);b.mark(2);val x=b.read();val y=b.read();b.reset();return if(x==0x1f&&y==0x8b)GZIPInputStream(b)else b}

/** Validate lexical bounds before JSON parsers can accumulate a huge string/tree from old cache. */
class BoundedJsonReader(reader:Reader,private val maxNodes:Int=150000):FilterReader(reader){
 private var quoted=false;private var escaped=false;private var chars=0;private var depth=0;private var nodes=0;private var separators=0
 private fun accept(c:Char){
  if(quoted){chars++;if(chars>65536)throw ImportLimitException("缓存字段过长");if(escaped)escaped=false else if(c=='\\')escaped=true else if(c=='"')quoted=false}
  else when(c){'"'->{quoted=true;chars=0};'{','['->{depth++;nodes++;if(depth>32||nodes>maxNodes)throw ImportLimitException("缓存结构超过上限")};'}',']'->depth--;','->{if(++separators>maxNodes*20)throw ImportLimitException("缓存条目超过上限")}}
 }
 override fun read():Int{val c=super.read();if(c>=0)accept(c.toChar());return c}
 override fun read(b:CharArray,off:Int,len:Int):Int{val n=super.read(b,off,len);for(i in off until off+n.coerceAtLeast(0))accept(b[i]);return n}
}
