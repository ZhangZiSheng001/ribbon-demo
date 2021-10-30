# 什么是Ribbon

之前分析了[如何使用原生的Feign](https://www.cnblogs.com/ZhangZiSheng001/p/14989165.html)，今天我们来研究 Netflix 团队开发的另外一个类库--Ribbon。
Ribbon 和 Feign 有很多相似的地方，首先，它们本质上都是 HTTP client，其次，它们都具备重试、集成断路器等功能。最大的区别在于，Ribbon 内置了一个负载均衡器，而 Feign 没有。

本文将介绍如何使用原生的 Ribbon，注意是原生的，而不是被 Spring 层层封装的 Ribbon。

# 为什么要使用Ribbon

这里我们需要回答两个问题：

1. 为什么要使用 HTTP client？
2. 为什么要在 HTTP client 里内置负载均衡器？

其中，第一个问题在[如何使用原生的Feign](https://www.cnblogs.com/ZhangZiSheng001/p/14989165.html)中已经讲过，这里就不啰嗦了，我们直接看第二个问题。

我们知道，Apache HTTP client、Feign 并没有内置负载均衡器，也就是说，HTTP client 并不一定要内置负载均衡器，那为什么 Ribbon 要搞特殊呢？

其实，我们可以想想，**Ribbon 更多地被用在内部调用，而这种场景有一个比较大的特点--目标服务为集群部署**。通常情况下，在调用目标服务时，我们希望请求尽可能平均地分发到每个实例。通过内置的负载均衡器，Ribbon 可以很好地满足要求，而 Apache HTTP client、Feign 就无法做到。

所以，在 HTTP client 里内置负载均衡器是为了能够在目标服务为集群部署时提供负载均衡支持。

![zzs_ribbon_003](https://img2020.cnblogs.com/blog/1731892/202110/1731892-20211030104641227-1976089618.png)

有的人可能会说，你单独部署一台负载均衡器就行了嘛，搞那么复杂干嘛。当然，你可以这么做。但是你要考虑很重要的一点，mid-tier services 的请求量要远大于 edge services，所以你需要一台性能极高的负载均衡器。从这个角度来说，Ribbon 的方案帮你省下了独立部署负载均衡器的开销。

![zzs_ribbon_002](https://img2020.cnblogs.com/blog/1731892/202110/1731892-20211030104700284-1493373663.png)

# 如何使用Ribbon

项目中我用 RxNettty 写了一个简单的 HTTP 接口（见`cn.zzs.ribbon.RxUserServer`）供后面的例子调用，这个接口运行在本机的 8080、8081、8082 接口，用来模拟三台不同的实例。所以，如果你想要测试项目中的例子，要先把这三台实例先启动好。

```
http://127.0.0.1:8080/user/getUserById?userId={userId}
request:userId=1
response:User [id=1, name=zzs001, age=18]
```

这里提醒一下，Ribbon 的 API 用到了很多 RxJava 代码，如果之前没接触过，最好先了解下。

## 项目环境

os：win 10

jdk：1.8.0_231

maven：3.6.3

IDE：Spring Tool Suite 4.6.1.RELEASE

Ribbon：2.7.17

## 作为HTTP client的用法

和 Feign 一样，Ribbon 支持使用注解方式定义 HTTP 接口，除此之外，Ribbon 还支持使用`HttpRequestTemplate`、`HttpClientRequest`等方式定义，这部分的例子我也提供了，感兴趣可以移步项目源码。

服务实例的列表通过`ConfigurationManager`设置。当你看到`ConfigurationManager`时，会不会觉得很熟悉呢？我们之前在[Eureka详解系列(三)--探索Eureka强大的配置体系](https://www.cnblogs.com/ZhangZiSheng001/p/14374005.html)中详细介绍过，没错，Ribbon 用的还是这套配置体系。需要强调下，**Netflix 团队开发的这套配置体系提供了动态配置支持（当然，你要会用才行），正是基于这一点，集成了 eureka 的应用才能够实现服务实例的动态调整**。

```java
// 使用注解定义HTTP API
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
    @Test
    public void testBase() throws InterruptedException {
        // 指定服务实例的地址
        // key：服务+“.ribbon.”+配置项名称（见com.netflix.client.config.CommonClientConfigKey）
        ConfigurationManager.getConfigInstance().setProperty(
                "UserService.ribbon.listOfServers", "127.0.0.1:8080,127.0.0.1:8081,127.0.0.1:8082");
        
        UserService userService = Ribbon.from(UserService.class);
        
        userService.getUserById("1")
            .toObservable()
            .subscribe(new Subscriber<Object>() {
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
            });
        // 因为请求HTTP接口是异步的，这里要让测试主线程先睡一会
        Thread.sleep(10000);
    }
}
```

## 默认的负载均衡规则

为了观察多次请求在三台实例的分配情况，现在我们更改下代码，试着发起 6 次请求。

```java
    @Test
    public void test01() throws InterruptedException {
        ConfigurationManager.getConfigInstance().setProperty(
                "UserService.ribbon.listOfServers", "127.0.0.1:8080,127.0.0.1:8081,127.0.0.1:8082");
            
        UserService userService = Ribbon.from(UserService.class);
        // 发起多次请求
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
```

运行测试，可以看到，6 次请求被平均地分配到了 3 台实例。

![zzs_ribbon_004](https://img2020.cnblogs.com/blog/1731892/202110/1731892-20211030104719918-1578459686.png)

在日志中，可以看到了默认的负载均衡规则。

![zzs_ribbon_005](https://img2020.cnblogs.com/blog/1731892/202110/1731892-20211030104730617-1447876605.png)

通过源码可以看到，这个默认的规则本质上采用的是轮询策略`RoundRobinRule`。除此之外，Ribbon 还定义了`RandomRule`、`RetryRule`等规则供我们选择。

```java
public class AvailabilityFilteringRule {
    RoundRobinRule roundRobinRule = new RoundRobinRule();
}
```

## 自定义负载均衡规则

自定义负载均衡规则需要继承`com.netflix.loadbalancer.AbstractLoadBalancerRule`，并实现 choose 方法。这里我定义的规则是：不管有多少实例，默认访问第一台。

```java
public class MyLoadBalancerRule extends AbstractLoadBalancerRule {
    @Override
    public Server choose(Object key) {
        
        ILoadBalancer lb = getLoadBalancer();
        
        List<Server> allServers = lb.getAllServers();
        
        return allServers.stream().findFirst().orElse(null);
    }
}
```

接着，只需要通过`ConfigurationManager`配置自定义规则就行。

```java
    @Test
    public void test01() throws InterruptedException {
        ConfigurationManager.getConfigInstance().setProperty(
                "UserService.ribbon.listOfServers", "127.0.0.1:8080,127.0.0.1:8081,127.0.0.1:8082");
        // 配置自定义规则
        ConfigurationManager.getConfigInstance().setProperty(
                "UserService.ribbon.NFLoadBalancerRuleClassName", "cn.zzs.ribbon.MyLoadBalancerRule");
            
        UserService userService = Ribbon.from(UserService.class);
        
        Observable<ByteBuf>[] requestList = new Observable[]{
                userService.getUserById("1").toObservable(),
                userService.getUserById("2").toObservable(),
                userService.getUserById("3").toObservable(),
                userService.getUserById("1").toObservable(),
                userService.getUserById("2").toObservable(),
                userService.getUserById("3").toObservable()
        };
        Observable.concat(Observable.from(requestList))
                .subscribe(subscriber);
        Thread.sleep(10000);
    }
```

运行测试，可以看到，所有请求都被分配到了第一台实例。自定义负载均衡规则生效。

![zzs_ribbon_006](https://img2020.cnblogs.com/blog/1731892/202110/1731892-20211030104747810-1135715627.png)

# 结语

以上，基本讲完 Ribbon 的使用方法，其实 Ribbon 还有其他可以扩展的东西，例如，断路器、重试等等。感兴趣的话，可以自行分析。

最后，感谢阅读。

# 参考资料

[ribbon github](https://github.com/Netflix/ribbon)

> 相关源码请移步：https://github.com/ZhangZiSheng001/ribbon-demo

> 本文为原创文章，转载请附上原文出处链接：https://www.cnblogs.com/ZhangZiSheng001/p/15484505.html