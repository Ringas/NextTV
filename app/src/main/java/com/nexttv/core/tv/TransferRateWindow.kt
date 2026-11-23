// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.tv

/** Actual received bytes over up to three measured intervals, never a stream-bitrate estimate. */
internal class TransferRateWindow(private val capacity:Int=3){
 private data class Sample(val bytes:Long,val millis:Long)
 private val samples=java.util.ArrayDeque<Sample>()
 fun clear(){samples.clear()}
 fun add(bytes:Long,millis:Long):Long{
  if(millis<=0)return 0
  samples.addLast(Sample(bytes.coerceAtLeast(0),millis))
  while(samples.size>capacity.coerceAtLeast(1))samples.removeFirst()
  return (samples.sumOf{it.bytes}.toDouble()*1000/samples.sumOf{it.millis}).toLong()
 }
}
