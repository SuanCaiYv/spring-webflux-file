package com.stackfarm.file.router;

import com.stackfarm.file.handler.FileHandler;
import com.stackfarm.file.handler.RemoteRedisHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * @author Mr.M
 */
@Configuration
public class GlobalRouter {

    @Autowired
    private FileHandler fileHandler;

    @Autowired
    private RemoteRedisHandler remoteRedisHandler;

    @Bean
    public RouterFunction<ServerResponse> routerFunction() {
        RouterFunction<ServerResponse> routerFunction = RouterFunctions.route()
                .POST("/getValue", RequestPredicates.all(), fileHandler::getValue)
                .POST("/upload/{uuid}", RequestPredicates.all(), fileHandler::upload)
                .GET("/download/{uuid}", RequestPredicates.all(), fileHandler::download)
                .DELETE("/delete/{uuid}", RequestPredicates.all(), fileHandler::delete)
                .POST("/filepath", RequestPredicates.all(), remoteRedisHandler::setUpload)
                .GET("/filepath", RequestPredicates.all(), remoteRedisHandler::setDownload)
                .DELETE("/filepath", RequestPredicates.all(), remoteRedisHandler::setDelete)
                .DELETE("/cache/all", RequestPredicates.all(), remoteRedisHandler::flushAll)
                .build();
        return routerFunction;
    }
}
