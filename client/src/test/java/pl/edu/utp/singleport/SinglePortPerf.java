package pl.edu.utp.singleport;

import static io.netty.buffer.Unpooled.wrappedBuffer;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpMethod.PUT;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static org.junit.Assert.assertEquals;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import org.apache.commons.lang3.StringUtils;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.rest.http2.NettyHttpClient;
import org.junit.Test;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.util.CharsetUtil;

public class SinglePortPerf {

   private static final int MEASUREMENT_ITERATIONS_COUNT = 1;
   private static final int WARMUP_ITERATIONS_COUNT = 1;

   private static final String TRUSTSTORE_FILE_NAME = "server-keystore.jks";
   private static final URL TRUSTSTORE_URI = SinglePortTest.class.getResource("/" + TRUSTSTORE_FILE_NAME);
   private static final String TRUSTSTORE_IN_CLASSPATH = "classpath:" + TRUSTSTORE_FILE_NAME;
   private static final String TRUSTSTORE_PASSWORD = "secret";

   @Test
   public void performRouterBenchmark() throws Exception {
      Options opt = new OptionsBuilder()
            .include(this.getClass().getName() + ".*")
//            .mode(Mode.AverageTime)
//            .mode(Mode.SingleShotTime)
            .timeUnit(TimeUnit.MILLISECONDS)
            .warmupIterations(WARMUP_ITERATIONS_COUNT)
            .measurementIterations(MEASUREMENT_ITERATIONS_COUNT)
            .threads(1)
            .forks(1)
            .shouldFailOnError(true)
            .shouldDoGC(true)
            .build();

      new Runner(opt).run();
   }

   @State(Scope.Thread)
   public static class BenchmarkState {

      static final int MEASUREMENT_SIZE = 1;

      private final String HTTPS_ROUTE = "infinispan-app-https-myproject.127.0.0.1.nip.io";
      private final String HTTP_ROUTE = "infinispan-app-http-myproject.127.0.0.1.nip.io";

      RemoteCache<String, String> tlsAlpnCache;
      RemoteCache<String, String> httpUpgradeCache;

      NettyHttpClient httpUpgradeHttp2Client;
      NettyHttpClient tlsAlpnHttp2Client;
      //HTTP/1.1 clients in Netty are single shot :((( We need to pool them.
      List<NettyHttpClient> http11Clients = new ArrayList<>(MEASUREMENT_SIZE);

      private List<String> String36ByteList = new ArrayList<>(MEASUREMENT_SIZE);
      private List<String> String360ByteList = new ArrayList<>(MEASUREMENT_SIZE);

      @Setup
      public void setup() throws Exception {
         RemoteCacheManager tlsAlpnRemoteCacheManager = getTlsAlpnRemoteCacheManager();
         tlsAlpnCache = tlsAlpnRemoteCacheManager.getCache("default");
         tlsAlpnCache.clear();

         RemoteCacheManager httpUpgradeRemoteCacheManager = getHttpUpgradeRemoteCacheManager();
         httpUpgradeCache = httpUpgradeRemoteCacheManager.getCache("default");
         httpUpgradeCache.clear();

         httpUpgradeHttp2Client = NettyHttpClient.newHttp2ClientWithHttp11Upgrade();
         httpUpgradeHttp2Client.start(HTTP_ROUTE, 80);

         for (int i = 0; i < MEASUREMENT_SIZE; ++i) {
            NettyHttpClient http11Client = NettyHttpClient.newHttp11Client();
            http11Client.start(HTTP_ROUTE, 80);
            http11Clients.add(http11Client);
         }

         tlsAlpnHttp2Client = NettyHttpClient
               .newHttp2ClientWithALPN(TRUSTSTORE_URI.getFile(), TRUSTSTORE_PASSWORD, HTTPS_ROUTE);
         tlsAlpnHttp2Client.start(HTTPS_ROUTE, 443);

         for (int i = 0; i < MEASUREMENT_SIZE; ++i) {
            String val = UUID.randomUUID().toString();
            String36ByteList.add(val);
            String360ByteList.add(StringUtils.repeat(val, 10));
         }

      }

