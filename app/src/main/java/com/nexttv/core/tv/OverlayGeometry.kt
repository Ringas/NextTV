package com.nexttv.core.tv

internal data class BrowserWidths(val group:Float,val channel:Float,val programme:Float,val date:Float,val logo:Float) {
    val guideTotal get()=channel+programme+date
}
/** Values are dp, derived from the actual available window rather than physical TV inches. */
internal fun browserWidths(width:Float,showLogos:Boolean,fontScale:Float=1f):BrowserWidths {
    val compact=((960f-width)/480f).coerceIn(0f,1f)
    val channel=width*(.32f+.03f*compact)
    val logo=(channel*.14f).coerceIn(24f,44f).coerceAtMost(channel*.4f)
    val group=(width*(.115f+.065f*compact)).coerceAtLeast(minOf(144f*fontScale.coerceAtLeast(1f),width*.30f))
    return BrowserWidths(group,channel-if(showLogos)0f else logo+10f,
        width*(.30f+.01f*compact),width*(.08f+.03f*compact),logo)
}
internal fun playerPanelFraction(width:Float):Float = .78f+.04f*((960f-width)/480f).coerceIn(0f,1f)

internal data class HomeSafeInsets(val horizontal:Float,val vertical:Float)
/** Reference tvOS 1920×1080 safe region, expressed as ratios of the Android logical window. */
internal fun homeSafeInsets(width:Int,height:Int)=HomeSafeInsets((width/24f).coerceIn(16f,80f),(height/18f).coerceIn(12f,60f))
