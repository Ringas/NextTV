package com.nexttv.core.tv

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build

internal object LaunchPolicy {
 internal var televisionOverride:Boolean?=null
 fun classify(tvMode:Boolean,tvFeature:Boolean,touch:Boolean,hardware:String,model:String,product:String):Boolean{
  val identity=(hardware+" "+model+" "+product).lowercase()
  val emulator=listOf("ranchu","goldfish","cuttlefish","qemu","emulator","sdk_gphone","sdk_phone","mumu","nox","memu","vbox").any{it in identity}||model.lowercase().contains("android sdk")
  return !emulator&&(tvMode||tvFeature||!touch)
 }
 fun resetOnReturn(context:Context):Boolean=televisionOverride?:classify(
  context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK==Configuration.UI_MODE_TYPE_TELEVISION,
  context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)||context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION),
  context.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN),Build.HARDWARE,Build.MODEL,Build.PRODUCT)
}

/** Resolve the initial destination directly, after the real channel cache is available. */
internal fun initialPlaybackChannel(channels:List<com.nexttv.core.data.Channel>,startLive:Boolean,lastId:String):com.nexttv.core.data.Channel? =
 if(startLive)channels.firstOrNull{it.id==lastId}?:channels.firstOrNull{c->c.playbackRoutes.any{it.originalId==lastId}}?:channels.firstOrNull()else null