      private RemoteCacheManager getHttpUpgradeRemoteCacheManager() {
         ConfigurationBuilder httpUpgradeBuilder = new ConfigurationBuilder();
         httpUpgradeBuilder.addServer().host(HTTP_ROUTE).port(80);
         httpUpgradeBuilder.singlePort(true);
         return new RemoteCacheManager(httpUpgradeBuilder.build());
      }

      private RemoteCacheManager getTlsAlpnRemoteCacheManager() {
         ConfigurationBuilder tlsAlpnConfigBuilder = new ConfigurationBuilder();
         tlsAlpnConfigBuilder.addServer().host(HTTPS_ROUTE).port(443);
         tlsAlpnConfigBuilder.security().ssl().enable()
               .trustStoreFileName(TRUSTSTORE_IN_CLASSPATH)
               .trustStorePassword(TRUSTSTORE_PASSWORD.toCharArray());
         tlsAlpnConfigBuilder.security().ssl().sniHostName(HTTPS_ROUTE);
         tlsAlpnConfigBuilder.singlePort(true);
         RemoteCacheManager remoteCacheManager = new RemoteCacheManager(tlsAlpnConfigBuilder.build());
         tlsAlpnCache = remoteCacheManager.getCache("default");
         return remoteCacheManager;
      }

      @TearDown
      public void tearDown() {
         tlsAlpnCache.getRemoteCacheManager().stop();
         httpUpgradeCache.getRemoteCacheManager().stop();

         httpUpgradeHttp2Client.stop();
         tlsAlpnHttp2Client.stop();

         for (int i = 0; i < MEASUREMENT_SIZE; ++i) {
            http11Clients.get(i).stop();
         }
         http11Clients.clear();
      }

      @Benchmark
      @BenchmarkMode(Mode.SingleShotTime)
      public void initTLSALPNHotRodConnection() throws Exception {
         RemoteCacheManager tlsAlpnRemoteCacheManager = getTlsAlpnRemoteCacheManager();
         tlsAlpnRemoteCacheManager.getCache("default");
         tlsAlpnRemoteCacheManager.close();
      }

      @Benchmark
      @BenchmarkMode(Mode.SingleShotTime)
      public void initTLSALPNHTTP2Connection() throws Exception {
         NettyHttpClient client = NettyHttpClient
               .newHttp2ClientWithALPN(TRUSTSTORE_URI.getFile(), TRUSTSTORE_PASSWORD, HTTPS_ROUTE);
         client.start(HTTPS_ROUTE, 443);
         client.stop();
      }

      @Benchmark
      @BenchmarkMode(Mode.SingleShotTime)
      public void initHTTPUpgradeHTTP2Connection() throws Exception {
         NettyHttpClient client = NettyHttpClient
               .newHttp2ClientWithHttp11Upgrade();
         client.start(HTTP_ROUTE, 80);
         client.stop();
      }

      @Benchmark
      @BenchmarkMode(Mode.SingleShotTime)
      public void initHTTP11Connection() throws Exception {
         NettyHttpClient client = NettyHttpClient
               .newHttp11Client();
         client.start(HTTP_ROUTE, 80);
         client.stop();
      }

      @Benchmark
      @BenchmarkMode(Mode.SingleShotTime)
      public void initHTTPUpgradeHotRodConnection() throws Exception {
         RemoteCacheManager tlsAlpnRemoteCacheManager = getTlsAlpnRemoteCacheManager();
         tlsAlpnRemoteCacheManager.getCache("default");
         tlsAlpnRemoteCacheManager.close();
      }

      @Benchmark
      @BenchmarkMode({Mode.AverageTime, Mode.SingleShotTime})
      @Measurement(iterations = MEASUREMENT_SIZE)
      public void put36ByteEntriesThroughHotRodAndTLSALPN() throws Exception {
         for (int i = 0; i < MEASUREMENT_SIZE; ++i) {
            tlsAlpnCache.put(String36ByteList.get(i), String36ByteList.get(i));
         }
      }

      @Benchmark
      @BenchmarkMode({Mode.AverageTime, Mode.SingleShotTime})
      @Measurement(iterations = MEASUREMENT_SIZE)
      public void putAndGet36ByteEntriesThroughHotRodAndHTTPUpgrade() throws Exception {
         for (int i = 0; i < MEASUREMENT_SIZE; ++i) {
            httpUpgradeCache.put(String36ByteList.get(i), String36ByteList.get(i));
            httpUpgradeCache.get(String36ByteList.get(i));
         }
      }

