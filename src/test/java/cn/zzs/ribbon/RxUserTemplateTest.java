package cn.zzs.ribbon;

import static com.netflix.client.config.CommonClientConfigKey.NFLoadBalancerRuleClassName;

import java.nio.charset.Charset;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.config.ConfigurationManager;
import com.netflix.ribbon.ClientOptions;
import com.netflix.ribbon.Ribbon;
import com.netflix.ribbon.http.HttpRequestTemplate;
import com.netflix.ribbon.http.HttpResourceGroup;

import io.netty.buffer.ByteBuf;
import rx.Observable;
import rx.Subscriber;

public class RxUserTemplateTest {
    
    private static final Logger LOG = LoggerFactory.getLogger(RxUserTemplateTest.class);
    
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
        
        ConfigurationManager.getConfigInstance().setProperty(
                "UserService.ribbon." + NFLoadBalancerRuleClassName, "cn.zzs.ribbon.MyLoadBalancerRule");
        
        HttpResourceGroup httpResourceGroup = Ribbon.createHttpResourceGroup("UserService",
                ClientOptions.create()
                        .withMaxAutoRetries(1)
                        .withMaxAutoRetriesNextServer(2)
                        .withConnectTimeout(1000)
                        .withReadTimeout(2000)
                        .withConfigurationBasedServerList("127.0.0.1:8080,127.0.0.1:8081,127.0.0.1:8082"));
        
        @SuppressWarnings("unchecked")
        HttpRequestTemplate<ByteBuf> registerMovieTemplate = httpResourceGroup.newTemplateBuilder("getUserById", ByteBuf.class)
                .withMethod("GET")
                .withUriTemplate("/user/getUserById?userId={userId}")
                .withHeader("X-Platform-Version", "xyz")
                .withHeader("X-Auth-Token", "abc")
                .build();
        
        @SuppressWarnings("unchecked")
        Observable<ByteBuf>[] requestList = new Observable[]{
                registerMovieTemplate.requestBuilder()
                        .withRequestProperty("userId", "1")
                        .build().toObservable(),
                registerMovieTemplate.requestBuilder()
                        .withRequestProperty("userId", "2")
                        .build().toObservable(),
                registerMovieTemplate.requestBuilder()
                        .withRequestProperty("userId", "3")
                        .build().toObservable(),
                registerMovieTemplate.requestBuilder()
                        .withRequestProperty("userId", "4")
                        .build().toObservable(),
                registerMovieTemplate.requestBuilder()
                        .withRequestProperty("userId", "5")
                        .build().toObservable(),
                registerMovieTemplate.requestBuilder()
                        .withRequestProperty("userId", "6")
                        .build().toObservable()
        };
        
        
        Observable.concat(Observable.from(requestList))
            .subscribe(subscriber);
        Thread.sleep(10000);
        
    }
    
    
    @Test
    public void testBase() throws InterruptedException {
        
        HttpResourceGroup httpResourceGroup = Ribbon.createHttpResourceGroup("UserService",
                ClientOptions.create()
                        .withMaxAutoRetries(1)
                        .withMaxAutoRetriesNextServer(2)
                        .withConnectTimeout(1000)
                        .withReadTimeout(2000)
                        .withConfigurationBasedServerList("127.0.0.1:8080,127.0.0.1:8081,127.0.0.1:8082"));
        
        @SuppressWarnings("unchecked")
        HttpRequestTemplate<ByteBuf> registerMovieTemplate = httpResourceGroup.newTemplateBuilder("getUserById", ByteBuf.class)
                .withMethod("GET")
                .withUriTemplate("/user/getUserById?userId={userId}")
                .withHeader("X-Platform-Version", "xyz")
                .withHeader("X-Auth-Token", "abc")
                .build();
        
        registerMovieTemplate.requestBuilder()
            .withRequestProperty("userId", "1")
            .build().toObservable()
            .subscribe(subscriber);
        Thread.sleep(10000);
        
    }
}
