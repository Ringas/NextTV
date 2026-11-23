// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.tv

import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.security.MessageDigest

/** Bounded local history. No raw subscription URL or credentials are stored. */
internal class DecoderHistory(context:Context){
 private val prefs=context.getSharedPreferences("decoder_history_v1",Context.MODE_PRIVATE)
 private val device=Build.FINGERPRINT
 private val entries=runCatching{JSONObject(prefs.getString("entries","{}")?:"{}")}.getOrDefault(JSONObject())
 private fun key(route:String)=MessageDigest.getInstance("SHA-256").digest((device+route).toByteArray()).joinToString(""){"%02x".format(it)}
 @Synchronized fun success(route:String,mime:String,name:String){if(mime.isBlank()||name.isBlank())return;entries.put(key(route),JSONObject().put("mime",mime).put("name",name).put("at",System.currentTimeMillis()));save()}
 @Synchronized fun preferred(route:String,mime:String):String?=entries.optJSONObject(key(route))?.takeIf{it.optString("mime")==mime&&System.currentTimeMillis()-it.optLong("at") in 0..604800000L}?.optString("name")?.takeIf{it.isNotBlank()}
 @Synchronized fun hasSuccess(route:String):Boolean=entries.optJSONObject(key(route))?.let{System.currentTimeMillis()-it.optLong("at") in 0..604800000L}==true
 private val failed=mutableMapOf<String,Long>()
 @Synchronized fun failure(route:String,name:String){if(name.isNotBlank()){failed[key(route)+"\n"+name]=System.currentTimeMillis();while(failed.size>64)failed.remove(failed.keys.first())}}
 @Synchronized fun discouraged(route:String):Set<String>{val now=System.currentTimeMillis();failed.entries.removeAll{now-it.value !in 0..600000L};val prefix=key(route)+"\n";return failed.keys.filter{it.startsWith(prefix)}.map{it.removePrefix(prefix)}.toSet()}
 private fun save(){while(entries.length()>64){val oldest=entries.keys().asSequence().minByOrNull{entries.optJSONObject(it)?.optLong("at")?:0}?:break;entries.remove(oldest)};prefs.edit().putString("entries",entries.toString()).apply()}
}