      @Benchmark
      @BenchmarkMode({Mode.AverageTime, Mode.SingleShotTime})
      @Measurement(iterations = MEASUREMENT_SIZE)
      public void put36ByteEntriesThroughHTTP2AndHTTPUpgrade() throws Exception {
         for (int i = 0; i < MEASUREMENT_SIZE; ++i) {
            FullHttpRequest putValueInCacheRequest = new DefaultFullHttpRequest(HTTP_1_1, PUT, "/rest/default/" + String36ByteList.get(i),
                  wrappedBuffer(String36ByteList.get(0).getBytes(CharsetUtil.UTF_8)));
            httpUpgradeHttp2Client.sendRequest(putValueInCacheRequest);
            while(httpUpgradeHttp2Client.getResponses().size() < i + 1) {
               LockSupport.parkNanos(100);
            }
         }
      }

      @Benchmark
      @BenchmarkMode({Mode.AverageTime, Mode.SingleShotTime})
      @Measurement(iterations = MEASUREMENT_SIZE)
      public void put36ByteEntriesThroughHTTP2AndTLSALPN() throws Exception {
         for (int i = 0; i < MEASUREMENT_SIZE; ++i) {
            FullHttpRequest putValueInCacheRequest = new DefaultFullHttpRequest(HTTP_1_1, PUT, "/rest/default/" + String36ByteList.get(i),
                  wrappedBuffer(String36ByteList.get(0).getBytes(CharsetUtil.UTF_8)));
            FullHttpRequest getValueFromCacheRequest = new DefaultFullHttpRequest(HTTP_1_1, GET, "/rest/default/" + String36ByteList.get(i));
            tlsAlpnHttp2Client.sendRequest(putValueInCacheRequest);
            //we always wait for the responses before continuing the loop.
            while(tlsAlpnHttp2Client.getResponses().size() < i + 1) {
               LockSupport.parkNanos(100);
            }
         }
      }

      @Benchmark
      @BenchmarkMode({Mode.AverageTime, Mode.SingleShotTime})
      @Measurement(iterations = MEASUREMENT_SIZE)
      public void put36ByteEntriesThroughHTTP11WithPooling() throws Exception {
         for (int i = 0; i < MEASUREMENT_SIZE; ++i) {
            FullHttpRequest putValueInCacheRequest = new DefaultFullHttpRequest(HTTP_1_1, PUT, "/rest/default/" + String36ByteList.get(i),
                  wrappedBuffer(String36ByteList.get(i).getBytes(CharsetUtil.UTF_8)));
            putValueInCacheRequest.headers().add("HOST", HTTP_ROUTE);
            http11Clients.get(i).sendRequest(putValueInCacheRequest);
            while(http11Clients.get(i).getResponses().size() < 1) {
               LockSupport.parkNanos(100);
            }
         }
      }

      @Benchmark
      @BenchmarkMode({Mode.AverageTime, Mode.SingleShotTime})
      @Measurement(iterations = MEASUREMENT_SIZE)
      public void put360ByteEntriesThroughHotRodAndTLSALPN() throws Exception {
         for (int i = 0; i < MEASUREMENT_SIZE; ++i) {
            tlsAlpnCache.put(String360ByteList.get(i), String36ByteList.get(i));
         }
      }

      @Benchmark
      @BenchmarkMode({Mode.AverageTime, Mode.SingleShotTime})
      @Measurement(iterations = MEASUREMENT_SIZE)
      public void put360ByteEntriesThroughHotRodAndHTTPUpgrade() throws Exception {
         for (int i = 0; i < MEASUREMENT_SIZE; ++i) {
            httpUpgradeCache.put(String360ByteList.get(i), String360ByteList.get(i));
         }
      }

