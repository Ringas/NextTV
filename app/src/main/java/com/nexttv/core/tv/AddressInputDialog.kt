package com.nexttv.core.tv

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.*
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.Text
import com.nexttv.SubscriptionInputServer
import com.nexttv.subscriptionLanAddress
import com.nexttv.AddressInputKind
import com.nexttv.normalizeAddressInput
import com.nexttv.addressInputLabels
import com.nexttv.core.ui.QrCode
import kotlinx.coroutines.*

/** Remote confirmation opens keyboard; merely focusing the address never opens it. */
@Composable internal fun AddressInputDialog(initial:String,epg:Boolean,editingSource:Boolean=false,onDismiss:()->Unit,onConfirm:(String)->Unit){
 val kind=if(epg)AddressInputKind.PROGRAMME_GUIDE else AddressInputKind.SUBSCRIPTION
 val labels=addressInputLabels(kind,editingSource)
 val title=labels.title;val fieldLabel=labels.field;val confirmLabel=labels.confirm;val action=labels.action
 val scope=rememberCoroutineScope();val context=LocalContext.current;val owner=LocalLifecycleOwner.current
 val dismiss by rememberUpdatedState(onDismiss)
 val serverRef=remember{java.util.concurrent.atomic.AtomicReference<SubscriptionInputServer?>(null)}
 var draft by remember{mutableStateOf(initial)};var editing by remember{mutableStateOf(false)}
 var address by remember{mutableStateOf("")};var status by remember{mutableStateOf("")};var error by remember{mutableStateOf("")}
 DisposableEffect(owner){val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_STOP&&LaunchPolicy.resetOnReturn(context)){serverRef.getAndSet(null)?.close();dismiss()}};owner.lifecycle.addObserver(observer);onDispose{owner.lifecycle.removeObserver(observer);serverRef.getAndSet(null)?.close()}}
 LaunchedEffect(Unit){var server:SubscriptionInputServer?=null
  try{withContext(Dispatchers.IO){server=SubscriptionInputServer(subscriptionLanAddress(),epgInput=epg,namedInput=!epg,simpleAddress=true){received->scope.launch{draft=received;status="已收到手机输入，请确认$action";error=""}};serverRef.set(server)};if(LaunchPolicy.resetOnReturn(context)&&!owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))throw CancellationException("Input page backgrounded");address=server!!.url;delay(300000);address="";status="二维码已过期，请返回后重新打开"}
  catch(e:CancellationException){throw e}catch(_:Exception){status="扫码服务不可用，可直接输入地址"}
  finally{server?.close()}
 }
 val confirmFocus=remember{FocusRequester()}
 TvDialog(title,onDismiss,width=620.dp,keyboardSession=if(editing)1 else 0,onKeyboardDismiss={editing=false}){
  val keyboard=LocalSoftwareKeyboardController.current
  val input=remember{FocusRequester()}
  val inputView=LocalView.current;val inWindowModal=LocalInWindowModal.current
  LaunchedEffect(Unit){awaitModalFocusWindow(inputView,inWindowModal);input.requestFocus()}
  LaunchedEffect(editing){if(editing){withFrameNanos{};input.requestFocus();keyboard?.show()}}
  val stacked=LocalConfiguration.current.screenWidthDp<600
  val compact=LocalConfiguration.current.screenWidthDp<1000||LocalConfiguration.current.fontScale>1.1f
  Column(Modifier.fillMaxWidth().weight(1f,false).appVerticalScroll(rememberScrollState()).padding(12.dp)){
   val qr:@Composable ()->Unit={
   var bitmap by remember(address){mutableStateOf<android.graphics.Bitmap?>(null)}
   LaunchedEffect(address){if(address.startsWith("http"))bitmap=withContext(Dispatchers.Default){QrCode.render(address,480)}}
   Box(Modifier.size(if(compact)144.dp else 180.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(InteractionStyle.radius)).background(androidx.compose.ui.graphics.Color.White),contentAlignment=Alignment.Center){
    val rendered=bitmap
    if(rendered!=null)Image(rendered.asImageBitmap(),"$fieldLabel 输入二维码",Modifier.fillMaxSize())
    else AppText(if(status.isBlank())"正在生成二维码…"else"请手动输入",textAlign=androidx.compose.ui.text.style.TextAlign.Center,fontSize=InteractionStyle.caption,color=NextTvStyle.QrInk)
   }
   }
   val form:@Composable ()->Unit={
   Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)){
    AppText("使用手机扫描二维码\n或在浏览器访问地址",fontSize=InteractionStyle.body,color=NextTvStyle.Text)
    AppText(address.ifBlank{"手机与设备连接同一网络"},fontSize=InteractionStyle.caption,color=NextTvStyle.Muted)
    androidx.compose.material3.OutlinedTextField(draft,{draft=it;error=""},Modifier.fillMaxWidth().heightIn(min=72.dp).focusRequester(input).onFocusChanged{if(!it.isFocused)editing=false}.focusProperties{up=FocusRequester.Cancel;down=confirmFocus;next=confirmFocus;previous=confirmFocus}.onPreviewKeyEvent{e->if(!editing&&TvInput.action(e.nativeKeyEvent.keyCode)==InputAction.DOWN&&e.nativeKeyEvent.action==android.view.KeyEvent.ACTION_DOWN){confirmFocus.requestFocus();true}else if(TvInput.confirm(e.nativeKeyEvent.keyCode,includeSpace=!editing)&&e.nativeKeyEvent.action==android.view.KeyEvent.ACTION_DOWN){editing=true;true}else false}.pointerInput(Unit){awaitEachGesture{awaitFirstDown(requireUnconsumed=false);editing=true;waitForUpOrCancellation()}},singleLine=true,readOnly=!editing,keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(showKeyboardOnFocus=false),shape=androidx.compose.foundation.shape.RoundedCornerShape(InteractionStyle.radius),colors=androidx.compose.material3.OutlinedTextFieldDefaults.colors(focusedBorderColor=NextTvStyle.Muted,focusedLabelColor=NextTvStyle.Text,cursorColor=NextTvStyle.Text),label={androidx.compose.material3.Text(fieldLabel)})
    if(epg)AppText("留空保存可恢复使用订阅自带节目单",fontSize=InteractionStyle.caption,color=NextTvStyle.Muted)
    if(!epg)AppText("名称,订阅地址（中英文逗号均可，名称可省略）",fontSize=InteractionStyle.caption,color=NextTvStyle.Muted)
    AppText(error.ifBlank{status.ifBlank{"手机发送后，请在此确认$action"}},fontSize=InteractionStyle.caption,color=if(error.isBlank())NextTvStyle.Muted else NextTvStyle.Error)
   }
   }
   if(stacked){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center){qr()};Spacer(Modifier.height(12.dp));form()}
   else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(20.dp),verticalAlignment=Alignment.CenterVertically){qr();Box(Modifier.weight(1f)){form()}}
  }
  Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),horizontalArrangement=Arrangement.End){
   TvButton(confirmLabel,Modifier.focusRequester(confirmFocus).focusProperties{up=input;previous=input;next=input;down=FocusRequester.Cancel;left=FocusRequester.Cancel;right=FocusRequester.Cancel},onClick={val valid=runCatching{normalizeAddressInput(draft,kind)}.getOrNull();if(valid==null)error="请输入完整的 http:// 或 https:// 地址"else onConfirm(valid)})
  }
 }
}
