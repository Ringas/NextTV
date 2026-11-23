// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.data

/** One parser per import: no formatter, regular expression or timezone allocation per programme. */
internal class XmltvTimeParser {
    private val calendar=java.util.GregorianCalendar(java.util.TimeZone.getTimeZone("UTC")).apply { isLenient=false }
    private val localZone=java.util.TimeZone.getDefault()
    fun parse(raw:String?):Long? {
        if(raw==null||raw.length !in 14..64)return null
        val text=raw.trim()
        if(text.length<14)return null
        fun number(start:Int,count:Int):Int {
            var value=0
            for(i in start until start+count){val d=text[i]-'0';if(d !in 0..9)throw IllegalArgumentException();value=value*10+d}
            return value
        }
        return try {
            val zone=text.substring(14).trim()
            val offset=when {
                zone.isEmpty()->null
                zone=="Z"||zone=="UTC"->0
                zone.length==5&&(zone[0]=='+'||zone[0]=='-')->{
                    if(zone.substring(1).any{it !in '0'..'9'})return null
                    val h=zone.substring(1,3).toInt();val m=zone.substring(3,5).toInt()
                    if(h>23||m>59)return null
                    (h*60+m)*60000*(if(zone[0]=='-')-1 else 1)
                }
                else->return null
            }
            calendar.timeZone=if(offset==null)localZone else UTC
            calendar.clear()
            calendar.set(number(0,4),number(4,2)-1,number(6,2),number(8,2),number(10,2),number(12,2))
            calendar.timeInMillis-(offset?:0)
        }catch(_:IllegalArgumentException){null}
    }
    companion object { private val UTC=java.util.TimeZone.getTimeZone("UTC") }
}
