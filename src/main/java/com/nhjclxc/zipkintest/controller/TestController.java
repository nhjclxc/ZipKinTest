package com.nhjclxc.zipkintest.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author LuoXianchao
 * @since 2025/06/27 10:56
 */
@Slf4j
@RestController
@RequestMapping("/zipkin")
public class TestController {


    @GetMapping("get")
    public Object get(String aa) {
//        System.out.println("aa = " + aa);
        log.info("=== aa 开始执行 ===" + aa);
        return "返回：" + aa;
    }
}