      @Benchmark
      @BenchmarkMode({Mode.AverageTime, Mode.SingleShotTime})
      @Measurement(iterations = MEASUREMENT_SIZE)
      public void put360ByteEntriesThroughHTTP2AndHTTPUpgrade() throws Exception {
         for (int i = 0; i < MEASUREMENT_SIZE; ++i) {
            FullHttpRequest putValueInCacheRequest = new DefaultFullHttpRequest(HTTP_1_1, PUT, "/rest/default/" + String360ByteList.get(i),
                  wrappedBuffer(String36ByteList.get(0).getBytes(CharsetUtil.UTF_8)));
            httpUpgradeHttp2Client.sendRequest(putValueInCacheRequest);
            while(httpUpgradeHttp2Client.getResponses().size() < i + 1) {
               LockSupport.parkNanos(100);
            }
         }
      }

      @Benchmark
      @BenchmarkMode({Mode.AverageTime, Mode.SingleShotTime})
      @Measurement(iterations = MEASUREMENT_SIZE)
      public void put360ByteEntriesThroughHTTP2AndTLSALPN() throws Exception {
         for (int i = 0; i < MEASUREMENT_SIZE; ++i) {
            FullHttpRequest putValueInCacheRequest = new DefaultFullHttpRequest(HTTP_1_1, PUT, "/rest/default/" + String360ByteList.get(i),
                  wrappedBuffer(String360ByteList.get(0).getBytes(CharsetUtil.UTF_8)));
            tlsAlpnHttp2Client.sendRequest(putValueInCacheRequest);
            while(tlsAlpnHttp2Client.getResponses().size() < i + 1) {
               LockSupport.parkNanos(100);
            }
         }
      }

      @Benchmark
      @BenchmarkMode({Mode.AverageTime, Mode.SingleShotTime})
      @Measurement(iterations = MEASUREMENT_SIZE)
      public void put360ByteEntriesThroughHTTP11WithPooling() throws Exception {
         for (int i = 0; i < MEASUREMENT_SIZE; ++i) {
            FullHttpRequest putValueInCacheRequest = new DefaultFullHttpRequest(HTTP_1_1, PUT, "/rest/default/" + String360ByteList.get(i),
                  wrappedBuffer(String36ByteList.get(i).getBytes(CharsetUtil.UTF_8)));
            putValueInCacheRequest.headers().add("HOST", HTTP_ROUTE);
            http11Clients.get(i).sendRequest(putValueInCacheRequest);
            while(http11Clients.get(i).getResponses().size() < 1) {
               LockSupport.parkNanos(100);
            }
         }
      }

      @Benchmark
      @BenchmarkMode({Mode.AverageTime, Mode.SingleShotTime})
      @Measurement(iterations = MEASUREMENT_SIZE)
      public void put36ByteEntriesThroughHTTP11WithoutPooling() throws Exception {
         for (int i = 0; i < MEASUREMENT_SIZE; ++i) {
            NettyHttpClient client = NettyHttpClient.newHttp11Client();
            client.start(HTTP_ROUTE, 80);
            FullHttpRequest putValueInCacheRequest = new DefaultFullHttpRequest(HTTP_1_1, PUT, "/rest/default/" + String36ByteList.get(i),
                  wrappedBuffer(String36ByteList.get(i).getBytes(CharsetUtil.UTF_8)));
            putValueInCacheRequest.headers().add("HOST", HTTP_ROUTE);
            client.sendRequest(putValueInCacheRequest);
            while(client.getResponses().size() < 1) {
               LockSupport.parkNanos(100);
            }
            client.stop();
         }
      }

      @Benchmark
      @BenchmarkMode({Mode.AverageTime, Mode.SingleShotTime})
      @Measurement(iterations = MEASUREMENT_SIZE)
      public void put360ByteEntriesThroughHTTP11WithoutPooling() throws Exception {
         for (int i = 0; i < MEASUREMENT_SIZE; ++i) {
            NettyHttpClient client = NettyHttpClient.newHttp11Client();
            client.start(HTTP_ROUTE, 80);
            FullHttpRequest putValueInCacheRequest = new DefaultFullHttpRequest(HTTP_1_1, PUT, "/rest/default/" + String360ByteList.get(i),
                  wrappedBuffer(String360ByteList.get(i).getBytes(CharsetUtil.UTF_8)));
            putValueInCacheRequest.headers().add("HOST", HTTP_ROUTE);
            client.sendRequest(putValueInCacheRequest);
            while(client.getResponses().size() < 1) {
               LockSupport.parkNanos(100);
            }
            client.stop();
         }
      }
   }

}
