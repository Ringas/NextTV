package com.nexttv
import android.app.*
import android.content.Intent
import android.os.*
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.nexttv.core.tv.*
import kotlinx.coroutines.delay
class AuditChoiceFocusInstrumentation:Instrumentation(){
 override fun onCreate(b:Bundle?){super.onCreate(b);start()}
 fun focus(n:AccessibilityNodeInfo?=uiAutomation.rootInActiveWindow):String{if(n==null)return "";if(n.isFocused)return tree(n);for(i in 0 until n.childCount){val f=focus(n.getChild(i));if(f.isNotEmpty())return f};return ""}
 fun tree(n:AccessibilityNodeInfo?):String=if(n==null)""else buildString{append(n.text?:"");for(i in 0 until n.childCount){append('\n');append(tree(n.getChild(i)))}}
 override fun onStart(){val activity=startActivitySync(Intent().setClassName("com.nexttv","com.nexttv.core.tv.TvActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as TvActivity;var result=Activity.RESULT_OK;var report=""
 try{
  val options=mutableStateOf(listOf(ChoiceOption("1","线路一","测速中"),ChoiceOption("2","线路二","测速中")))
  runOnMainSync{activity.setContent{ChoiceDialog("焦点审查探针",options.value,"1",onDismiss={}){}}}
  var end=SystemClock.uptimeMillis()+10000;while(!focus().contains("线路一")&&SystemClock.uptimeMillis()<end)Thread.sleep(100)
  check(focus().contains("线路一")){"Initial focus unavailable: ${tree(uiAutomation.rootInActiveWindow)}"}
  sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);end=SystemClock.uptimeMillis()+5000;while(!focus().contains("线路二")&&SystemClock.uptimeMillis()<end)Thread.sleep(100)
  val before=focus();check(before.contains("线路二")){"Cannot move to second option: $before"}
  runOnMainSync{options.value=options.value.map{it.copy(detail="250 ms")}}
  Thread.sleep(700);val after=focus();report="PASS ${if(after.contains("线路一"))"REPRO"else"NOT_REPRO"} choice detail update resets focus: before=$before after=$after (minimal component probe)\n"
 }catch(t:Throwable){result=Activity.RESULT_CANCELED;report=t.stackTraceToString()}finally{runOnMainSync{activity.finish()}}
 finish(result,Bundle().apply{putString("stream",report)})
 }
}
