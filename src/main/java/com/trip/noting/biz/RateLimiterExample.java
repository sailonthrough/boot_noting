package com.trip.noting.biz;

import com.trip.noting.utils.RateLimiter;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 限流器使用示例
 * 展示如何使用不同的限流策略控制 QPS
 */
@Service
public class RateLimiterExample {

    /**
     * 使用固定窗口限流器
     */
    public void processWithFixedWindow(List<Long> bizIds) {
        // 创建限流器：每秒最多处理 50 个请求
        RateLimiter.FixedWindowRateLimiter limiter = new RateLimiter.FixedWindowRateLimiter(50, 1000);
        
        System.out.println("使用固定窗口限流器，QPS 限制: 50");
        
        bizIds.parallelStream().forEach(bizId -> {
            try {
                // 等待获取令牌
                limiter.acquire();
                
                // 处理业务逻辑
                processBusinessLogic(bizId);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("处理被中断: " + bizId);
            }
        });
        
        System.out.println("固定窗口限流器统计: " + limiter.getStats());
    }

    /**
     * 使用滑动窗口限流器
     */
    public void processWithSlidingWindow(List<Long> bizIds) {
        // 创建限流器：每秒最多处理 100 个请求，使用 10 个桶
        RateLimiter.SlidingWindowRateLimiter limiter = new RateLimiter.SlidingWindowRateLimiter(100, 1000, 10);
        
        System.out.println("使用滑动窗口限流器，QPS 限制: 100");
        
        bizIds.parallelStream().forEach(bizId -> {
            try {
                // 等待获取令牌
                limiter.acquire();
                
                // 处理业务逻辑
                processBusinessLogic(bizId);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("处理被中断: " + bizId);
            }
        });
    }

    /**
     * 使用令牌桶限流器
     */
    public void processWithTokenBucket(List<Long> bizIds) {
        // 创建限流器：桶容量 200，每秒补充 150 个令牌
        RateLimiter.TokenBucketRateLimiter limiter = new RateLimiter.TokenBucketRateLimiter(200, 150);
        
        System.out.println("使用令牌桶限流器，容量: 200, 补充速率: 150/秒");
        
        bizIds.parallelStream().forEach(bizId -> {
            try {
                // 等待获取令牌
                limiter.acquire();
                
                // 处理业务逻辑
                processBusinessLogic(bizId);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("处理被中断: " + bizId);
            }
        });
        
        System.out.println("令牌桶限流器统计: " + limiter.getStats());
    }

    /**
     * 使用自适应限流器
     */
    public void processWithAdaptive(List<Long> bizIds) {
        // 创建自适应限流器：初始 QPS 100，最小 20，最大 200
        RateLimiter.AdaptiveRateLimiter limiter = new RateLimiter.AdaptiveRateLimiter(100, 20, 200);
        
        System.out.println("使用自适应限流器，初始 QPS: 100, 范围: 20-200");
        
        bizIds.parallelStream().forEach(bizId -> {
            try {
                // 等待获取令牌
                limiter.acquire();
                
                // 记录开始时间
                long startTime = System.currentTimeMillis();
                
                // 处理业务逻辑
                boolean success = processBusinessLogicWithResult(bizId);
                
                // 记录响应时间和结果
                long responseTime = System.currentTimeMillis() - startTime;
                limiter.recordResponse(responseTime, !success);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("处理被中断: " + bizId);
            }
        });
        
        System.out.println("自适应限流器统计: " + limiter.getStats());
    }

    /**
     * 混合限流策略示例
     * 结合多种限流器实现更精细的控制
     */
    public void processWithHybridStrategy(List<Long> bizIds) {
        // 第一层：固定窗口限流，总体 QPS 控制
        RateLimiter.FixedWindowRateLimiter globalLimiter = new RateLimiter.FixedWindowRateLimiter(200, 1000);
        
        // 第二层：令牌桶限流，平滑流量
        RateLimiter.TokenBucketRateLimiter smoothLimiter = new RateLimiter.TokenBucketRateLimiter(50, 30);
        
        System.out.println("使用混合限流策略");
        System.out.println("全局 QPS 限制: 200");
        System.out.println("平滑限流: 容量 50, 补充速率 30/秒");
        
        // 分批处理
        int batchSize = 20;
        for (int i = 0; i < bizIds.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, bizIds.size());
            List<Long> batch = bizIds.subList(i, endIndex);
            
            // 等待全局限流器
            try {
                globalLimiter.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            
            // 异步处理批次
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                batch.parallelStream().forEach(bizId -> {
                    try {
                        // 等待平滑限流器
                        smoothLimiter.acquire();
                        
                        // 处理业务逻辑
                        processBusinessLogic(bizId);
                        
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        System.err.println("处理被中断: " + bizId);
                    }
                });
            });
            
            // 等待批次完成
            future.join();
        }
        
        System.out.println("混合限流策略统计:");
        System.out.println("全局限流器: " + globalLimiter.getStats());
        System.out.println("平滑限流器: " + smoothLimiter.getStats());
    }

    /**
     * 处理业务逻辑（模拟）
     */
    private void processBusinessLogic(Long bizId) {
        try {
            // 模拟网络延迟
            Thread.sleep(10 + (long) (Math.random() * 20));
            
            // 模拟业务处理
            System.out.println("处理业务逻辑: " + bizId);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 处理业务逻辑并返回结果（模拟）
     */
    private boolean processBusinessLogicWithResult(Long bizId) {
        try {
            // 模拟网络延迟
            Thread.sleep(10 + (long) (Math.random() * 20));
            
            // 模拟业务处理
            System.out.println("处理业务逻辑: " + bizId);
            
            // 模拟成功率 95%
            return Math.random() > 0.05;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 性能测试方法
     */
    public void performanceTest(List<Long> bizIds, String strategy) {
        System.out.println("\n=== 性能测试: " + strategy + " ===");
        long startTime = System.currentTimeMillis();
        
        switch (strategy.toLowerCase()) {
            case "fixed":
                processWithFixedWindow(bizIds);
                break;
            case "sliding":
                processWithSlidingWindow(bizIds);
                break;
            case "token":
                processWithTokenBucket(bizIds);
                break;
            case "adaptive":
                processWithAdaptive(bizIds);
                break;
            case "hybrid":
                processWithHybridStrategy(bizIds);
                break;
            default:
                System.out.println("未知策略: " + strategy);
                return;
        }
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        System.out.println("策略: " + strategy);
        System.out.println("总数据量: " + bizIds.size());
        System.out.println("总耗时: " + duration + "ms");
        System.out.println("平均 QPS: " + String.format("%.2f", (double) bizIds.size() / duration * 1000));
        System.out.println("=== 测试完成 ===\n");
    }
}
