package cn.zzs.ribbon.rule;

import java.util.List;

import com.netflix.loadbalancer.AbstractLoadBalancerRule;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.Server;


/**
 * 自定义负载均衡器
 * @author zzs
 * @date 2021年10月29日 下午3:52:17
 */
public class MyLoadBalancerRule extends AbstractLoadBalancerRule {

    @Override
    public Server choose(Object key) {
        
        ILoadBalancer lb = getLoadBalancer();
        
        List<Server> allServers = lb.getAllServers();
        
        // 始终选择第一台服务器
        return allServers.stream().findFirst().orElse(null);
    }

}
