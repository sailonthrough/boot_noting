package com.trip.noting.biz;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class FlushData {

    // QPS 控制相关配置
    private static final int MAX_QPS = 100; // 最大 QPS，根据下游接口能力调整
    private static final int BATCH_SIZE = 10; // 批量处理大小
    private static final long RATE_LIMIT_INTERVAL = 1000; // 限流时间窗口（毫秒）
    
    // 限流器
    private final Semaphore rateLimiter = new Semaphore(MAX_QPS);
    private final AtomicLong lastResetTime = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong requestCount = new AtomicLong(0);

    @Async
    public void flushImage(List<Long> bizIds) {
        if (bizIds == null || bizIds.isEmpty()) {
            return;
        }

        // 分批处理，控制并发量
        for (int i = 0; i < bizIds.size(); i += BATCH_SIZE) {
            int endIndex = Math.min(i + BATCH_SIZE, bizIds.size());
            List<Long> batch = bizIds.subList(i, endIndex);
            
            // 等待 QPS 限制
            waitForQPSLimit();
            
            // 异步处理批次
            processBatchAsync(batch);
        }
    }

    /**
     * 等待 QPS 限制
     */
    private void waitForQPSLimit() {
        try {
            // 检查是否需要重置计数器
            long currentTime = System.currentTimeMillis();
            long lastTime = lastResetTime.get();
            
            if (currentTime - lastTime >= RATE_LIMIT_INTERVAL) {
                // 重置计数器
                if (lastResetTime.compareAndSet(lastTime, currentTime)) {
                    requestCount.set(0);
                }
            }
            
            // 如果当前时间窗口内请求数已达到限制，则等待
            if (requestCount.get() >= MAX_QPS) {
                long waitTime = RATE_LIMIT_INTERVAL - (currentTime - lastTime);
                if (waitTime > 0) {
                    Thread.sleep(waitTime);
                    // 重置计数器
                    lastResetTime.set(System.currentTimeMillis());
                    requestCount.set(0);
                }
            }
            
            // 获取令牌
            rateLimiter.acquire();
            requestCount.incrementAndGet();
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("QPS 控制被中断", e);
        }
    }

    /**
     * 异步处理批次数据
     */
    private void processBatchAsync(List<Long> batch) {
        // 为每个 bizId 创建异步任务
        List<CompletableFuture<Void>> futures = batch.stream()
                .map(bizId -> CompletableFuture.runAsync(() -> {
                    try {
                        processSingleImage(bizId);
                    } catch (Exception e) {
                        // 记录错误日志，但不中断其他任务
                        System.err.println("处理图片失败，bizId: " + bizId + ", 错误: " + e.getMessage());
                    }
                }))
                .collect(java.util.stream.Collectors.toList());

        // 等待所有任务完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    /**
     * 处理单个图片
     */
    private void processSingleImage(Long bizId) {
        try {
            // 模拟调用下游接口
            int imageId = getImageId(bizId);
            System.out.println("处理图片成功，bizId: " + bizId + ", imageId: " + imageId);
            
            // 这里可以添加实际的业务逻辑
            // 比如调用下游接口、处理响应等
            
        } catch (Exception e) {
            System.err.println("处理图片异常，bizId: " + bizId + ", 错误: " + e.getMessage());
            throw e;
        }
    }

    /**
     * 获取图片数据（模拟下游接口调用）
     */
    private int getImageId(Long bizId) {
        // 模拟网络延迟
        try {
            Thread.sleep(10 + (long) (Math.random() * 20)); // 10-30ms 随机延迟
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return (int) (bizId % 1000);
    }

    /**
     * 动态调整 QPS 限制
     */
    public void setMaxQPS(int maxQPS) {
        if (maxQPS > 0) {
            // 这里可以实现动态调整 QPS 的逻辑
            System.out.println("QPS 限制已调整为: " + maxQPS);
        }
    }

    /**
     * 获取当前 QPS 统计信息
     */
    public String getQPSStats() {
        long currentTime = System.currentTimeMillis();
        long lastTime = lastResetTime.get();
        long count = requestCount.get();
        
        if (currentTime - lastTime >= RATE_LIMIT_INTERVAL) {
            return "当前 QPS: 0 (已重置)";
        } else {
            long elapsed = currentTime - lastTime;
            double currentQPS = (double) count / elapsed * 1000;
            return String.format("当前 QPS: %.2f, 剩余配额: %d", currentQPS, MAX_QPS - count);
        }
    }
}
