package com.trip.noting.data;

import org.junit.Test;

public class AlgorithmTest {

    @Test
    public void demo() {
        String originalUrl = "https://dimg04.c-ctrip.com/images/100i0h000000g3n2c1C27_C_160_120_Q60.jpg";
        String outerDomain = "https://dimg04.c-ctrip.com";
        String innerDomain = "https://dimg04.fx.ctripcorp.com";
        // 检查URL是否已经包含外网域名
        if (!originalUrl.contains(outerDomain)) {
            System.out.println(originalUrl);
        }
        String replace = originalUrl.replace(outerDomain, innerDomain);
        System.out.println(replace);
    }
}
