package com.nexttv
import android.app.*
import android.content.Intent
import android.os.*
import android.view.accessibility.AccessibilityNodeInfo as Node
import com.nexttv.core.App
import com.nexttv.core.tv.*
class TelevisionReturnInstrumentation:Instrumentation(){
 private fun root():Node?=uiAutomation.rootInActiveWindow?.takeIf{it.packageName?.toString()=="com.nexttv"}?:uiAutomation.windows.firstNotNullOfOrNull{it.root?.takeIf{n->n.packageName?.toString()=="com.nexttv"}}
 private fun tree(n:Node?=root()):String=if(n==null)""else buildString{append(n.text?:"");append('\n');for(i in 0 until n.childCount)append(tree(n.getChild(i)))}
 private fun focused(n:Node?=root()):Node?{if(n==null)return null;if(n.isFocused)return n;for(i in 0 until n.childCount)focused(n.getChild(i))?.let{return it};return null}
 private fun find(t:String,n:Node?=root()):Node?{if(n==null)return null;if(n.text?.toString()==t)return n;for(i in 0 until n.childCount)find(t,n.getChild(i))?.let{return it};return null}
 private fun await(label:String,f:()->Boolean){val until=SystemClock.uptimeMillis()+15000;while(SystemClock.uptimeMillis()<until){if(f())return;SystemClock.sleep(80)};error("Timeout $label: ${tree().take(1000)} activePlayback=${PlaybackSession.active!=null}")}
 private fun reopen(){uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME);SystemClock.sleep(400);uiAutomation.executeShellCommand("am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -f 0x10200000 -n com.nexttv/com.nexttv.core.tv.TvActivity").use{fd->java.io.FileInputStream(fd.fileDescriptor).use{it.readBytes()}}}
 override fun onCreate(args:Bundle?){super.onCreate(args);start()}
 override fun onStart(){val info=uiAutomation.serviceInfo;info.flags=info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;uiAutomation.serviceInfo=info;val bootstrap=startActivitySync(Intent().setClassName("com.nexttv","com.nexttv.core.tv.TvActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();runOnMainSync{bootstrap.finish()};waitForIdleSync();val app=App.get();val group=app.prefs.selectedGroup;var activity:Activity?=null
 try{LaunchPolicy.televisionOverride=true;app.prefs.selectedGroup="";activity=startActivitySync(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClassName("com.nexttv","com.nexttv.core.tv.TvActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));await("initial Channels"){tree(focused()).trim()=="频道"};sendKeyDownUpSync(22);sendKeyDownUpSync(22);await("Settings"){tree(focused()).trim()=="设置"};reopen();await("TV settings return Channels"){tree(focused()).trim()=="频道"&&tree().contains("频道分类：")};sendKeyDownUpSync(21);check(tree(focused()).trim()=="频道")
 var n=find("CCTV1综合")?:error("Missing real channel");while(!n.isClickable)n=n.parent?:error("No card");n.performAction(Node.ACTION_CLICK);await("player"){PlaybackSession.active!=null};reopen();await("TV player return Channels"){tree(focused()).trim()=="频道"&&PlaybackSession.active==null};finish(Activity.RESULT_OK,Bundle().apply{putString("stream","PASS simulated television policy: Settings → Home → Channels; player → Home → Channels; left boundary held\n")})
 }catch(t:Throwable){finish(Activity.RESULT_CANCELED,Bundle().apply{putString("stream",t.stackTraceToString())})}finally{LaunchPolicy.televisionOverride=null;app.prefs.selectedGroup=group;runOnMainSync{activity?.finish()}}}
}
