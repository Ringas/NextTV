// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.tv

internal const val CHANNEL_DIGIT_WAIT_MS=1600L
internal fun channelDigitCapacity(maxNumber:Int)=maxNumber.coerceAtLeast(0).toString().length.coerceAtLeast(3)
internal fun appendChannelDigit(value:String,digit:Int,capacity:Int):String{
 require(digit in 0..9)
 return (value+digit).takeLast(capacity.coerceAtLeast(1))
}
