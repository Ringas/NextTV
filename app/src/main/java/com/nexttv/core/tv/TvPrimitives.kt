@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.nexttv.core.tv

import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.relocation.*
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.input.key.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import androidx.compose.foundation.interaction.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.heading
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.*

object NextTvStyle {
 // Shared reactive semantic colours: the theme switches without recreating playback.
 private val light get()=com.nexttv.core.App.get().prefs.lightGlass
 private fun tone(dark:Long,pale:Long)=Color(if(light)pale else dark)
 // Dark: near-black neutral canvas, flat graphite cards and a single cool accent.
 // Light: cool paper canvas, white cards, ink text and the same accent, never a mirror-inverted dark UI.
 val Background get()=tone(0xFF0F1116,0xFFF3F6FA)
 val Text get()=tone(0xFFF3F5F9,0xFF18202B)
 val Muted get()=tone(0xFFA8B1C0,0xFF5B6778)
 val AccentText get()=tone(0xFFA9CCFF,0xFF1D4F9E)
 val MediaBadge get()=tone(0xD9161A20,0xE6FFFFFF)
 val QrInk=Color(0xFF1D2633)
 val Disabled get()=tone(0xFF737D8C,0xFF8A94A3)
 val Accent get()=tone(0xFF7DB3FF,0xFF2563C9)
 val Stroke get()=tone(0xFF2B323D,0xFFD6DEE8)
 val Focus get()=tone(0xFF2E3846,0xFFDDEAFB)
 val Selected get()=tone(0xFF242B35,0xFFEAF1F9)
 val Surface get()=tone(0xFF1A1F27,0xFFFFFFFF)
 val Button get()=tone(0xFF232A34,0xFFE9EEF5)
 val Pressed get()=tone(0xFF35404F,0xFFDCE7F4)
 val DisabledSurface get()=tone(0xFF1A1E24,0xFFEDF1F6)
 val Divider get()=tone(0xFF2A313C,0xFFDCE3EC)
 val Badge get()=tone(0xFF2A323E,0xFFE9EFF7)
 val ChannelNumber get()=tone(0xFF303846,0xFFDDE7F2)
 val AccentBadge get()=tone(0xFF233A56,0xFFDFECFF)
 val PlayerInfoBorder get()=tone(0x2BFFFFFF,0xFFD6DEE8)
 val PlayerProgressTrack get()=tone(0x3DFFFFFF,0xFFD6DEE8)
 val PlayerProgressFill get()=tone(0xFFF2F4F8,0xFF24364D)
 val SpecNeutralFill get()=tone(0x1FFFFFFF,0x121B2A3A)
 val SpecNeutralStroke get()=tone(0x2BFFFFFF,0x2B1B2A3A)
 val SpecWarmFill get()=tone(0x33FF9F0A,0xFFFFF1DF)
 val SpecWarmStroke get()=tone(0x80FFB24D,0xFFE2A45D)
 val SpecWarmText get()=tone(0xFFFFC978,0xFF8A4B00)
 val SpecCoolFill get()=tone(0x2E5AA9FF,0xFFE6F2FF)
 val SpecCoolStroke get()=tone(0x7F5AC8FA,0xFF7EA9D8)
 val SpecCoolText get()=tone(0xFFA9CCFF,0xFF0B4F9E)
 val Error get()=tone(0xFFFFB4AB,0xFFA32B22)
 val Nav get()=tone(0xF21B2028,0xF2FBFCFE)
 val NavActive get()=tone(0xF2262E39,0xF2EDF2F8)
 val NavFocused get()=tone(0xF235414F,0xF2E1EBF8)
 // tvOS 27 fusion, reduced: only the floating functional layer gets a wash,
 // a dark edge and a quiet specular line. Opacity stays high for legibility.
 val GlassReadingTop get()=tone(0xFA1C2129,0xFFFFFFFF)
 val GlassReadingBottom get()=tone(0xF3171A20,0xFFFAFBFD)
 val PlayerGlassTop get()=tone(0xF21A1D24,0xFAFFFFFF)
 val PlayerGlassBottom get()=tone(0xE6161920,0xFFF7F9FC)
 val GlassSpecular get()=tone(0x33FFFFFF,0x14FFFFFF)
 val GlassEdge get()=tone(0x59000000,0x241E2C3D)
 val GlassShadow get()=tone(0x59000000,0x2633455A)
 val Panel get()=tone(0xF2181A21,0xFFFAFFFFFF)
 val Home get()=tone(0xFF0F1116,0xFFF3F6FA)
 val Decelerate=Easing {1f-(1f-it)*(1f-it)}
 val CardEase=Easing {((kotlin.math.cos((it+1)*Math.PI)/2)+.5).toFloat()}
}
internal val LocalSurfaceFocused=compositionLocalOf{false}

