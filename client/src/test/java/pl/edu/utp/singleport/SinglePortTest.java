package pl.edu.utp.singleport;

import static io.netty.buffer.Unpooled.wrappedBuffer;
import static io.netty.handler.codec.http.HttpMethod.POST;
import static io.netty.handler.codec.http.HttpMethod.PUT;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static org.junit.Assert.assertEquals;

import java.net.URL;
import java.util.Queue;

import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.rest.http2.NettyHttpClient;
import org.junit.Test;

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.CharsetUtil;

public class SinglePortTest {

   private static final String TRUSTSTORE_FILE_NAME = "server-keystore.jks";
   private static final URL TRUSTSTORE_URI = SinglePortTest.class.getResource("/" + TRUSTSTORE_FILE_NAME);
   private static final String TRUSTSTORE_IN_CLASSPATH = "classpath:" + TRUSTSTORE_FILE_NAME;
   private static final String TRUSTSTORE_PASSWORD = "secret";

   @Test
   public void upgradeToHotRodThroughALPN() {
      ConfigurationBuilder builder = new ConfigurationBuilder();
      builder.addServer().host("infinispan-app-https-myproject.127.0.0.1.nip.io").port(443);
      builder.security().ssl().enable()
            .trustStoreFileName(TRUSTSTORE_IN_CLASSPATH)
            .trustStorePassword(TRUSTSTORE_PASSWORD.toCharArray());
      builder.security().ssl().sniHostName("infinispan-app-https-myproject.127.0.0.1.nip.io");
      builder.singlePort(true);

      //when
      RemoteCacheManager remoteCacheManager = new RemoteCacheManager(builder.build());
      RemoteCache<String, String> cache = remoteCacheManager.getCache("default");
      cache.put("testHotRodSwitchThroughUpgradeHeader", "test");

      //then
      assertEquals("test", cache.get("testHotRodSwitchThroughUpgradeHeader"));
   }

   @Test
   public void upgradeToHotRodThroughUpgrade() {
      ConfigurationBuilder builder = new ConfigurationBuilder();
      builder.addServer().host("infinispan-app-http-myproject.127.0.0.1.nip.io").port(80);
      builder.singlePort(true);

      //when
      RemoteCacheManager remoteCacheManager = new RemoteCacheManager(builder.build());
      RemoteCache<String, String> cache = remoteCacheManager.getCache("default");
      cache.put("testHotRodSwitchThroughUpgradeHeader", "test");

      //then
      assertEquals("test", cache.get("testHotRodSwitchThroughUpgradeHeader"));
   }

   @Test
   public void upgradeToH2ThroughUpgrade() throws Exception {
      //given
      FullHttpRequest putValueInCacheRequest = new DefaultFullHttpRequest(HTTP_1_1, PUT, "/rest/default/upgradeToH2ThroughUpgrade",
            wrappedBuffer("test".getBytes(CharsetUtil.UTF_8)));
      putValueInCacheRequest.headers().add("Host", "infinispan-app-http-myproject.127.0.0.1.nip.io");

      NettyHttpClient client = NettyHttpClient
            .newHttp2ClientWithHttp11Upgrade();
      client.start("infinispan-app-http-myproject.127.0.0.1.nip.io", 80);

      //when
      client.sendRequest(putValueInCacheRequest);
      Queue<FullHttpResponse> responses = client.getResponses();

      //then
      assertEquals(1, responses.size());
      assertEquals(HttpResponseStatus.OK, responses.poll().status());
   }

   @Test
   public void upgradeToH2ThroughALPN() throws Exception {
      //given
      FullHttpRequest putValueInCacheRequest = new DefaultFullHttpRequest(HTTP_1_1, PUT, "/rest/default/upgradeToH2ThroughALPN",
            wrappedBuffer("test".getBytes(CharsetUtil.UTF_8)));

      NettyHttpClient client = NettyHttpClient
            .newHttp2ClientWithALPN(TRUSTSTORE_URI.getFile(), TRUSTSTORE_PASSWORD, "infinispan-app-https-myproject.127.0.0.1.nip.io");
      client.start("infinispan-app-https-myproject.127.0.0.1.nip.io", 443);

      //when
      client.sendRequest(putValueInCacheRequest);
      Queue<FullHttpResponse> responses = client.getResponses();

      //then
      assertEquals(1, responses.size());
      assertEquals(HttpResponseStatus.OK, responses.poll().status());
   }

}
