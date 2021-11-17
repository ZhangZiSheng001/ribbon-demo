package cn.zzs.ribbon;


import java.nio.charset.Charset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.appinfo.MyDataCenterInstanceConfig;
import com.netflix.config.ConfigurationManager;
import com.netflix.discovery.DefaultEurekaClientConfig;
import com.netflix.discovery.DiscoveryManager;
import com.netflix.ribbon.Ribbon;
import com.netflix.ribbon.RibbonRequest;
import com.netflix.ribbon.proxy.annotation.ClientProperties;
import com.netflix.ribbon.proxy.annotation.ClientProperties.Property;
import com.netflix.ribbon.proxy.annotation.Http;
import com.netflix.ribbon.proxy.annotation.Http.Header;
import com.netflix.ribbon.proxy.annotation.Http.HttpMethod;
import com.netflix.ribbon.proxy.annotation.Hystrix;
import com.netflix.ribbon.proxy.annotation.TemplateName;
import com.netflix.ribbon.proxy.annotation.Var;

import cn.zzs.ribbon.handler.UserServiceFallbackHandler;
import io.netty.buffer.ByteBuf;
import rx.Observable;
import rx.Subscriber;

@ClientProperties(properties = {
        @Property(name="ReadTimeout", value="2000"),
        @Property(name="ConnectTimeout", value="1000"),
        @Property(name="MaxAutoRetries", value="0"),
        @Property(name="MaxAutoRetriesNextServer", value="0")
}, exportToArchaius = true)
interface UserService {
    
    @TemplateName("/user/getUserById")
    @Http(
            method = HttpMethod.GET,
            uri = "/user/getUserById?userId={userId}",
            headers = {
                    @Header(name = "X-Platform-Version", value = "xyz"),
                    @Header(name = "X-Auth-Token", value = "abc")
            })
    @Hystrix(
            //validator = UserServiceResponseValidator.class, 
            fallbackHandler = UserServiceFallbackHandler.class)
    RibbonRequest<ByteBuf> getUserById(@Var("userId") String userId);
}

public class RxUserProxyTest {
    
    private static final Logger LOG = LoggerFactory.getLogger(RxUserProxyTest.class);
    
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
            logByteBuf(t);
        }
    };
    
    @Test
    public void testLB() throws InterruptedException {
        
        ConfigurationManager.getConfigInstance().setProperty(
                "UserService.ribbon.listOfServers", "127.0.0.1:8080,127.0.0.1:8081,127.0.0.1:8082");
        ConfigurationManager.getConfigInstance().setProperty(
                "UserService.ribbon.NFLoadBalancerRuleClassName", "cn.zzs.ribbon.rule.MyLoadBalancerRule");
        
        UserService userService = Ribbon.from(UserService.class);
        
        @SuppressWarnings("unchecked")
        Observable<ByteBuf>[] requestList = new Observable[]{
                userService.getUserById("1").toObservable(),
                userService.getUserById("2").toObservable(),
                userService.getUserById("3").toObservable(),
                userService.getUserById("4").toObservable(),
                userService.getUserById("5").toObservable(),
                userService.getUserById("6").toObservable()
        };
        Observable.concat(Observable.from(requestList))
                .subscribe(subscriber);
        
        Thread.sleep(10000);
    }
    
    
    @Test
    public void testBase() throws InterruptedException {
        
        ConfigurationManager.getConfigInstance().setProperty(
                "UserService.ribbon.listOfServers", "127.0.0.1:8080,127.0.0.1:8081,127.0.0.1:8082");
        
        UserService userService = Ribbon.from(UserService.class);
        
        userService.getUserById("1")
            .toObservable()
            .subscribe(subscriber);
        
        Thread.sleep(10000);
    }
    
    
    @SuppressWarnings("deprecation")
    @Test
    public void testEureka() throws InterruptedException {
        // 指定实例列表从eureka获取
        ConfigurationManager.getConfigInstance().setProperty(
                "UserService.ribbon.NIWSServerListClassName", "com.netflix.niws.loadbalancer.DiscoveryEnabledNIWSServerList");
        ConfigurationManager.getConfigInstance().setProperty(
                "UserService.ribbon.DeploymentContextBasedVipAddresses", "UserService");
        
        // 初始化EurekaClient
        DiscoveryManager.getInstance().initComponent(new MyDataCenterInstanceConfig(), new DefaultEurekaClientConfig());
        
        UserService userService = Ribbon.from(UserService.class);
        userService.getUserById("1")
            .toObservable()
            .subscribe(subscriber);
        
        Thread.sleep(10000);
    }
    
    
    @Test
    public void testHystrix() throws InterruptedException, ExecutionException {
        
        ConfigurationManager.getConfigInstance().setProperty(
                "UserService.ribbon.listOfServers", "127.0.0.1:8080,127.0.0.1:8081,127.0.0.1:8082");
        // 下面两个配置是为了加快断路器触发
        ConfigurationManager.getConfigInstance().setProperty(
                "hystrix.command./user/getUserById.metrics.healthSnapshot.intervalInMilliseconds", "500");
        ConfigurationManager.getConfigInstance().setProperty(
                "hystrix.command./user/getUserById.circuitBreaker.requestVolumeThreshold", "5");
        
        int maxRequest = 1000;
        
        UserService userService = Ribbon.from(UserService.class);
        
        // 多次请求让断路器打开
        CountDownLatch countDownLatch = new CountDownLatch(maxRequest);
        for(int i = 0; i < maxRequest; i++) {
            final int temp = i;
            userService.getUserById("1").toObservable().subscribe(v -> {
                countDownLatch.countDown();
                LOG.info("{}   onNext:{}", temp, v);
                logByteBuf(v);
            });
        }
        countDownLatch.await();
        
        // 这时访问会直接走fall back
        /*for(int i = 0; i < 100; i++) {
            ByteBuf t = userService.getUserById("1").execute();
            logByteBuf(t);
        }*/
        
        // 正常的服务器还可以访问？？
        Thread.sleep(10000);
        for(int i = 0; i < 100; i++) {
            ByteBuf t = userService.getUserById("1").execute();
            logByteBuf(t);
        }
    }

    private void logByteBuf(Object t) {
        if(t != null && t instanceof ByteBuf) {
            LOG.info(ByteBuf.class.cast(t).toString(Charset.defaultCharset()));
        }
    }
}
