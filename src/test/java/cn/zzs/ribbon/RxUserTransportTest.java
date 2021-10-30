package cn.zzs.ribbon;


import java.nio.charset.Charset;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.IClientConfig;
import com.netflix.ribbon.transport.netty.RibbonTransport;
import com.netflix.ribbon.transport.netty.http.LoadBalancingHttpClient;

import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Func1;

public class RxUserTransportTest {
    
    private static final Logger LOG = LoggerFactory.getLogger(RxUserTransportTest.class);
    
    private final Subscriber<Object> subscriber = new Subscriber<Object>() {
        
        @Override
        public void onCompleted() {
            LOG.info("onCompleted");
        }
        
        @Override
        public void onError(Throwable e) {
            e.printStackTrace();
        }
        
        @Override
        public void onNext(Object t) {
            LOG.info("onNext:{}", t);
            if(t != null && t instanceof ByteBuf) {
                LOG.info(ByteBuf.class.cast(t).toString(Charset.defaultCharset()));
            }
        }
    };
    
    @Test
    public void test01() throws InterruptedException {
        
        @SuppressWarnings("deprecation")
        IClientConfig clientConfig = IClientConfig.Builder.newBuilder("UserService").build();
        clientConfig.set(CommonClientConfigKey.ListOfServers, "127.0.0.1:8080,127.0.0.1:8081,127.0.0.1:8082");
        clientConfig.set(CommonClientConfigKey.ConnectTimeout, 1000);
        clientConfig.set(CommonClientConfigKey.ReadTimeout, 2000);
        clientConfig.set(CommonClientConfigKey.MaxAutoRetries, 1);
        clientConfig.set(CommonClientConfigKey.MaxAutoRetriesNextServer, 2);
        clientConfig.set(CommonClientConfigKey.NFLoadBalancerRuleClassName, "cn.zzs.ribbon.MyLoadBalancerRule");
        LoadBalancingHttpClient<ByteBuf, ByteBuf> client = RibbonTransport.newHttpClient(clientConfig);
        
        HttpClientRequest<ByteBuf> httpRequest = HttpClientRequest.createGet(String.format("/user/getUserById?userId=%s", "1"))
                .withHeader("X-Platform-Version", "xyz")
                .withHeader("X-Auth-Token", "abc");
        
        client.submit(httpRequest).flatMap(new Func1<HttpClientResponse<ByteBuf>, Observable<ByteBuf>>() {
                    @Override
                    public Observable<ByteBuf> call(HttpClientResponse<ByteBuf> httpClientResponse) {
                        if (httpClientResponse.getStatus().code() / 100 != 2) {
                            return Observable.error(new RuntimeException(
                                    String.format("HTTP request failed (status code=%s)", httpClientResponse.getStatus())));
                        }
                        return httpClientResponse.getContent();
                    }
                })
                .subscribe(subscriber);
    
        Thread.sleep(10000);
    }
}
