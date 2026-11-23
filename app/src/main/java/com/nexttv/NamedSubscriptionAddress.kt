package com.nexttv

/** Only the first delimiter before an HTTP scheme separates a name. Commas inside URLs stay intact. */
data class NamedSubscriptionAddress(val name:String,val url:String){
 val input:String get()=if(name.isBlank())url else "$name,$url"
}
fun parseNamedSubscriptionAddress(value:String):NamedSubscriptionAddress {
 val input=value.trim()
 if(input.startsWith("http://")||input.startsWith("https://"))return NamedSubscriptionAddress("",validateSubscriptionAddress(input))
 val separator=input.indexOfFirst{it==','||it=='，'}
 require(separator>=0){"请输入订阅地址，或名称,订阅地址"}
 val name=input.substring(0,separator).trim()
 require(name.length<=256&&name.none{it.code<32}){"订阅名称无效"}
 return NamedSubscriptionAddress(name,validateSubscriptionAddress(input.substring(separator+1).trim()))
}
