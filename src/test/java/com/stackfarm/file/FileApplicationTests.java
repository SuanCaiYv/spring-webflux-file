package com.stackfarm.file;

import com.stackfarm.file.redis.RedisOperation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class FileApplicationTests {

    @Autowired
    private RedisOperation redisOperation;

    @Test
    void contextLoads() {
        redisOperation.setValue("key", "123", 10*1000);
    }

}
