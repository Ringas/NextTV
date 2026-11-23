// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** DateFormat is mutable: reuse per thread, and invalidate after locale/time-zone changes. */
internal object ProgrammeTimeLabels {
 private data class Formats(val locale:Locale,val zone:String,val time:SimpleDateFormat)
 private val local=ThreadLocal<Formats>()
 private fun formats():Formats {
  val locale=Locale.getDefault();val zone=TimeZone.getDefault()
  val saved=local.get()
  if(saved!=null&&saved.locale==locale&&saved.zone==zone.id)return saved
  return Formats(locale,zone.id,SimpleDateFormat("HH:mm",locale).apply{timeZone=zone}).also{local.set(it)}
 }
 fun time(millis:Long)=formats().time.format(Date(millis))
}
