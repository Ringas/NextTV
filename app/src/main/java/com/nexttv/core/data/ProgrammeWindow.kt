package com.nexttv.core.data

/** Strict interval matching without allocating filtered lists on the scrolling UI path. */
internal fun programmeWindow(programmes:List<Program>,now:Long):Pair<Program?,Program?> {
 var current:Program?=null;var next:Program?=null
 for(p in programmes){
  if(p.stop<=p.start)continue
  if(p.start<=now&&now<p.stop&&(current==null||p.start>current.start))current=p
  if(p.start>now&&(next==null||p.start<next.start))next=p
 }
 return current to next
}

/** Shared by channel rows and the player; malformed or unknown durations never report full progress. */
internal fun playbackFraction(position:Long,duration:Long):Float=if(duration<=0)0f else (position.toDouble()/duration).toFloat().coerceIn(0f,1f)
internal fun programmeProgress(program:Program?,now:Long):Float=if(program==null)0f else playbackFraction(now-program.start,program.stop-program.start)
