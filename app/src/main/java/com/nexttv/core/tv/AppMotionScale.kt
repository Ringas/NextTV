// SPDX-License-Identifier: GPL-3.0-only
@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.nexttv.core.tv
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.ui.MotionDurationScale
import com.nexttv.core.App

/** App-local animation scale; never changes the television's system settings. */
internal object AppMotionScale:MotionDurationScale {
 private var systemScale by androidx.compose.runtime.mutableFloatStateOf(1f)
 override val scaleFactor:Float get()=if(App.get().prefs.reduceMotion)0f else systemScale
 fun refresh(context:Context){systemScale=runCatching{Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)}.getOrDefault(1f).coerceAtLeast(0f)}
 fun install(context:Context){
  context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,object:ContentObserver(Handler(Looper.getMainLooper())){override fun onChange(selfChange:Boolean){refresh(context)}})

 }
}
