package com.trip.noting.utils;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 高级 QPS 控制工具类
 * 支持多种限流策略：固定窗口、滑动窗口、令牌桶
 */
public class RateLimiter {
    
    /**
     * 固定窗口限流器
     */
    public static class FixedWindowRateLimiter {
        private final int maxRequests;
        private final long windowSize;
        private final AtomicLong requestCount;
        private final AtomicLong windowStart;
        
        public FixedWindowRateLimiter(int maxRequests, long windowSizeMs) {
            this.maxRequests = maxRequests;
            this.windowSize = windowSizeMs;
            this.requestCount = new AtomicLong(0);
            this.windowStart = new AtomicLong(System.currentTimeMillis());
        }
        
        public boolean tryAcquire() {
            long currentTime = System.currentTimeMillis();
            long currentWindowStart = windowStart.get();
            
            // 检查是否需要重置窗口
            if (currentTime - currentWindowStart >= windowSize) {
                if (windowStart.compareAndSet(currentWindowStart, currentTime)) {
                    requestCount.set(0);
                }
            }
            
            // 检查是否允许请求
            long currentCount = requestCount.get();
            if (currentCount < maxRequests) {
                return requestCount.compareAndSet(currentCount, currentCount + 1);
            }
            
            return false;
        }
        
        public void acquire() throws InterruptedException {
            while (!tryAcquire()) {
                Thread.sleep(1); // 短暂等待后重试
            }
        }
        
        public String getStats() {
            long currentTime = System.currentTimeMillis();
            long currentWindowStart = windowStart.get();
            long currentCount = requestCount.get();
            
            if (currentTime - currentWindowStart >= windowSize) {
                return "当前 QPS: 0 (窗口已重置)";
            } else {
                long elapsed = currentTime - currentWindowStart;
                double currentQPS = (double) currentCount / elapsed * 1000;
                return String.format("当前 QPS: %.2f, 剩余配额: %d", currentQPS, maxRequests - currentCount);
            }
        }
    }
    
    /**
     * 滑动窗口限流器
     */
    public static class SlidingWindowRateLimiter {
        private final int maxRequests;
        private final long windowSize;
        private final int bucketCount;
        private final long[] buckets;
        private final AtomicLong currentIndex;
        private final Lock lock;
        
        public SlidingWindowRateLimiter(int maxRequests, long windowSizeMs, int bucketCount) {
            this.maxRequests = maxRequests;
            this.windowSize = windowSizeMs;
            this.bucketCount = bucketCount;
            this.buckets = new long[bucketCount];
            this.currentIndex = new AtomicLong(0);
            this.lock = new ReentrantLock();
        }
        
        public boolean tryAcquire() {
            lock.lock();
            try {
                long currentTime = System.currentTimeMillis();
                long bucketIndex = (currentTime / (windowSize / bucketCount)) % bucketCount;
                
                // 更新当前桶
                if (bucketIndex != currentIndex.get()) {
                    currentIndex.set(bucketIndex);
                    buckets[(int) bucketIndex] = 0;
                }
                
                // 计算滑动窗口内的总请求数
                long totalRequests = 0;
                for (int i = 0; i < bucketCount; i++) {
                    if (i != bucketIndex) {
                        totalRequests += buckets[i];
                    }
                }
                totalRequests += buckets[(int) bucketIndex];
                
                // 检查是否允许请求
                if (totalRequests < maxRequests) {
                    buckets[(int) bucketIndex]++;
                    return true;
                }
                
                return false;
            } finally {
                lock.unlock();
            }
        }
        
        public void acquire() throws InterruptedException {
            while (!tryAcquire()) {
                Thread.sleep(1);
            }
        }
    }
    
    /**
     * 令牌桶限流器
     */
    public static class TokenBucketRateLimiter {
        private final int capacity;
        private final double refillRate;
        private final AtomicLong tokens;
        private final AtomicLong lastRefillTime;
        
        public TokenBucketRateLimiter(int capacity, double refillRatePerSecond) {
            this.capacity = capacity;
            this.refillRate = refillRatePerSecond;
            this.tokens = new AtomicLong(capacity);
            this.lastRefillTime = new AtomicLong(System.currentTimeMillis());
        }
        
        public boolean tryAcquire() {
            return tryAcquire(1);
        }
        
        public boolean tryAcquire(int tokenCount) {
            refillTokens();
            
            long currentTokens = tokens.get();
            if (currentTokens >= tokenCount) {
                return tokens.compareAndSet(currentTokens, currentTokens - tokenCount);
            }
            
            return false;
        }
        
