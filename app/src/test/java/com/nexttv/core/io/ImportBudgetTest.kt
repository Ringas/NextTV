package com.nexttv.core.io
import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.GZIPOutputStream
class ImportBudgetTest {
 private fun limited(block:()->Unit){try{block();fail("Expected bounded rejection")}catch(_:ImportLimitException){}}
 @Test fun exactBytesPassAndExtraByteFails(){ImportBudget("test").use{b->assertEquals(32,b.input(ByteArrayInputStream(ByteArray(32)),32).readBytes().size)};limited{ImportBudget("test").use{b->b.input(ByteArrayInputStream(ByteArray(33)),32).readBytes()}}}
 @Test fun endlessResponseStopsAfterLimit(){var produced=0;val stream=object:InputStream(){override fun read():Int{produced++;return 65};override fun read(b:ByteArray,off:Int,len:Int):Int{produced+=len;b.fill(65,off,off+len);return len}};limited{ImportBudget("test").use{b->b.input(stream,16384).readBytes()}};assertTrue(produced<=16385)}
 @Test fun gzipExpansionIsBoundedSeparately(){val zipped=ByteArrayOutputStream().also{o->GZIPOutputStream(o).use{it.write(ByteArray(512*1024){65})}}.toByteArray();assertTrue(zipped.size<8192);limited{ImportBudget("test").use{b->b.input(decodedInput(b.input(ByteArrayInputStream(zipped),8192)),16384).readBytes()}}}
 private class Blocking:InputStream(){val latch=CountDownLatch(1);@Volatile var closed=false;override fun read():Int{latch.await();return -1};override fun close(){closed=true;latch.countDown()}}
 @Test(timeout=4000) fun totalDeadlineClosesBlockedRead(){val input=Blocking();val start=System.nanoTime();limited{ImportBudget("test",120).use{b->b.input(input,32).read()}};assertTrue(input.closed);assertTrue((System.nanoTime()-start)/1_000_000<2500)}
 @Test(timeout=4000) fun cancellationClosesBlockedRead(){val input=Blocking();val cancel=AtomicBoolean();Thread{Thread.sleep(100);cancel.set(true)}.apply{isDaemon=true;start()};try{ImportBudget("test",10000,cancel::get).use{b->b.input(input,32).read()};fail("Expected cancellation")}catch(_:java.util.concurrent.CancellationException){};assertTrue(input.closed)}
 @Test fun cachedJsonCannotAccumulateHugeFieldsOrNesting(){limited{BoundedJsonReader(StringReader("{\"x\":\""+"a".repeat(65537)+"\"}")).readText()};limited{BoundedJsonReader(StringReader("[".repeat(33)+"]".repeat(33))).readText()}}
 @Test fun ordinaryJsonRemainsUnchanged(){val value="""{"x":"a\\\"b","list":[1,2,3]}""";assertEquals(value,BoundedJsonReader(StringReader(value)).readText())}
}
