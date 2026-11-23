package com.nexttv
import android.app.*
import android.os.*
import com.nexttv.core.ui.LogoLoader
import java.net.ServerSocket
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
/** Synthetic PNG is an image loader fixture, never used as playback media. */
class LogoSingleFlightInstrumentation:Instrumentation(){
 override fun onCreate(b:Bundle?){super.onCreate(b);start()}
 override fun onStart(){var result=Activity.RESULT_OK;var report="";val server=ServerSocket(0);val requests=AtomicInteger();val callbacks=AtomicInteger();val cancelled=AtomicInteger();val ready=CountDownLatch(9)
 val bitmap=android.graphics.Bitmap.createBitmap(32,32,android.graphics.Bitmap.Config.ARGB_8888).apply{eraseColor(android.graphics.Color.WHITE)};val bytes=java.io.ByteArrayOutputStream().also{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}.toByteArray();bitmap.recycle()
 val worker=Thread{try{while(!server.isClosed){server.accept().use{socket->socket.soTimeout=3000;val reader=socket.getInputStream().bufferedReader();while(!reader.readLine().isNullOrEmpty()){};requests.incrementAndGet();Thread.sleep(200);socket.getOutputStream().apply{write("HTTP/1.1 200 OK\r\nContent-Type: image/png\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray());write(bytes);flush()}}}}catch(_:Exception){}}
 worker.start()
 val url="http://127.0.0.1:${server.localPort}/logo-${System.nanoTime()}.png"
 try{
 runOnMainSync{val cancels=(0 until 12).map{i->LogoLoader.fetch(targetContext,url){image->check(image!=null);if(i<3)cancelled.incrementAndGet()else{callbacks.incrementAndGet();ready.countDown()}}};cancels.take(3).forEach{it()}}
 check(ready.await(15,TimeUnit.SECONDS)){"Missing shared callbacks ${callbacks.get()}"};SystemClock.sleep(300);check(requests.get()==1){"Duplicate network requests ${requests.get()}"};check(cancelled.get()==0)
 val hit=CountDownLatch(1);runOnMainSync{LogoLoader.fetch(targetContext,url){check(it!=null);hit.countDown()}};check(hit.await(2,TimeUnit.SECONDS));check(requests.get()==1)
 val diskHit=CountDownLatch(1)
 runOnMainSync{LogoLoader.trimMemory();LogoLoader.fetch(targetContext,url){check(it!=null);diskHit.countDown()}}
 check(diskHit.await(5,TimeUnit.SECONDS)){"Disk cache did not return the saved logo"}
 check(requests.get()==1){"Disk cache miss triggered another network request"}
 report="PASS 12 simultaneous logo consumers share one HTTP request; 3 cancelled consumers receive no callback; 9 remaining consumers succeed\nPASS repeated logo request is served from memory without network\nPASS logo is restored from disk after clearing the memory cache\n"
 }catch(t:Throwable){result=Activity.RESULT_CANCELED;report=t.stackTraceToString()}finally{server.close();worker.join(2000)}
 finish(result,Bundle().apply{putString("stream",report)})
 }
}
