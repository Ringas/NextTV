package com.nexttv.core.tv

import kotlin.math.abs

/** Content dragging: left reveals the right page; up advances to the next row. One action per drag. */
internal enum class SwipeDirection{UP,DOWN,LEFT,RIGHT}
internal fun swipeDirection(dx:Float,dy:Float,threshold:Float):SwipeDirection? {
 if(maxOf(abs(dx),abs(dy))<threshold)return null
 if(abs(dx)>abs(dy)*1.3f)return if(dx<0)SwipeDirection.RIGHT else SwipeDirection.LEFT
 if(abs(dy)>abs(dx)*1.3f)return if(dy<0)SwipeDirection.DOWN else SwipeDirection.UP
 return null
}
