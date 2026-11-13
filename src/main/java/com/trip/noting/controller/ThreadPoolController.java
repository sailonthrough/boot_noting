package com.trip.noting.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

@RestController
@Slf4j
public class ThreadPoolController {
    @Autowired
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;

    @GetMapping("/testTask")
    public String testTask() throws InterruptedException {
        System.out.println("------------");
        CountDownLatch latch = new CountDownLatch(3);
        threadPoolTaskExecutor.execute(() -> {
            String threadName = Thread.currentThread().getName();
            try {
                TimeUnit.SECONDS.sleep(3);
            } catch (InterruptedException e) {
                log.error("threadName", e);
            }
            System.out.println("threadName:" + threadName);
            latch.countDown();
        });
        threadPoolTaskExecutor.execute(() -> {
            String threadName = Thread.currentThread().getName();
            try {
                TimeUnit.SECONDS.sleep(1);
                throw new RuntimeException("1-执行失败");
            } catch (InterruptedException e) {
                log.error("threadName", e);
            }
            System.out.println("threadName:" + threadName);
            latch.countDown();
        });
        threadPoolTaskExecutor.execute(() -> {
            String threadName = Thread.currentThread().getName();
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                log.error("threadName", e);
            }
            System.out.println("threadName:" + threadName);
            latch.countDown();
        });
        boolean await = latch.await(2, TimeUnit.SECONDS);
        System.out.println(await);
        // if (!await) {
        //     throw new RuntimeException("执行失败");
        // }
        return "testTask-执行成功";
    }

    @GetMapping("/testTaskErr")
    public String testTaskErr() throws InterruptedException {
        System.out.println("------------");
        CountDownLatch latch = new CountDownLatch(3);
        CompletableFuture<Void> asyncFuture = CompletableFuture.runAsync(() -> {
            String threadName = Thread.currentThread().getName();
            try {
                TimeUnit.SECONDS.sleep(3);
            } catch (InterruptedException e) {
                log.error("threadName", e);
            }
            System.out.println("threadName:" + threadName);
            latch.countDown();
        }, threadPoolTaskExecutor);

        CompletableFuture.runAsync(() -> {
            String threadName = Thread.currentThread().getName();
            try {
                TimeUnit.SECONDS.sleep(1);
                throw new RuntimeException("1-执行失败");
            } catch (InterruptedException e) {
                log.error("threadName", e);
            }
            System.out.println("threadName:" + threadName);
            latch.countDown();
        }, threadPoolTaskExecutor);
        CompletableFuture.runAsync(() -> {
            String threadName = Thread.currentThread().getName();
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                log.error("threadName", e);
            }
            System.out.println("threadName:" + threadName);
            latch.countDown();
        }, threadPoolTaskExecutor);
        // 必须每个返回值一个个处理
        asyncFuture.exceptionally(ex -> {
            System.out.println("Task threw an exception: " + ex.getMessage());
            return null;
        }).join();
        boolean await = latch.await(2, TimeUnit.SECONDS);

        System.out.println(await);
        // if (!await) {
        //     throw new RuntimeException("执行失败");
        // }
        return "testTask-执行成功";
    }

    @GetMapping("/testTaskErrR")
    public String testTaskErrR() throws Exception {
        System.out.println("------------");
        CountDownLatch latch = new CountDownLatch(3);
        AtomicReference<Exception> exceptionHolder = new AtomicReference<>();
        threadPoolTaskExecutor.execute(() -> {
            String threadName = Thread.currentThread().getName();
            try {
                TimeUnit.SECONDS.sleep(2);
            } catch (Exception e) {
                log.error("threadName", e);
                exceptionHolder.set(e);
            }
            System.out.println("threadName:" + threadName);
            latch.countDown();
        });

        threadPoolTaskExecutor.execute(() -> {
            String threadName = Thread.currentThread().getName();
            try {
                TimeUnit.SECONDS.sleep(1);
                throw new RuntimeException("1-执行失败");
            } catch (Exception e) {
                exceptionHolder.set(e);
            }
            System.out.println("threadName:" + threadName);
            latch.countDown();
        });
        threadPoolTaskExecutor.execute(() -> {
            String threadName = Thread.currentThread().getName();
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (Exception e) {
                exceptionHolder.set(e);
            }
            System.out.println("threadName:" + threadName);
            latch.countDown();
        });

        boolean await = latch.await(2, TimeUnit.SECONDS);
        System.out.println(await);
        Exception exception = exceptionHolder.get();
        if (exception != null) {
            throw exception;
        }
        // if (!await) {
        //     throw new RuntimeException("执行失败");
        // }
        return "testTask-执行成功";
    }

    @GetMapping("/testTaskE")
    public String testTaskE() throws Exception {
        ExecutorService executor = Executors.newCachedThreadPool();
        executor.execute(() -> {
            try {
                TimeUnit.SECONDS.sleep(2);
                System.out.println("3333333");
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        executor.shutdown();
        System.out.println("111111");
        return "22222";
    }

}
