package com.yupi.usercenter.service;

import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@SpringBootTest
public class RedissionTest {

    @Resource
    private RedissonClient redissonClient;

    @Test
    void testRedisson() {

        List<String> keys = new ArrayList<>();
//        keys.add("name")


    }
}
