// SPDX-License-Identifier: GPL-3.0-only
// Next TV — main activity (Compose for TV).
@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class,androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.nexttv.core.tv
import androidx.core.graphics.drawable.toDrawable

import com.nexttv.core.App
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.*
import androidx.tv.material3.*

class TvActivity:ComponentActivity(){
 internal var quickActionsPointerRegion:androidx.compose.ui.geometry.Rect?=null
 internal var directPointerRegion:androidx.compose.ui.geometry.Rect?=null
 internal var replayTouchSeek: ((Int, Boolean, Boolean) -> Unit)? = null
 internal var browserGesture:((Int)->Unit)?=null
 internal var actualMotionScale=-1f
 internal var navigationDiagnostics:(()->org.json.JSONObject)?=null
 internal var videoFrameDiagnostics:(()->org.json.JSONObject)?=null
 internal var browserDiagnostics:(()->org.json.JSONObject)?=null
 internal var gridDiagnostics:(()->org.json.JSONObject)?=null
 override fun dump(prefix:String,fd:java.io.FileDescriptor?,writer:java.io.PrintWriter,args:Array<out String>?){
  super.dump(prefix,fd,writer,args);videoFrameDiagnostics?.let{writer.println(prefix+"NextTVVideoFrame "+it())};writer.println(prefix+"NextTVRuntime cacheReadyMs="+App.get().cacheReadyMillis+" playback="+(PlaybackSession.active?.diagnostics()?:"idle"));writer.println(prefix+"NextTVLogoCache "+com.nexttv.core.ui.LogoLoader.cacheStats())
  when(args?.firstOrNull()){
   "nexttv-scroll-start"->com.nexttv.ScrollBenchmark.start()
   "nexttv-scroll-phase"->com.nexttv.ScrollBenchmark.phase(args.getOrNull(1)?:"warmup")
   "nexttv-scroll-stop"->writer.println(prefix+"NextTVScrollSamples "+com.nexttv.ScrollBenchmark.stop())
  }
  navigationDiagnostics?.let{writer.println(prefix+"NextTVNavigation "+it())}
  writer.println(prefix+"NextTVMotion "+org.json.JSONObject().put("reduced",App.get().prefs.reduceMotion).put("scale",AppMotionScale.scaleFactor).put("actualScale",actualMotionScale))
  writer.println(prefix+"NextTVPreview "+PreviewDiagnostics.snapshot())
  gridDiagnostics?.let{writer.println(prefix+"NextTVGrid "+it())}
  browserDiagnostics?.let{writer.println(prefix+"NextTVBrowser "+it())}
 }

 private var homeEpoch by mutableIntStateOf(0)
 override fun onStop(){if(LaunchPolicy.resetOnReturn(this))homeEpoch++;super.onStop()}
 override fun onNewIntent(intent:android.content.Intent){super.onNewIntent(intent);setIntent(intent);if(LaunchPolicy.resetOnReturn(this))homeEpoch++}

 internal var homeBack:(()->Unit)?=null
 internal var playerBack:(()->Unit)?=null
 internal var homeInteraction:(()->Unit)?=null
 internal var playerInteraction:(()->Unit)?=null
 private var modalKeyOwner:Any?=null
 private var modalKeys:((android.view.KeyEvent)->Boolean)?=null
 internal fun bindModalKeys(owner:Any,handler:(android.view.KeyEvent)->Boolean){modalKeyOwner=owner;modalKeys=handler}
 internal fun unbindModalKeys(owner:Any){if(modalKeyOwner===owner){modalKeyOwner=null;modalKeys=null}}
 private var pageBackOwner:Any?=null
 private var pageBack:(()->Unit)?=null
 internal fun continueBackAfterDialog(){if(com.nexttv.BuildConfig.DEBUG)android.util.Log.d("NextTVFocusBack","continue closing dialog player=${playerBack!=null} page=${pageBack!=null}");(playerBack?:pageBack?:homeBack)?.invoke()}
 internal fun bindPageBack(owner:Any,handler:()->Unit){pageBackOwner=owner;pageBack=handler}
 internal fun unbindPageBack(owner:Any){if(pageBackOwner===owner){pageBackOwner=null;pageBack=null}}
 private fun interaction(){(playerInteraction?:homeInteraction)?.invoke()}
 internal var pointerNavigation=false
  private set

 // Pointer gesture bookkeeping lives on the activity; dispatchTouchEvent below is a
 // standard Activity override instead of a Window.Callback replacement.
 private var replayStepX=0f
 private var replaySteps=0
 private var replayDrag=false
 private var swipeX=0f
 private var swipeY=0f
 private var swipeClaimed=false
 private var swipePassthrough=false