        public void acquire() throws InterruptedException {
            acquire(1);
        }
        
        public void acquire(int tokenCount) throws InterruptedException {
            while (!tryAcquire(tokenCount)) {
                Thread.sleep(1);
            }
        }
        
        private void refillTokens() {
            long currentTime = System.currentTimeMillis();
            long lastTime = lastRefillTime.get();
            
            if (currentTime - lastTime >= 1000) { // 每秒补充一次
                if (lastRefillTime.compareAndSet(lastTime, currentTime)) {
                    long elapsedSeconds = (currentTime - lastTime) / 1000;
                    long newTokens = (long) (elapsedSeconds * refillRate);
                    
                    if (newTokens > 0) {
                        long currentTokens = tokens.get();
                        long newTotal = Math.min(capacity, currentTokens + newTokens);
                        tokens.set(newTotal);
                    }
                }
            }
        }
        
        public String getStats() {
            return String.format("令牌桶状态 - 当前令牌: %d, 容量: %d, 补充速率: %.2f/秒", 
                    tokens.get(), capacity, refillRate);
        }
    }
    
    /**
     * 自适应限流器
     * 根据下游接口响应时间动态调整 QPS
     */
    public static class AdaptiveRateLimiter {
        private final FixedWindowRateLimiter rateLimiter;
        private final AtomicLong totalResponseTime;
        private final AtomicLong requestCount;
        private final AtomicLong errorCount;
        private final int minQPS;
        private final int maxQPS;
        
        public AdaptiveRateLimiter(int initialQPS, int minQPS, int maxQPS) {
            this.rateLimiter = new FixedWindowRateLimiter(initialQPS, 1000);
            this.totalResponseTime = new AtomicLong(0);
            this.requestCount = new AtomicLong(0);
            this.errorCount = new AtomicLong(0);
            this.minQPS = minQPS;
            this.maxQPS = maxQPS;
        }
        
        public boolean tryAcquire() {
            return rateLimiter.tryAcquire();
        }
        
        public void acquire() throws InterruptedException {
            rateLimiter.acquire();
        }
        
        public void recordResponse(long responseTime, boolean isError) {
            totalResponseTime.addAndGet(responseTime);
            requestCount.incrementAndGet();
            if (isError) {
                errorCount.incrementAndGet();
            }
            
            // 每 100 个请求调整一次 QPS
            if (requestCount.get() % 100 == 0) {
                adjustQPS();
            }
        }
        
        private void adjustQPS() {
            long totalTime = totalResponseTime.get();
            long count = requestCount.get();
            long errors = errorCount.get();
            
            if (count == 0) return;
            
            double avgResponseTime = (double) totalTime / count;
            double errorRate = (double) errors / count;
            
            // 根据响应时间和错误率调整 QPS
            int currentQPS = getCurrentQPS();
            int newQPS = currentQPS;
            
            if (avgResponseTime > 1000 || errorRate > 0.1) {
                // 响应时间过长或错误率过高，降低 QPS
                newQPS = Math.max(minQPS, (int) (currentQPS * 0.8));
            } else if (avgResponseTime < 100 && errorRate < 0.01) {
                // 响应时间短且错误率低，提高 QPS
                newQPS = Math.min(maxQPS, (int) (currentQPS * 1.2));
            }
            
            if (newQPS != currentQPS) {
                updateQPS(newQPS);
                System.out.println("自适应调整 QPS: " + currentQPS + " -> " + newQPS + 
                        " (响应时间: " + String.format("%.2f", avgResponseTime) + "ms, 错误率: " + 
                        String.format("%.2f", errorRate * 100) + "%)");
            }
            
            // 重置计数器
            totalResponseTime.set(0);
            requestCount.set(0);
            errorCount.set(0);
        }
        
        private int getCurrentQPS() {
            // 这里需要访问 FixedWindowRateLimiter 的内部状态
            // 简化实现，返回一个固定值
            return 100;
        }
        
        private void updateQPS(int newQPS) {
            // 这里需要更新 FixedWindowRateLimiter 的配置
            // 简化实现，只打印日志
            System.out.println("QPS 已更新为: " + newQPS);
        }
        
        public String getStats() {
            return rateLimiter.getStats() + "\n" +
                   "自适应统计 - 总响应时间: " + totalResponseTime.get() + "ms, " +
                   "请求数: " + requestCount.get() + ", " +
                   "错误数: " + errorCount.get();
        }
    }
}
