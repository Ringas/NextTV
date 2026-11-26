// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.tv
internal object PreloadPolicy {
 const val MAX_CHANNELS=3
 const val MAX_ROUTES=3
 const val ATTEMPT_TIMEOUT_MS=4_000L
 const val DIRECTION_TIMEOUT_MS=14_000L
 const val SHALLOW_TIMEOUT_MS=8_000L
 const val FAILURE_TTL_MS=20_000L

 data class Target(val index:Int,val slot:Int,val deep:Boolean)

 fun indices(size:Int,current:Int,direction:Int,limit:Int=size-1):Sequence<Int> {
  if(size<2||current !in 0 until size)return emptySequence()
  return (1..limit.coerceAtMost(size-1)).asSequence().map{(current+direction*it+size)%size}
 }

 /**
  * 第一候选深预热；同方向后两个候选只探测是否可用；另一方向最多保留一个浅候选。
  * mode=1/2 仍按设置固定下一台/上一台，mode=3 跟随最近一次换台方向。
  */
 fun plan(size:Int,current:Int,direction:Int,mode:Int):List<Target> {
  if(mode==0||size<2||current !in 0 until size)return emptyList()
  val primaryDirection=when(mode){1->1;2->-1;3->if(direction<0)-1 else 1;else->0}
  if(primaryDirection==0)return emptyList()
  val primary=indices(size,current,primaryDirection,MAX_CHANNELS)
   .mapIndexed{i,index->Target(index,0,i==0)}
   .toList()
  if(primary.isEmpty())return emptyList()
  val opposite=if(mode==3)
   indices(size,current,-primaryDirection,1).map{Target(it,1,false)}.toList()
  else emptyList()
  return (primary+opposite).distinctBy{it.index}
 }

 fun routeAttempts(count:Int,preferred:Int):List<Int> =
  if(count==1)listOf(0,0) else (listOf(preferred)+(0 until count)).distinct().take(MAX_ROUTES)

 fun directions(mode:Int):List<Int> = when(mode){1->listOf(1);2->listOf(-1);3->listOf(1,-1);else->emptyList()}
 fun bufferMillis(heapMb:Int,lowRam:Boolean=false):Long = when{lowRam||heapMb<128->0;heapMb<192->1200;else->2500}
 fun refreshIntervalMillis(heapMb:Int,lowRam:Boolean):Long =
  if(bufferMillis(heapMb,lowRam)==0L)24_000L else 8_000L
 fun deepTtlMillis(heapMb:Int,lowRam:Boolean):Long = refreshIntervalMillis(heapMb,lowRam)
 fun shallowTtlMillis(heapMb:Int,lowRam:Boolean):Long =
  if(bufferMillis(heapMb,lowRam)==0L)60_000L else 30_000L
}
