// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.tv

/** Shared loading/recovery allowance; normal playback and explicit pause consume no budget. */
internal class RecoveryBudget(private val clock:()->Long){
 private var channel=""
 private var started:Long?=null
 private var spent=0L
 fun reset(){channel="";started=null;spent=0}
 fun enter(id:String){if(channel!=id){reset();channel=id};if(started==null)started=clock()}
 fun pause(){started?.let{spent+=(clock()-it).coerceAtLeast(0)};started=null}
 fun remaining(limit:Long):Long=(limit-spent-(started?.let{(clock()-it).coerceAtLeast(0)}?:0)).coerceAtLeast(0)
}
