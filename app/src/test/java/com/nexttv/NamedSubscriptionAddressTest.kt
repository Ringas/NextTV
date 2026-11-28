package com.nexttv
import org.junit.Assert.*
import org.junit.Test
import java.net.*
class NamedSubscriptionAddressTest {
 companion object { init { System.setProperty("sun.net.http.allowRestrictedHeaders", "true") } }
 @Test fun acceptsBothCommaStylesAndOptionalNames(){assertEquals(NamedSubscriptionAddress("家庭","http://example.test/live.m3u"),parseNamedSubscriptionAddress(" 家庭， http://example.test/live.m3u "));assertEquals("家庭,http://example.test/live.m3u",parseNamedSubscriptionAddress("家庭,http://example.test/live.m3u").input);assertEquals("",parseNamedSubscriptionAddress("，http://example.test/live.m3u").name);assertEquals("",parseNamedSubscriptionAddress("http://example.test/live.m3u").name)}
 @Test fun commasInsideUrlAreNotNameDelimiters(){val url="http://example.test/a,b.m3u?token=x,y";assertEquals(url,parseNamedSubscriptionAddress(url).url);assertEquals(url,parseNamedSubscriptionAddress("家庭，$url").url)}
 @Test fun validatesActualAddressAndRejectsMalformedNamedInput(){for(s in listOf("家庭,not-a-url","家庭，file:///a","家庭","家庭,http://","家庭,http://example.test/a\nb"))assertTrue(s,runCatching{parseNamedSubscriptionAddress(s)}.isFailure)}
 @Test fun phoneNamedInputUsesTextFieldAndPreservesName(){var received="";SubscriptionInputServer(InetAddress.getLoopbackAddress(),namedInput=true){received=it}.use{s->val form=URL(s.url).readText();assertTrue(form.contains("type=\"text\""));assertTrue(form.contains("名称可省略"));val c=URL(s.url).openConnection()as HttpURLConnection;c.requestMethod="POST";c.doOutput=true;c.setRequestProperty("Content-Type","application/x-www-form-urlencoded");c.setRequestProperty("Origin","http://"+URL(s.url).authority);c.outputStream.use{it.write(("url="+URLEncoder.encode("家庭，http://example.test/live.m3u","UTF-8")).toByteArray())};assertEquals(200,c.responseCode);c.inputStream.close();c.disconnect();val end=System.currentTimeMillis()+1000;while(received.isBlank()&&System.currentTimeMillis()<end)Thread.yield();assertEquals("家庭,http://example.test/live.m3u",received)}}
}
