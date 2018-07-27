package pl.edu.utp.singleport;

import static org.junit.Assert.assertEquals;

import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.junit.Test;

public class SinglePortTest {

   private static final String DEFAULT_TRUSTSTORE_PATH = "classpath:server-keystore.jks";
   private static final String DEFAULT_TRUSTSTORE_PASSWORD = "secret";

   @Test
   public void upgradeThroughALPN() {
      ConfigurationBuilder builder = new ConfigurationBuilder();
      builder.addServer().host("infinispan-app-https-myproject.127.0.0.1.nip.io").port(443);
      builder.security().ssl().enable()
            .trustStoreFileName(DEFAULT_TRUSTSTORE_PATH)
            .trustStorePassword(DEFAULT_TRUSTSTORE_PASSWORD.toCharArray());
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
   public void upgradeThroughUpgrade() {
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

}
