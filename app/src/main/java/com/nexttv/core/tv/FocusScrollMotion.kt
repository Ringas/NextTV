package com.nexttv.core.tv
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sign

internal data class FocusScrollAdvance(val delta:Float,val velocity:Float)
/** Critically damped motion with retained velocity, bounded speed and no row-sized frame jumps. */
internal fun advanceFocusScroll(distance:Float,velocity:Float,elapsedSeconds:Float,viewport:Float,rowHeight:Float):FocusScrollAdvance =
 advanceScroll(distance,velocity,elapsedSeconds,viewport,rowHeight,45f,9f,.2f,.4f)

/** Returning crosses many rows: bound each frame instead of catching up a wall-clock tween. */
internal fun advanceReturnScroll(distance:Float,velocity:Float,elapsedSeconds:Float,viewport:Float,rowHeight:Float):FocusScrollAdvance =
 advanceScroll(distance,velocity,elapsedSeconds,viewport,rowHeight,100f,18f,.4f,.8f)

private fun advanceScroll(distance:Float,velocity:Float,elapsedSeconds:Float,viewport:Float,rowHeight:Float,omega:Float,speedScale:Float,viewportFraction:Float,rowFraction:Float):FocusScrollAdvance {
 if(!distance.isFinite()||abs(distance)<=1f)return FocusScrollAdvance(0f,0f)
 val dt=if(elapsedSeconds.isFinite())elapsedSeconds.coerceIn(.001f,.05f)else .016f
 val speed=viewport.coerceAtLeast(1f)*speedScale
 val prior=if(velocity.isFinite()&&sign(velocity)==sign(distance))velocity.coerceIn(-speed,speed)else 0f
 val decay=exp(-omega*dt)
 val rate=omega*distance-prior
 var delta=distance-(distance+rate*dt)*decay
 var nextVelocity=(prior+rate*omega*dt)*decay
 val limit=minOf(viewport.coerceAtLeast(1f)*viewportFraction,rowHeight.coerceAtLeast(1f)*rowFraction,speed*dt)
 if(abs(delta)>limit){delta=delta.coerceIn(-limit,limit);nextVelocity=delta/dt}
 if(sign(delta)==sign(distance)&&abs(delta)>=abs(distance))return FocusScrollAdvance(distance,0f)
 return FocusScrollAdvance(delta,nextVelocity.coerceIn(-speed,speed))
}
