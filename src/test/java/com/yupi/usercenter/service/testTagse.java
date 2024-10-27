package com.yupi.usercenter.service;

import com.yupi.usercenter.model.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.Arrays;

@SpringBootTest
public class testTagse {

    @Resource
    private UserService userService;


    @Test
    public void test() {
        User user = userService.getById(2);
        String tags = user.getTags();

        tags = tags.substring(2,tags.length()-2);
//        System.out.println(tags);
        Arrays.asList(tags.split(",")).forEach(System.out::println);
    }
}
