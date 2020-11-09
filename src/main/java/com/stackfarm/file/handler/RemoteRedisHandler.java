package com.stackfarm.file.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stackfarm.file.redis.RedisOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * @author Mr.M
 */
@Component
public class RemoteRedisHandler {

    @Autowired
    private RedisOperation redisOperation;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public Mono<ServerResponse> setUpload(ServerRequest serverRequest) {
        return setFilepath(serverRequest);
    }

    public Mono<ServerResponse> setDownload(ServerRequest serverRequest) {
        return setFilepath(serverRequest);
    }

    public Mono<ServerResponse> setDelete(ServerRequest serverRequest) {
        return setFilepath(serverRequest);
    }

    public Mono<ServerResponse> flushAll(ServerRequest serverRequest) {
        Map<String, Object> map = new HashMap<>();
        map.put("code", 200);
        map.put("msg", "");
        map.put("data", null);
        Mono<String> mono = Mono.fromCallable(() -> OBJECT_MAPPER.writeValueAsString(map));
        return ServerResponse
                .status(200)
                .contentType(MediaType.APPLICATION_JSON)
                .body(mono, String.class);
    }

    private Mono<ServerResponse> setFilepath(ServerRequest serverRequest) {
        Mono<MultiValueMap<String, String>> multiValueMapMono = serverRequest.formData();
        String uuid = UUID.randomUUID().toString();
        return multiValueMapMono.flatMap(stringStringMultiValueMap -> {
            String filepath = stringStringMultiValueMap.getFirst("filepath");
            assert filepath != null;
            return Mono.just(filepath);
        }).flatMap(filepath -> {
            redisOperation.setValue(uuid, filepath, 6 * 60 * 60 * 1000);
            Map<String, Object> map = new HashMap<>();
            map.put("code", 200);
            map.put("msg", "");
            map.put("data", uuid);
            Mono<String> mono = Mono.fromCallable(() -> OBJECT_MAPPER.writeValueAsString(map));
            return ServerResponse
                    .status(200)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(mono, String.class);
        });
    }
}