 internal fun dispatchNavigationKey(code:Int,fromPointer:Boolean){
  val previous=pointerNavigation;pointerNavigation=fromPointer
  try{
   dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,code))
   dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP,code))
  }finally{pointerNavigation=previous}
 }

 private fun applyReplayDrag(x: Float, start: Boolean = false, finish: Boolean = false) {
  val stepPixels = 36f * resources.displayMetrics.density
  val steps = ((x - replayStepX) / stepPixels).toInt()
  if (steps == 0 && !finish) return
  replayStepX += steps * stepPixels
  replaySteps += steps
  if (com.nexttv.BuildConfig.DEBUG) android.util.Log.d("NextTVReplayDrag", "x=$x threshold=$stepPixels total=$replaySteps start=$start finish=$finish")
  replayTouchSeek?.invoke(replaySteps, start, finish)
 }

 /**
  * Observe every key before Compose consumes it (input-mode hints, IME/quick-menu routing),
  * then delegate unchanged. BACK is never consumed here: ComponentActivity forwards it to
  * OnBackPressedDispatcher, where per-screen BackHandler runs first.
  * RestrictedApi is a false positive: dispatchKeyEvent is public on android.app.Activity.
  */
 @android.annotation.SuppressLint("RestrictedApi")
 override fun dispatchKeyEvent(event:android.view.KeyEvent):Boolean {
  InteractionStyle.keyboard(event.keyCode,pointerNavigation)
  if(modalKeys?.invoke(event)==true)return true
  if(event.action==android.view.KeyEvent.ACTION_DOWN&&TvKey(event).action!=InputAction.BACK)interaction()
  return super.dispatchKeyEvent(event)
 }

 override fun dispatchGenericMotionEvent(event:android.view.MotionEvent):Boolean{
  if(TvInput.pointerInteraction(event)){InteractionStyle.pointerInput();interaction()}
  return super.dispatchGenericMotionEvent(event)
 }

 override fun dispatchTouchEvent(event:android.view.MotionEvent):Boolean{
  when(event.actionMasked){
   android.view.MotionEvent.ACTION_DOWN->{
    InteractionStyle.pointerInput();interaction();swipeX=event.x;swipeY=event.y;swipeClaimed=false;replayDrag=false;replayStepX=event.x;replaySteps=0
    val point=androidx.compose.ui.geometry.Offset(event.x,event.y)
    swipePassthrough=modalKeys!=null||directPointerRegion?.contains(point)==true||
     quickActionsPointerRegion?.contains(point)==true||event.buttonState and android.view.MotionEvent.BUTTON_SECONDARY!=0||pageBack!=null
   }
   android.view.MotionEvent.ACTION_POINTER_DOWN->{swipePassthrough=true}
   android.view.MotionEvent.ACTION_MOVE->{
    val swipeThreshold=36f*resources.displayMetrics.density
    if (replayDrag) {
     if (event.pointerCount == 1) applyReplayDrag(event.x)
     return true
    }
    if(swipeClaimed)return true
    if(!swipePassthrough&&event.pointerCount==1){
     val dx=event.x-swipeX;val dy=event.y-swipeY
     // Home/settings keep native vertical scrolling; the player shares all four directions.
     if((playerBack==null||browserGesture!=null)&&kotlin.math.abs(dy)>=android.view.ViewConfiguration.get(this@TvActivity).scaledTouchSlop&&kotlin.math.abs(dy)>kotlin.math.abs(dx)*1.3f)swipePassthrough=true
     else{
      val direction=swipeDirection(dx,dy,swipeThreshold)
      if(direction!=null){
       val cancel=android.view.MotionEvent.obtain(event);cancel.action=android.view.MotionEvent.ACTION_CANCEL
       super.dispatchTouchEvent(cancel);cancel.recycle();swipeClaimed=true
       val action=TvInput.swipeAction(direction)
       val horizontal=TvInput.horizontal(action)
       val touchSeek = replayTouchSeek
       if (touchSeek != null && event.isFromSource(android.view.InputDevice.SOURCE_TOUCHSCREEN) && horizontal != null) {
        replayDrag = true
        applyReplayDrag(event.x, start = true)
        return true
       }
       val browserMove = browserGesture
       if (browserMove != null) {
        horizontal?.let { browserMove(-it) }
        return true
       }
       TvInput.directionKey(action)?.let{dispatchNavigationKey(it,fromPointer=true)}
       return true
      }
      if(playerBack!=null&&browserGesture==null)return true
     }
    }
   }
   android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
    if (swipeClaimed) {
     if (replayDrag && event.actionMasked == android.view.MotionEvent.ACTION_UP) applyReplayDrag(event.x, finish = true)
     swipeClaimed = false
     replayDrag = false
     return true
    }
    // Input dispatch may batch the entire threshold-crossing movement into the release.
    if (event.actionMasked == android.view.MotionEvent.ACTION_UP && !swipePassthrough &&
     replayTouchSeek != null && event.isFromSource(android.view.InputDevice.SOURCE_TOUCHSCREEN)) {
     val threshold = 36f * resources.displayMetrics.density
     val direction = swipeDirection(event.x - swipeX, event.y - swipeY, threshold)
     if (direction == SwipeDirection.LEFT || direction == SwipeDirection.RIGHT) {
      val cancel = android.view.MotionEvent.obtain(event)
      cancel.action = android.view.MotionEvent.ACTION_CANCEL
      super.dispatchTouchEvent(cancel)
      cancel.recycle()
      applyReplayDrag(event.x, start = true, finish = true)
      return true
     }
    }
   }
  }
  return super.dispatchTouchEvent(event)
 }

 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
  // Compose screens own their BackHandler; this callback is the last-resort exit path.
  onBackPressedDispatcher.addCallback(this) {
   val handler = homeBack
   if (handler != null) handler() else finish()
  }
  WindowCompat.setDecorFitsSystemWindows(window,false)
  WindowInsetsControllerCompat(window,window.decorView).apply{hide(WindowInsetsCompat.Type.systemBars());systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE}
  setContent(parent=window.decorView.createLifecycleAwareWindowRecomposer(AppMotionScale,lifecycle)){
   val canvas=NextTvStyle.Background
   SideEffect {
    window.setBackgroundDrawable(canvas.toArgb().toDrawable())
   }
   AppTextScale{NextTvTheme{StableFocusScroll{AppModalHost{key(homeEpoch){
    StartupContent(onExit={finish()})
   }}}}}
  }

 }
}
