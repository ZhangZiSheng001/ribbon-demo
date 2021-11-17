/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cn.zzs.ribbon.handler;

import com.netflix.hystrix.HystrixInvokableInfo;
import com.netflix.ribbon.http.HttpResourceObservableCommand;
import com.netflix.ribbon.hystrix.FallbackHandler;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.UnpooledByteBufAllocator;
import rx.Observable;

import java.nio.charset.Charset;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Tomasz Bak
 */
public class UserServiceFallbackHandler implements FallbackHandler<ByteBuf> {
    private static final Logger LOG = LoggerFactory.getLogger(UserServiceFallbackHandler.class);
    
    @Override
    public Observable<ByteBuf> getFallback(HystrixInvokableInfo<?> hystrixInfo, Map<String, Object> requestProperties) {
        LOG.info("断路器是否开启:{},isResponseShortCircuited={},isResponseTimedOut={},isFailedExecution={},isResponseRejected={}",
                hystrixInfo.isCircuitBreakerOpen(),
                hystrixInfo.isResponseShortCircuited(),
                hystrixInfo.isResponseTimedOut(),
                hystrixInfo.isFailedExecution(),
                hystrixInfo.isResponseRejected()
                );
        
        byte[] bytes ="fall back or timeout".getBytes(Charset.defaultCharset());
        ByteBuf byteBuf = UnpooledByteBufAllocator.DEFAULT.buffer(bytes.length);
        byteBuf.writeBytes(bytes);
        return Observable.just(byteBuf);
    }
}
