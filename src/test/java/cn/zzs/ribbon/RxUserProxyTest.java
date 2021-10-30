package cn.zzs.ribbon;


import java.nio.charset.Charset;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.config.ConfigurationManager;
import com.netflix.ribbon.Ribbon;
import com.netflix.ribbon.RibbonRequest;
import com.netflix.ribbon.proxy.annotation.ClientProperties;
import com.netflix.ribbon.proxy.annotation.ClientProperties.Property;
import com.netflix.ribbon.proxy.annotation.Http;
import com.netflix.ribbon.proxy.annotation.Http.Header;
import com.netflix.ribbon.proxy.annotation.Http.HttpMethod;
import com.netflix.ribbon.proxy.annotation.TemplateName;
import com.netflix.ribbon.proxy.annotation.Var;

import io.netty.buffer.ByteBuf;
import rx.Observable;
import rx.Subscriber;

@ClientProperties(properties = {
        @Property(name="ReadTimeout", value="2000"),
        @Property(name="ConnectTimeout", value="1000"),
        @Property(name="MaxAutoRetries", value="1"),
        @Property(name="MaxAutoRetriesNextServer", value="2")
}, exportToArchaius = true)
interface UserService {
    
    @TemplateName("getUserById")
    @Http(
            method = HttpMethod.GET,
            uri = "/user/getUserById?userId={userId}",
            headers = {
                    @Header(name = "X-Platform-Version", value = "xyz"),
                    @Header(name = "X-Auth-Token", value = "abc")
            })
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
            if(t != null && t instanceof ByteBuf) {
                LOG.info(ByteBuf.class.cast(t).toString(Charset.defaultCharset()));
            }
        }
    };
    
    @Test
    public void test01() throws InterruptedException {
        
        ConfigurationManager.getConfigInstance().setProperty(
                "UserService.ribbon.listOfServers", "127.0.0.1:8080,127.0.0.1:8081,127.0.0.1:8082");
        ConfigurationManager.getConfigInstance().setProperty(
                "UserService.ribbon.NFLoadBalancerRuleClassName", "cn.zzs.ribbon.MyLoadBalancerRule");
        
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
}
