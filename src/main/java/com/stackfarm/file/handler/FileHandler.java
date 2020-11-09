package com.stackfarm.file.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stackfarm.file.redis.RedisOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import com.sun.xml.messaging.saaj.packaging.mime.internet.MimeUtility;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.*;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * @author Mr.M
 */
@Component
public class FileHandler {

    @Autowired
    private RedisOperation redisOperation;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 测试用
     */
    public Mono<ServerResponse> getValue(ServerRequest serverRequest) {
        Mono<MultiValueMap<String, Part>> multiValueMapMono = serverRequest.multipartData();
        return multiValueMapMono.flatMap(multiValueMap -> {
            Flux<DataBuffer> dataBufferFlux = Objects.requireNonNull(multiValueMap.getFirst("key")).content();
            Flux<String> stringFlux = dataBufferFlux.flatMap(dataBuffer -> Flux.push(sink -> {
                try {
                    sink.next(new String(dataBuffer.asInputStream().readAllBytes()));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                sink.complete();
            }));
            return ServerResponse.ok().body(stringFlux, String.class);
        });
    }

    /**
     * 文件上传
     */
    public Mono<ServerResponse> upload(ServerRequest serverRequest) {
        String uuid = serverRequest.pathVariable("uuid");
        String value = redisOperation.getValue(uuid);
        Mono<MultiValueMap<String, Part>> multiValueMapMono = serverRequest.multipartData();
        return multiValueMapMono.flatMap(multiValueMap -> {
            // 获取文件部分
            List<Part> fileParts = multiValueMap.get("file");
            // 获取设置属性部分
            String[] values = value.split("&");
            if (values.length != fileParts.size()) {
                return error(HttpStatus.BAD_REQUEST.value(), "文件数和请求数不一致");
            }
            // 因为顺序问题，所以不能用forEach
            for (String val : values) {
                if (!val.contains("direname=") || !val.contains("iscover=")) {
                    return error(HttpStatus.BAD_REQUEST.value(), "文件属性设置缺少");
                }
            }
            // 在新的线程里处理
            Schedulers.boundedElastic().schedule(() -> {
                int i = 0;
                Map<String, String> map = new HashMap<>();
                for (Part part : fileParts) {
                    FilePart filePart = (FilePart) part;
                    // 获取全部属性
                    String[] strings = values[i].split(";");
                    for (String str : strings) {
                        String[] strs = str.split("=");
                        map.put(strs[0], strs[1]);
                    }
                    // 文件名，如果为空就是用原文件名
                    String fileName = map.get("filename");
                    fileName = fileName == null ? filePart.filename() : fileName;
                    // 目录名，不可为空
                    String dirName = map.get("direname");
                    // 是否覆盖原文件，不可为空
                    boolean isCover = Boolean.parseBoolean(map.get("iscover"));
                    // 获取当前文件的Flux流
                    Flux<DataBuffer> dataBufferFlux = filePart.content();
                    Path tempPath = Paths.get(dirName + File.separatorChar + fileName);
                    Path file = null;
                    if (Files.exists(tempPath)) {
                        if (isCover) {
                            try {
                                Files.delete(tempPath);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            file = forceCreateFile(dirName + File.separatorChar + fileName);
                        } else {
                            file = forceCreateFile(dirName + File.separatorChar + UUID.randomUUID().toString() + fileName);
                        }
                    } else {
                        file = forceCreateFile(dirName + File.separatorChar + fileName);
                    }
                    try {
                        OutputStream outputStream = Files.newOutputStream(file);
                        dataBufferFlux.subscribe(dataBuffer -> {
                            try {
                                outputStream.write(dataBuffer.asInputStream().readAllBytes());
                            } catch (IOException e) {
                                System.out.println(e.getMessage());
                            }
                        });
                    } catch (IOException e) {
                        System.out.println(e.getMessage());
                    }
                    ++ i;
                    map.clear();
                }
            });
            return ok();
        });
    }

    /**
     * 文件下载
     */
    public Mono<ServerResponse> download(ServerRequest serverRequest) {
        String uuid = serverRequest.pathVariable("uuid");
        String filePath = redisOperation.getValue(uuid);
        if (filePath == null || "null".equals(filePath)) {
            return error(HttpStatus.BAD_REQUEST.value(), "所请求的文件不存在");
        }
        Path file = Paths.get(filePath);
        if (!Files.exists(file)) {
            return error(HttpStatus.BAD_REQUEST.value(), "所请求的文件不存在");
        }
        // 获取文件名
        String filename = filePath.substring(filePath.lastIndexOf(File.separatorChar) + 1);
        // 向Flux写入数据
        Flux<ByteBuffer> flux = Flux.push(byteBufferFluxSink -> {
            try {
                InputStream inputStream = Files.newInputStream(file);
                byte[] bytes = new byte[byteSize()];
                int size = inputStream.read(bytes);
                while (size != -1) {
                    byteBufferFluxSink.next(ByteBuffer.wrap(bytes, 0, size));
                    size = inputStream.read(bytes);
                }
                byteBufferFluxSink.complete();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        // 设置新的文件名
        Mono<String> filenameMono = Mono.from(Flux.push(fluxSink -> {
            fluxSink.next(adaptBrower(serverRequest, filename));
            fluxSink.complete();
        }));
        // 返回响应
        return filenameMono.flatMap(fname -> ServerResponse
                .ok()
                .header("Content-Disposition", "attachment;" + fname)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(flux, ByteBuffer.class));
    }

    /**
     * 文件删除
     */
    public Mono<ServerResponse> delete(ServerRequest serverRequest) {
        String uuid = serverRequest.pathVariable("uuid");
        String filePath = redisOperation.getValue(uuid);
        if (filePath == null || "null".equals(filePath)) {
            return error(HttpStatus.BAD_REQUEST.value(), "文件路径为空");
        }
        Path file = Paths.get(filePath);
        if (!Files.exists(file)) {
            return error(HttpStatus.BAD_REQUEST.value(), "文件不存在");
        }
        Mono<String> stringMono = Mono.from(Flux.push(stringFluxSink -> {
            try {
                Files.delete(file);
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }
            Map<String, Object> map = new HashMap<>();
            map.put("code", 200);
            map.put("msg", "");
            map.put("data", null);
            try {
                stringFluxSink.next(OBJECT_MAPPER.writeValueAsString(map));
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
            stringFluxSink.complete();
        }));
        return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(stringMono, String.class);
    }

    private Mono<ServerResponse> error(int statusCode, String msg) {
        Map<String, Object> map = new HashMap<>();
        map.put("code", statusCode);
        map.put("msg", msg);
        map.put("data", null);
        Mono<String> mono = Mono.fromCallable(() -> OBJECT_MAPPER.writeValueAsString(map));
        return ServerResponse
                .status(statusCode)
                .contentType(MediaType.APPLICATION_JSON)
                .body(mono, String.class);
    }

    private Mono<ServerResponse> ok() {
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

    /**
     * 强制创建文件
     * 阻塞方法，注意合理调用
     * @return 已创建的文件
     */
    private Path forceCreateFile(String path) {
        Path dir = Paths.get(path.substring(0, path.lastIndexOf(File.separatorChar)));
        Path file = Paths.get(path);
        if (!Files.exists(dir)) {
            try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (!Files.exists(file)) {
            try {
                Files.createFile(file);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return file;
    }

    /**
     * 设置下载文件时的缓冲区大小，默认为总可用内存的1/5
     * @return 实际的缓冲区大小
     */
    private int byteSize() {
        long memorySize = Runtime.getRuntime().freeMemory();
        return (int) ((memorySize / 5) / 1024) * 1024;
    }

    /**
     * 浏览器命名适配
     * 阻塞方法，注意调用时的处理
     * @return 新文件名
     */
    private String adaptBrower(ServerRequest serverRequest, String oldFileName) {
        String userAgent = serverRequest.headers().asHttpHeaders().getFirst("User-Agent");
        String rtn = "";
        String newFilename = URLEncoder.encode(oldFileName, StandardCharsets.UTF_8);
        // 如果没有UA，则默认使用IE的方式进行编码，因为毕竟IE还是占多数的
        rtn = "filename=\"" + newFilename + "\"";
        if (userAgent != null) {
            userAgent = userAgent.toLowerCase();
            // IE浏览器，只能采用URLEncoder编码
            if (userAgent.contains("msie")) {
                rtn = "filename=\"" + newFilename + "\"";
            }
            // Opera浏览器只能采用filename*
            else if (userAgent.contains("opera")) {
                rtn = "filename*=UTF-8''" + newFilename;
            }
            // Safari浏览器，只能采用ISO编码的中文输出
            else if (userAgent.contains("safari")) {
                try {
                    rtn = "filename=\"" + new String(oldFileName.getBytes(StandardCharsets.UTF_8), "ISO8859-1") + "\"";
                } catch (UnsupportedEncodingException e) {
                    System.out.println(e.getMessage());
                }
            }
            // Chrome浏览器，只能采用MimeUtility编码或ISO编码的中文输出
            else if (userAgent.contains("applewebkit")) {
                try {
                    newFilename = MimeUtility.encodeText(oldFileName, "UTF8", "B");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
                rtn = "filename=\"" + newFilename + "\"";
            }
            // FireFox浏览器，可以使用MimeUtility或filename*或ISO编码的中文输出
            else if (userAgent.contains("mozilla")) {
                rtn = "filename*=UTF-8''" + newFilename;
            }
            oldFileName = rtn;
        }
        return oldFileName;
    }
}
