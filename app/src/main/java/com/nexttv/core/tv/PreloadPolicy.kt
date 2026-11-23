// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.tv
internal object PreloadPolicy {
 const val MAX_CHANNELS=3
 const val MAX_ROUTES=3
 const val ATTEMPT_TIMEOUT_MS=4_000L
 const val DIRECTION_TIMEOUT_MS=14_000L
 const val FAILURE_TTL_MS=20_000L

 fun indices(size:Int,current:Int,direction:Int,limit:Int=size-1):Sequence<Int> {
  if(size<2||current !in 0 until size)return emptySequence()
  return (1..limit.coerceAtMost(size-1)).asSequence().map{(current+direction*it+size)%size}
 }

 fun routeAttempts(count:Int,preferred:Int):List<Int> =
  if(count==1)listOf(0,0) else (listOf(preferred)+(0 until count)).distinct().take(MAX_ROUTES)

 fun directions(mode:Int):List<Int> = when(mode){1->listOf(1);2->listOf(-1);3->listOf(1,-1);else->emptyList()}
 fun bufferMillis(heapMb:Int,lowRam:Boolean=false):Long = when{lowRam||heapMb<128->0;heapMb<192->1200;else->2500}
 fun refreshIntervalMillis(heapMb:Int,lowRam:Boolean):Long =
  if(bufferMillis(heapMb,lowRam)==0L)24_000L else 8_000L
}
