package com.nexttv
import com.nexttv.core.data.M3uParser
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale
class StableIdEncodingTest {
 @Test fun directHexPreservesPersistedChannelIds(){
  for(source in listOf("", "家庭订阅", "provider-42"))for(name in listOf("CCTV1 综合", "  News\tHD ", "频道🍀"))for(tvg in listOf("", "CCTV-1", "Mixed ID")){
   val identity=source+"|"+if(tvg.isNotBlank())"id:"+tvg.trim().lowercase(Locale.ROOT)else"name:"+name.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"),"")
   val reference="v2-"+java.security.MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8)).take(16).joinToString(""){"%02x".format(it)}
   assertEquals(reference,M3uParser.stableChannelId(source,name,tvg))
  }
 }
}
