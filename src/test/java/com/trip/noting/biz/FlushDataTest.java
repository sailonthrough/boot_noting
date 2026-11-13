package com.trip.noting.biz;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class FlushDataTest {

    private FlushData flushData = new FlushData();

    @Test
    public void testQPSControl() throws InterruptedException {
        // 创建测试数据
        List<Long> bizIds = new ArrayList<>();
        for (long i = 1; i <= 200; i++) {
            bizIds.add(i);
        }

        System.out.println("开始测试 QPS 控制，总数据量: " + bizIds.size());
        long startTime = System.currentTimeMillis();

        // 异步执行
        flushData.flushImage(bizIds);

        // 等待处理完成
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        System.out.println("处理完成，总耗时: " + duration + "ms");
        System.out.println("平均 QPS: " + String.format("%.2f", (double) bizIds.size() / duration * 1000));
        
        // 获取 QPS 统计信息
        System.out.println(flushData.getQPSStats());
    }

    @Test
    public void testConcurrentQPSControl() throws InterruptedException {
        int threadCount = 5;
        int dataPerThread = 50;
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        System.out.println("开始并发测试，线程数: " + threadCount + ", 每线程数据量: " + dataPerThread);
        long startTime = System.currentTimeMillis();

        // 创建多个线程并发调用
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            new Thread(() -> {
                try {
                    List<Long> bizIds = new ArrayList<>();
                    for (long j = 1; j <= dataPerThread; j++) {
                        bizIds.add((long) (threadIndex * dataPerThread + j));
                    }
                    
                    System.out.println("线程 " + threadIndex + " 开始处理 " + bizIds.size() + " 条数据");
                    flushData.flushImage(bizIds);
                    System.out.println("线程 " + threadIndex + " 处理完成");
                    
                } catch (Exception e) {
                    System.err.println("线程 " + threadIndex + " 处理异常: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        // 等待所有线程完成
        latch.await(60, TimeUnit.SECONDS);
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        int totalData = threadCount * dataPerThread;
        
        System.out.println("并发测试完成，总耗时: " + duration + "ms");
        System.out.println("总数据量: " + totalData);
        System.out.println("平均 QPS: " + String.format("%.2f", (double) totalData / duration * 1000));
        System.out.println(flushData.getQPSStats());
    }

    @Test
    public void testQPSAdjustment() {
        System.out.println("测试 QPS 动态调整");
        System.out.println("当前 QPS 统计: " + flushData.getQPSStats());
        
        // 调整 QPS 限制
        flushData.setMaxQPS(50);
        System.out.println("QPS 限制已调整为 50");
        
        // 再次获取统计信息
        System.out.println("调整后 QPS 统计: " + flushData.getQPSStats());
    }
}
