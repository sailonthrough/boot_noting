package com.trip.noting.controller;

import com.trip.noting.biz.RateLimiterExample;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 限流器测试控制器
 * 提供 HTTP 接口来测试不同的限流策略
 */
@RestController
@RequestMapping("/rate-limiter")
public class RateLimiterController {

    @Autowired
    private RateLimiterExample rateLimiterExample;

    /**
     * 测试固定窗口限流器
     */
    @PostMapping("/test/fixed-window")
    public String testFixedWindow(@RequestParam(defaultValue = "100") int count) {
        List<Long> bizIds = generateTestData(count);
        
        CompletableFuture.runAsync(() -> {
            rateLimiterExample.processWithFixedWindow(bizIds);
        });
        
        return "固定窗口限流器测试已启动，数据量: " + count;
    }

    /**
     * 测试滑动窗口限流器
     */
    @PostMapping("/test/sliding-window")
    public String testSlidingWindow(@RequestParam(defaultValue = "100") int count) {
        List<Long> bizIds = generateTestData(count);
        
        CompletableFuture.runAsync(() -> {
            rateLimiterExample.processWithSlidingWindow(bizIds);
        });
        
        return "滑动窗口限流器测试已启动，数据量: " + count;
    }

    /**
     * 测试令牌桶限流器
     */
    @PostMapping("/test/token-bucket")
    public String testTokenBucket(@RequestParam(defaultValue = "100") int count) {
        List<Long> bizIds = generateTestData(count);
        
        CompletableFuture.runAsync(() -> {
            rateLimiterExample.processWithTokenBucket(bizIds);
        });
        
        return "令牌桶限流器测试已启动，数据量: " + count;
    }

    /**
     * 测试自适应限流器
     */
    @PostMapping("/test/adaptive")
    public String testAdaptive(@RequestParam(defaultValue = "100") int count) {
        List<Long> bizIds = generateTestData(count);
        
        CompletableFuture.runAsync(() -> {
            rateLimiterExample.processWithAdaptive(bizIds);
        });
        
        return "自适应限流器测试已启动，数据量: " + count;
    }

    /**
     * 测试混合限流策略
     */
    @PostMapping("/test/hybrid")
    public String testHybrid(@RequestParam(defaultValue = "100") int count) {
        List<Long> bizIds = generateTestData(count);
        
        CompletableFuture.runAsync(() -> {
            rateLimiterExample.processWithHybridStrategy(bizIds);
        });
        
        return "混合限流策略测试已启动，数据量: " + count;
    }

    /**
     * 性能测试
     */
    @PostMapping("/test/performance")
    public String testPerformance(@RequestParam String strategy, 
                                @RequestParam(defaultValue = "200") int count) {
        List<Long> bizIds = generateTestData(count);
        
        CompletableFuture.runAsync(() -> {
            rateLimiterExample.performanceTest(bizIds, strategy);
        });
        
        return "性能测试已启动，策略: " + strategy + ", 数据量: " + count;
    }

    /**
     * 批量测试所有策略
     */
    @PostMapping("/test/all")
    public String testAllStrategies(@RequestParam(defaultValue = "50") int count) {
        List<Long> bizIds = generateTestData(count);
        
        CompletableFuture.runAsync(() -> {
            System.out.println("=== 开始批量测试所有限流策略 ===");
            
            // 测试固定窗口
            rateLimiterExample.processWithFixedWindow(bizIds);
            
            // 测试滑动窗口
            rateLimiterExample.processWithSlidingWindow(bizIds);
            
            // 测试令牌桶
            rateLimiterExample.processWithTokenBucket(bizIds);
            
            // 测试自适应
            rateLimiterExample.processWithAdaptive(bizIds);
            
            // 测试混合策略
            rateLimiterExample.processWithHybridStrategy(bizIds);
            
            System.out.println("=== 所有策略测试完成 ===");
        });
        
        return "所有限流策略测试已启动，数据量: " + count;
    }

    /**
     * 获取测试数据
     */
    @GetMapping("/test/data")
    public List<Long> getTestData(@RequestParam(defaultValue = "100") int count) {
        return generateTestData(count);
    }

    /**
     * 生成测试数据
     */
    private List<Long> generateTestData(int count) {
        List<Long> bizIds = new ArrayList<>();
        for (long i = 1; i <= count; i++) {
            bizIds.add(i);
        }
        return bizIds;
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public String health() {
        return "Rate Limiter Controller is running";
    }

    /**
     * 获取支持的策略列表
     */
    @GetMapping("/strategies")
    public String getSupportedStrategies() {
        return "支持的限流策略:\n" +
               "1. fixed - 固定窗口限流器\n" +
               "2. sliding - 滑动窗口限流器\n" +
               "3. token - 令牌桶限流器\n" +
               "4. adaptive - 自适应限流器\n" +
               "5. hybrid - 混合限流策略\n\n" +
               "使用方法:\n" +
               "POST /rate-limiter/test/{strategy}?count=100\n" +
               "POST /rate-limiter/test/performance?strategy=fixed&count=200\n" +
               "POST /rate-limiter/test/all?count=50";
    }
}