/** Native Compose focus/click/long-click with the shared TV theme and input-mode visuals. */
@Composable fun TvSurface(modifier:Modifier=Modifier,kind:String="button",selected:Boolean=false,onFocus:(()->Unit)?=null,onLongClick:(()->Unit)?=null,longClickLabel:String?=null,enabled:Boolean=true,focusReturn:FocusRequester?=null,onClick:()->Unit,content:@Composable BoxScope.()->Unit) {
 // A supplied return target is already attached by the caller; do not duplicate its node.
 val ownFocus=focusReturn?:remember{FocusRequester()}
 val modalController=LocalModalController.current
 val inModal=LocalInWindowModal.current
 fun rememberReturnTarget(){if(!inModal)modalController?.lastBackgroundFocus=ownFocus}
 var focused by remember{mutableStateOf(false)}
 val scope=rememberCoroutineScope()
 var holdJob by remember{mutableStateOf<Job?>(null)}
 var holding by remember{mutableStateOf(false)}
 var longFired by remember{mutableStateOf(false)}
 val longAction by rememberUpdatedState(onLongClick?.let{action->{rememberReturnTarget();action()}})
 val shortAction by rememberUpdatedState<()->Unit>({rememberReturnTarget();onClick()})
 val holdMillis=androidx.compose.ui.platform.LocalViewConfiguration.current.longPressTimeoutMillis
 val card=kind=="card"||kind=="list"
 // Every selectable surface shares immediate local feedback. Top navigation retains its spring.
 val visibleFocus=focused&&!InteractionStyle.pointer&&enabled
 val interactions=remember{MutableInteractionSource()}
 val hovered by interactions.collectIsHoveredAsState()
 val pressed by interactions.collectIsPressedAsState()
 val radius=when(kind){"setting","side"->InteractionStyle.sideRadius;"card","list","source"->InteractionStyle.cardRadius;"nav"->InteractionStyle.pillInnerRadius;"quick","playerRow"->InteractionStyle.badgeRadius;"button","action","chip","danger"->InteractionStyle.controlRadius;else->InteractionStyle.radius}
 // Navigation has exactly one shared moving capsule; never paint a second focus fill.
 val background=when{
  kind=="nav"->Color.Transparent
  !enabled->NextTvStyle.DisabledSurface
  pressed->NextTvStyle.Pressed
  visibleFocus||(hovered&&InteractionStyle.pointer)->NextTvStyle.Focus
  selected->if(kind=="playerRow")NextTvStyle.SpecNeutralFill else NextTvStyle.Selected
  kind=="source"->NextTvStyle.Surface
  kind=="quick"->NextTvStyle.SpecNeutralFill
  card||kind=="setting"||kind=="side"||kind=="playerRow"||kind=="choice"->Color.Transparent
  else->NextTvStyle.Button
 }
 val foreground=when{
  !enabled->NextTvStyle.Disabled
  kind=="danger"->NextTvStyle.Error
  kind=="nav"->if(selected||visibleFocus)NextTvStyle.Text else NextTvStyle.Muted
  kind=="quick"->NextTvStyle.Text
  else->NextTvStyle.Text
 }
 val reduced=com.nexttv.core.App.get().prefs.reduceMotion
 // Blur must clear on the first rendered frame: fading the departing control
 // paints several stale highlights during remote key repeat. Only arrival eases in.
 val emphasis=animateFloatAsState(if(visibleFocus)1f else 0f,if(reduced||!visibleFocus)snap()else tween(60,easing=LinearOutSlowInEasing),label="focus-material")
 val shape=RoundedCornerShape(radius)
 Box(modifier.onPreviewKeyEvent{event->
   val key=event.nativeKeyEvent
   if(!enabled)false
   else if(TvInput.action(key.keyCode)==InputAction.MENU&&longAction!=null){
    if(key.action==android.view.KeyEvent.ACTION_DOWN&&key.repeatCount==0)longAction?.invoke();true
   }else if(TvInput.confirm(key.keyCode)&&(holding||focused&&longAction!=null)){
    if(key.action==android.view.KeyEvent.ACTION_DOWN){if(!holding&&key.repeatCount==0){holding=true;longFired=false;holdJob=scope.launch{delay(holdMillis);longFired=true;longAction?.invoke()}}}
    else if(key.action==android.view.KeyEvent.ACTION_UP){val invoke=holding&&!longFired&&!key.isCanceled;holding=false;holdJob?.cancel();if(invoke)shortAction()}
    true
   }else false
  }.pointerInput(onLongClick!=null,enabled){
   if(onLongClick!=null&&enabled)awaitPointerEventScope{
    var secondary=false
    while(true){val event=awaitPointerEvent(PointerEventPass.Initial)
     if(event.type==PointerEventType.Press&&event.buttons.isSecondaryPressed){secondary=true;longAction?.invoke()}
     if(secondary){event.changes.forEach{it.consume()};if(event.type==PointerEventType.Release)secondary=false}
    }
   }
  }.semantics{if(kind=="nav"||kind=="side"||kind=="playerRow"||selected)this.selected=selected}.then(if(focusReturn==null)Modifier.focusRequester(ownFocus)else Modifier).focusProperties{canFocus=enabled}.onFocusChanged{focused=it.isFocused;if(!it.hasFocus){holdJob?.cancel();holding=false};if(it.isFocused){rememberReturnTarget();if(FocusAudit.frame!=null)FocusAudit.generation++;onFocus?.invoke()}}
  .then(if(kind=="card"||kind=="list")Modifier else Modifier.graphicsLayer()).drawWithContent{drawContent();if(visibleFocus)FocusAudit.frame?.invoke(FocusAudit.generation,android.os.SystemClock.uptimeMillis())}.drawWithCache{val outline=shape.createOutline(size,layoutDirection,this);val line=when(kind){"button","action","chip","danger","source"->NextTvStyle.PlayerInfoBorder;"setting","choice"->if(selected)NextTvStyle.PlayerInfoBorder else Color.Transparent;else->Color.Transparent};onDrawBehind{val paintedFill=if(visibleFocus)background.copy(alpha=background.alpha*(.8f+.2f*emphasis.value))else background;drawOutline(outline,paintedFill);if(kind=="playerRow"&&selected){drawOutline(outline,NextTvStyle.Text.copy(alpha=.28f),style=androidx.compose.ui.graphics.drawscope.Stroke(width=.75.dp.toPx()))}else if(line.alpha>0f)drawOutline(outline,line,style=androidx.compose.ui.graphics.drawscope.Stroke(width=.75.dp.toPx()))}}
  .hoverable(interactions,enabled=enabled)
  .combinedClickable(enabled=enabled,role=if(kind=="nav")androidx.compose.ui.semantics.Role.Tab else if(kind=="choice")null else androidx.compose.ui.semantics.Role.Button,interactionSource=interactions,indication=null,onClick={shortAction()},onLongClick=onLongClick?.let{{longAction?.invoke()}},onLongClickLabel=longClickLabel?:if(onLongClick!=null)"更多操作"else null)){
  CompositionLocalProvider(LocalContentColor provides foreground,LocalAppForeground provides foreground,LocalSurfaceFocused provides visibleFocus){content()}
 }

}
@Composable fun TvButton(label:String,modifier:Modifier=Modifier,selected:Boolean=false,kind:String="button",enabled:Boolean=true,onFocus:(()->Unit)?=null,focusReturn:FocusRequester?=null,onClick:()->Unit){
 TvSurface(modifier.widthIn(min=if(kind=="chip")64.dp else 80.dp).heightIn(min=if(kind=="chip"||kind=="action")36.dp else 44.dp),kind,selected,onFocus,enabled=enabled,focusReturn=focusReturn,onClick=onClick){
  Box(Modifier.align(Alignment.Center).padding(horizontal=if(kind=="chip")16.dp else 18.dp,vertical=if(kind=="action")5.dp else 9.dp),contentAlignment=Alignment.Center){AppText(label,textAlign=androidx.compose.ui.text.style.TextAlign.Center,fontSize=InteractionStyle.body,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)}
 }
}
private class DialogWindowSession{var disposed=false}

