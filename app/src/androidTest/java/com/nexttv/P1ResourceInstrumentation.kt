package com.nexttv
import android.app.*
import android.content.*
import android.os.*
import com.nexttv.core.App
import com.nexttv.core.data.*
import com.nexttv.core.ui.LogoLoader
import kotlinx.coroutines.*
import java.io.*
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream

class P1ResourceInstrumentation:Instrumentation(){
 override fun onCreate(b:Bundle?){super.onCreate(b);start()}
 private fun pass(s:String)=sendStatus(0,Bundle().apply{putString("stream","PASS $s\n")})
 override fun onStart(){var status=Activity.RESULT_OK;var report="";val bootstrap=startActivitySync(Intent().setClassName("com.nexttv","com.nexttv.core.tv.TvActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();runOnMainSync{bootstrap.finish()};waitForIdleSync();val app=App.get();val old=app.channels
 val dir=File(targetContext.cacheDir,"p1-res-${System.nanoTime()}").apply{mkdirs()};val ctx=object:ContextWrapper(targetContext){override fun getApplicationContext():Context=this;override fun getFilesDir()=dir;override fun getSharedPreferences(n:String,m:Int)=baseContext.getSharedPreferences(dir.name+n,m)}
 val prefs=Prefs(ctx);val repo=ChannelRepository(ctx,prefs);val epg=EpgRepository(ctx,prefs);val field=app.javaClass.getDeclaredField("channels").apply{isAccessible=true};runOnMainSync{field.set(app,repo)}
 val listener=ServerSocket(0);var mode="valid";var body=ByteArray(0);var accepted=CountDownLatch(1);val clients=java.util.concurrent.CopyOnWriteArrayList<java.net.Socket>()
 Thread{while(!listener.isClosed)try{val socket=listener.accept();clients.add(socket);Thread{try{socket.use{s->val input=s.getInputStream().bufferedReader();while(input.readLine()?.isNotEmpty()==true){};val out=s.getOutputStream();val current=mode
 if(current=="declared"){out.write("HTTP/1.1 200 OK\r\nContent-Length: 99999999\r\nConnection: close\r\n\r\n".toByteArray())}
 else if(current=="endless"){out.write("HTTP/1.1 200 OK\r\nConnection: close\r\n\r\n".toByteArray());val bytes=ByteArray(8192){32};while(true)out.write(bytes)}
 else if(current=="stall"){out.write("HTTP/1.1 200 OK\r\nConnection: close\r\n\r\n".toByteArray());out.flush();accepted.countDown();s.getInputStream().read()}
 else{val data=body;out.write("HTTP/1.1 200 OK\r\nContent-Length: ${data.size}\r\nConnection: close\r\n\r\n".toByteArray());out.write(data)}
 }}catch(_:Exception){}finally{clients.remove(socket)}}.apply{isDaemon=true;start()}}catch(_:Exception){}}.apply{isDaemon=true;start()}
 val url="http://127.0.0.1:${listener.localPort}"
 try{
  val real=old.channels.first();body="#EXTM3U\n#EXTINF:-1 tvg-id=\"stress\",P1资源测试\n${real.url}\n".toByteArray();repo.upsertSource(Source("stress","P1资源测试",url));check(runBlocking{repo.refreshAll()}.ok==1);val ids=repo.channels.map{it.id};val cache=File(dir,"channels.json").readBytes()
  for(m in listOf("declared","endless")){mode=m;val start=SystemClock.uptimeMillis();check(runBlocking{repo.refreshAll()}.failed==1);check(SystemClock.uptimeMillis()-start<15000);check(repo.channels.map{it.id}==ids);check(File(dir,"channels.json").readBytes().contentEquals(cache));pass("F06 HTTP $m subscription bounded and previous cache retained")}
  mode="stall";accepted=CountDownLatch(1);runBlocking{val job=launch(Dispatchers.IO){repo.refreshAll()};check(accepted.await(5,TimeUnit.SECONDS));val start=SystemClock.uptimeMillis();job.cancel();withTimeout(2500){job.join()};check(SystemClock.uptimeMillis()-start<2500)};check(repo.channels.map{it.id}==ids);pass("F06 cancellation closes blocked subscription IO within 2.5 seconds")
  // Compressed expansion: comments avoid text-field limits so the XML byte budget is exercised.
  val f=java.text.SimpleDateFormat("yyyyMMddHHmmss Z",java.util.Locale.US);val now=System.currentTimeMillis();val programme="<programme channel=\"stress\" start=\"${f.format(java.util.Date(now-60000))}\" stop=\"${f.format(java.util.Date(now+60000))}\"><title>旧节目</title></programme>"
  mode="valid";body=("<tv>"+programme+"</tv>").toByteArray();prefs.epgUrl=url+"/guide";check(runBlocking{epg.refresh(true)});val c=repo.channels.first();val epgFile=File(dir,"epg_feeds").listFiles()!!.first();val previous=epgFile.readBytes()
  val compressed=ByteArrayOutputStream();GZIPOutputStream(compressed).use{g->g.write("<tv>".toByteArray());val comment=("<!--"+"a".repeat(4000)+"-->").toByteArray();repeat(4400){g.write(comment)};g.write((programme+"</tv>").toByteArray())};body=compressed.toByteArray();check(!runBlocking{epg.refresh(true)});check(epg.lastError.contains("大小上限")){epg.lastError};check(epgFile.readBytes().contentEquals(previous));pass("F06 small gzip expanding beyond 16 MiB rejected before replacing valid guide")
  body=buildString{append("<tv>");repeat(50001){append("<programme/>")};append("</tv>")}.toByteArray();check(!runBlocking{epg.refresh(true)});check(epg.lastError.contains("数量")){epg.lastError};check(epg.nowNext(c).first?.title=="旧节目");pass("F06 50,001 programmes rejected and prior guide remains current")
  fun logo():android.graphics.Bitmap?{val done=CountDownLatch(1);var bitmap:android.graphics.Bitmap?=null;runOnMainSync{LogoLoader.fetch(ctx,url+"/logo-"+System.nanoTime()){bitmap=it;done.countDown()}};check(done.await(20,TimeUnit.SECONDS));return bitmap}
  mode="declared";check(logo()==null);mode="endless";check(logo()==null);pass("F06 oversized declared and endless HTTP logos safely return fallback")
  mode="valid";val tall=android.graphics.Bitmap.createBitmap(2,4096,android.graphics.Bitmap.Config.ARGB_8888);val png=ByteArrayOutputStream();tall.compress(android.graphics.Bitmap.CompressFormat.PNG,100,png);tall.recycle();body=png.toByteArray();val decoded=logo()?:error("Tall image failed");check(decoded.height<=420&&decoded.width<=420);pass("F06 tall logo decoded to ${decoded.width}x${decoded.height}, longest edge ≤420 px")
  report="Resource stress complete; no OOM/crash; fixtures do not substitute real playback.\n"
 }catch(t:Throwable){status=Activity.RESULT_CANCELED;report=t.stackTraceToString()}
 finally{listener.close();clients.forEach{runCatching{it.close()}};runOnMainSync{field.set(app,old)};ctx.getSharedPreferences("nexttv",0).edit().clear().commit();dir.deleteRecursively()}
 finish(status,Bundle().apply{putString("stream",report)})
 }
}
