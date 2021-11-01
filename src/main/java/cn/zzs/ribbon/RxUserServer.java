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

package cn.zzs.ribbon;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;

import cn.zzs.ribbon.entity.User;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.logging.LogLevel;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.pipeline.PipelineConfigurators;
import io.reactivex.netty.protocol.http.server.HttpServer;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import io.reactivex.netty.protocol.http.server.RequestHandler;
import rx.Observable;

public class RxUserServer {


    private final int port;

    final List<User> users = Arrays.asList(
            new User("1", "zzs001", 18), 
            new User("2", "zzs002", 18),
            new User("3", "zzs003", 18),
            new User("4", "zzf001", 18), 
            new User("5", "zzf002", 18),
            new User("6", "zzf003", 18)
            );

    public RxUserServer(int port) {
        this.port = port;
    }


    public HttpServer<ByteBuf, ByteBuf> createServer() {
        HttpServer<ByteBuf, ByteBuf> server = RxNetty.newHttpServerBuilder(port, new RequestHandler<ByteBuf, ByteBuf>() {
            @Override
            public Observable<Void> handle(HttpServerRequest<ByteBuf> request, final HttpServerResponse<ByteBuf> response) {
                if (request.getPath().contains("/user/getUserById")) {
                    return handleGetUserById(request, response);
                }
                response.setStatus(HttpResponseStatus.NOT_FOUND);
                return response.close();
            }
        }).pipelineConfigurator(PipelineConfigurators.<ByteBuf, ByteBuf>httpServerConfigurator()).enableWireLogging(LogLevel.ERROR).build();

        System.out.println("RxUser server started...");
        return server;
    }

    private Observable<Void> handleGetUserById(HttpServerRequest<ByteBuf> request, final HttpServerResponse<ByteBuf> response) {
        
        List<String> userId = request.getQueryParameters().get("userId");
        if (userId.isEmpty()) {
            response.setStatus(HttpResponseStatus.BAD_REQUEST);
            return response.close();
        }

        StringBuilder builder = new StringBuilder();
        for (User user : users) {
            if (user.getId().equals(userId.get(0))) {
                builder.append(user);
                break;
            }
        }

        ByteBuf byteBuf = UnpooledByteBufAllocator.DEFAULT.buffer();
        byteBuf.writeBytes(builder.toString().getBytes(Charset.defaultCharset()));

        response.write(byteBuf);
        return response.close();
    }


    public static void main(final String[] args) {
        new RxUserServer(8080).createServer().startAndWait();
    }

}