internal val LocalStableDialogHost=staticCompositionLocalOf{false}

/** A full-window anchor keeps every sheet centered while its actual contour changes size.
 * Only taps outside the card dismiss it; blank space inside a sheet remains inert. */
@Composable private fun CenteredDialogContent(onDismiss:()->Unit,content:@Composable ()->Unit){
 val latestDismiss by rememberUpdatedState(onDismiss)
 val bounds=remember{arrayOfNulls<androidx.compose.ui.geometry.Rect>(1)}
 Box(Modifier.fillMaxSize().pointerInput(Unit){detectTapGestures{point->
  if(com.nexttv.BuildConfig.DEBUG)android.util.Log.d("NextTVModal","outsideTap=$point bounds=${bounds[0]}");if(bounds[0]?.contains(point)==false)latestDismiss()
 }},contentAlignment=Alignment.Center){
  Box(Modifier.onGloballyPositioned{bounds[0]=it.boundsInRoot()}){content()}
 }
}

@Composable internal fun StableDialogHost(onDismiss:()->Unit,transitionKey:Any?=null,content:@Composable ()->Unit){
 val session=remember{DialogWindowSession()}
 val activity=androidx.activity.compose.LocalActivity.current as? TvActivity
 val latestDismiss by rememberUpdatedState(onDismiss)
 DisposableEffect(Unit){onDispose{session.disposed=true}}
 val render:@Composable ()->Unit={
  val modalController=LocalModalController.current
  LaunchedEffect(transitionKey){withFrameNanos{};modalController?.focusDefault?.invoke()}
  val reduced=com.nexttv.core.App.get().prefs.reduceMotion
  val reveal = remember(transitionKey) { Animatable(if (reduced) 1f else .86f) }
  LaunchedEffect(transitionKey, reduced) {
   if (reduced) reveal.snapTo(1f) else reveal.animateTo(1f, tween(120, easing = LinearOutSlowInEasing))
  }
  CenteredDialogContent({if(session.disposed)activity?.continueBackAfterDialog()else latestDismiss()}){
   Box(Modifier.graphicsLayer { alpha = reveal.value }
    .glassReadingPanel(RoundedCornerShape(InteractionStyle.panelRadius))) {
    CompositionLocalProvider(LocalStableDialogHost provides true) { content() }
   }
  }
 }
 if(LocalModalController.current!=null)AppModalContent(render)
 else Dialog(onDismissRequest={if(session.disposed)activity?.continueBackAfterDialog()else latestDismiss()},properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false)){render()}

}

@Composable fun TvDialog(title:String,onDismiss:()->Unit,onKeyboardDismiss:(()->Unit)?=null,width:Dp=520.dp,onKeyboardNavigate:((Int)->Boolean)?=null,keyboardSession:Int=0,titleScale:Float=1f,content:@Composable ColumnScope.()->Unit){
 val embedded=LocalStableDialogHost.current
 val inWindow=LocalInWindowModal.current||LocalModalController.current!=null
 val session=remember{DialogWindowSession()}
 DisposableEffect(Unit){onDispose{session.disposed=true}}
 var dismissing by remember{mutableStateOf(false)}
 val hostActivity=androidx.activity.compose.LocalActivity.current as? TvActivity
 val latestOnDismiss by rememberUpdatedState(onDismiss)
 val latestBackDismiss by rememberUpdatedState<()->Unit>({
  // A closing native window can still receive the next physical Back. Route that
  // press to the now-current page instead of dismissing the old sheet twice.
  if(dismissing||session.disposed)hostActivity?.continueBackAfterDialog()
  else{dismissing=true;latestOnDismiss()}
 })
 val body:@Composable ()->Unit={
  val keyboard=LocalSoftwareKeyboardController.current
  val focusManager=androidx.compose.ui.platform.LocalFocusManager.current
  val imeVisible=WindowInsets.isImeVisible
  var imeHiding by remember{mutableStateOf(false)}
  val view=LocalView.current
  val dialogWindow=(view.parent as? DialogWindowProvider)?.window
  val latestDismiss by rememberUpdatedState(latestBackDismiss)
  val latestKeyboardNavigate by rememberUpdatedState(onKeyboardNavigate)
  SideEffect{dialogWindow?.setWindowAnimations(0)}
  var dismissAfterIme by remember{mutableStateOf(false)}
  var imeSettled by remember{mutableStateOf(false)}
  LaunchedEffect(keyboardSession){imeHiding=false;dismissAfterIme=false;imeSettled=false}
  LaunchedEffect(imeHiding){if(imeHiding){
   withTimeoutOrNull(1200){while(ViewCompat.getRootWindowInsets(dialogWindow?.decorView?:view)?.isVisible(WindowInsetsCompat.Type.ime())==true)withFrameNanos{}}
   withFrameNanos{};withFrameNanos{}
   imeSettled=true
   onKeyboardDismiss?.invoke()
   if(dismissAfterIme)latestDismiss()
  }}
  fun hideInput(){imeSettled=false;imeHiding=true;view.context.getSystemService(android.view.inputmethod.InputMethodManager::class.java)?.hideSoftInputFromWindow(dialogWindow?.decorView?.windowToken?:view.windowToken,0)}
  val handleKey by rememberUpdatedState<(android.view.KeyEvent)->Boolean>({event->
   if(com.nexttv.BuildConfig.DEBUG)android.util.Log.d("NextTVIme","key=${event.keyCode} action=${event.action} ime=$imeVisible hiding=$imeHiding window=${dialogWindow!=null}")
   if(event.action==android.view.KeyEvent.ACTION_DOWN&&dialogWindow==null&&!inWindow)InteractionStyle.keyboard(event.keyCode)
   if(event.keyCode==android.view.KeyEvent.KEYCODE_BACK||event.keyCode==android.view.KeyEvent.KEYCODE_ESCAPE){
    if(event.action==android.view.KeyEvent.ACTION_DOWN&&event.repeatCount==0){
     val visible=ViewCompat.getRootWindowInsets(dialogWindow?.decorView?:view)?.isVisible(WindowInsetsCompat.Type.ime())?:imeVisible
     if(imeHiding){if(imeSettled)latestDismiss()else dismissAfterIme=true}else if(visible){hideInput()}else if(keyboardSession>0){keyboard?.hide();onKeyboardDismiss?.invoke()}else latestDismiss()
    }
    true
   }else false
  })
  val modalOwner=remember{Any()}
  DisposableEffect(hostActivity,inWindow){
   if(inWindow)hostActivity?.bindModalKeys(modalOwner){event->
    (event.action==android.view.KeyEvent.ACTION_DOWN&&InteractionStyle.keyboardAfterPointer&&TvInput.vertical(event.keyCode)&&latestKeyboardNavigate?.invoke(event.keyCode)==true)||handleKey(event)
   }
   onDispose{if(inWindow)hostActivity?.unbindModalKeys(modalOwner)}
  }
  DisposableEffect(dialogWindow){
   val window=dialogWindow;val original=window?.callback
   val callback=if(window!=null&&original!=null)object:android.view.Window.Callback by original{
    override fun dispatchKeyEvent(event:android.view.KeyEvent):Boolean {if(event.action==android.view.KeyEvent.ACTION_DOWN)InteractionStyle.keyboard(event.keyCode);if(onKeyboardDismiss==null&&event.action==android.view.KeyEvent.ACTION_DOWN&&event.keyCode !in listOf(android.view.KeyEvent.KEYCODE_BACK,android.view.KeyEvent.KEYCODE_ESCAPE))imeHiding=false;return (event.action==android.view.KeyEvent.ACTION_DOWN&&InteractionStyle.keyboardAfterPointer&&TvInput.vertical(event.keyCode)&&latestKeyboardNavigate?.invoke(event.keyCode)==true)||handleKey(event)||original.dispatchKeyEvent(event)}
    override fun dispatchTouchEvent(event:android.view.MotionEvent):Boolean{if(event.actionMasked==android.view.MotionEvent.ACTION_DOWN){InteractionStyle.pointerInput();if(onKeyboardDismiss==null)imeHiding=false};return original.dispatchTouchEvent(event)}
    override fun dispatchGenericMotionEvent(event:android.view.MotionEvent):Boolean{if(event.actionMasked==android.view.MotionEvent.ACTION_SCROLL||event.actionMasked==android.view.MotionEvent.ACTION_HOVER_MOVE)InteractionStyle.pointerInput();return original.dispatchGenericMotionEvent(event)}
   }else null
   if(callback!=null)window?.callback=callback
   onDispose{if(window!=null&&window.callback===callback&&original!=null)window.callback=original}
  }
  androidx.activity.compose.BackHandler{if(imeHiding){if(imeSettled)latestDismiss()else dismissAfterIme=true}else if(imeVisible){hideInput()}else latestDismiss()}
  Column(Modifier.semantics{isTraversalGroup=true;paneTitle=title}.onPreInterceptKeyBeforeSoftKeyboard{handleKey(it.nativeKeyEvent)}.onPreviewKeyEvent{handleKey(it.nativeKeyEvent)}.width(minOf(width,(androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp-48.dp).coerceAtLeast(240.dp))).heightIn(max=(androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp-48.dp).coerceIn(160.dp,480.dp)).then(if(embedded)Modifier else Modifier.glassReadingPanel(RoundedCornerShape(InteractionStyle.panelRadius))).padding(22.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
   AppText(title,Modifier.semantics{heading()},color=NextTvStyle.Text,fontSize=InteractionStyle.heading*titleScale,fontWeight=FontWeight.Bold,maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
   content()
  }
 }
 if(embedded)body()else if(LocalModalController.current!=null)AppModalContent{CenteredDialogContent({latestBackDismiss()}){body()}}else Dialog(onDismissRequest={if(session.disposed||dismissing)hostActivity?.continueBackAfterDialog()else{dismissing=true;latestOnDismiss()}},properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false,dismissOnClickOutside=true,dismissOnBackPress=true)){CenteredDialogContent({latestBackDismiss()}){body()}}
}
