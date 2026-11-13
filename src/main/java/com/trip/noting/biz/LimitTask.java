package com.trip.noting.biz;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 限流任务服务
 * 用于处理各种类型的限流任务，控制下游接口QPS=2
 * 
 * 重试机制：
 * - 最多重试5次（初始尝试 + 4次重试）
 * - 重试间隔：第1次2分钟，第2次4分钟，第3次8分钟，第4次16分钟（指数退避）
 * - 重试间隔公式：delaySeconds = 2^retryCount * 60（秒）
 * - 重试次数编码在bizKey中：原始bizKey为"12345"，第1次重试为"12345_1"，第2次为"12345_2"，以此类推
 * - 不需要额外的Hash存储重试次数，简化数据结构
 * 
 * 任务执行器映射：
 * - 支持通过zSetKey注册不同的任务执行器
 * - 不同的任务类型可以有不同的执行逻辑
 * - 支持两种执行模式：
 *   1. 单个执行模式：使用Consumer<String>作为任务执行器，按照QPS=2的速度逐个执行
 *   2. 批量执行模式：使用Consumer<List<String>>作为批量任务执行器，一次性批量执行所有任务
 * - 如果注册了批量执行器，优先使用批量执行；否则使用单个执行器
 * - 批量执行可以提高效率，减少网络往返次数，特别适合支持批量接口的下游服务
 */
@Slf4j
@Service
public class LimitTask {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    /**
     * 单个任务执行器映射：zSetKey -> 任务执行函数
     * 使用ConcurrentHashMap保证线程安全
     */
    private final Map<String, Consumer<String>> taskExecutorMap = new ConcurrentHashMap<>();
    
    /**
     * 当前执行上下文的zSetKey
     * 使用ThreadLocal存储，执行器内部可以通过 getCurrentZSetKey() 获取
     */
    private static final ThreadLocal<String> CURRENT_ZSET_KEY = new ThreadLocal<>();
    
    /**
     * 批量任务执行器映射：zSetKey -> 批量任务执行函数
     * 使用ConcurrentHashMap保证线程安全
     * 如果注册了批量执行器，优先使用批量执行；否则使用单个执行器
     */
    private final Map<String, Consumer<List<String>>> batchTaskExecutorMap = new ConcurrentHashMap<>();

    // 每次执行获取的任务数量
    private static final int BATCH_SIZE = 10;
    
    // QPS限制：每秒2个请求
    private static final int QPS_LIMIT = 2;
    
    // 每个请求的间隔时间（毫秒）：1000ms / 2 = 500ms
    private static final long REQUEST_INTERVAL_MS = 1000L / QPS_LIMIT;
    
    // 默认的ZSet key（可以通过配置注入）
    private static final String DEFAULT_ZSET_KEY = "route:refresh:pending";
    
    // 最大重试次数（初始尝试不算重试，所以最多重试4次，共5次尝试）
    private static final int MAX_RETRY_COUNT = 4;
    
    // 重试间隔基数（分钟）
    private static final int RETRY_BASE_MINUTES = 2;
    
    // 重试后缀分隔符
    private static final String RETRY_SEPARATOR = "_";
    
    /**
     * 用于并发执行所有任务的线程池
     * 使用固定大小的线程池，避免创建过多线程
     */
    private final ExecutorService taskExecutorService;
    
    /**
     * 构造函数：初始化线程池
     */
    public LimitTask() {
        // 创建固定大小的线程池，线程数根据CPU核心数动态调整
        int corePoolSize = Math.max(4, Runtime.getRuntime().availableProcessors());
        this.taskExecutorService = new ThreadPoolExecutor(
            corePoolSize,
            corePoolSize * 2,
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100),
            new ThreadFactory() {
                private int counter = 0;
                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r, "LimitTask-Executor-" + (counter++));
                    thread.setDaemon(true);
                    return thread;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        log.info("Initialized LimitTask thread pool with corePoolSize: {}", corePoolSize);
    }
    
    /**
     * 注册单个任务执行器
     * 为指定的zSetKey注册一个任务执行函数
     * 
     * @param zSetKey ZSet的key
     * @param executor 任务执行函数，接收原始bizKey作为参数
     *                 执行器内部可以通过 LimitTask.getCurrentZSetKey() 获取当前的zSetKey
     */
    public void registerTaskExecutor(String zSetKey, Consumer<String> executor) {
        if (zSetKey == null || zSetKey.trim().isEmpty()) {
            log.warn("Invalid zSetKey for task executor registration: {}", zSetKey);
            return;
        }
        if (executor == null) {
            log.warn("Task executor is null for zSetKey: {}", zSetKey);
            return;
        }
        
        taskExecutorMap.put(zSetKey, executor);
        log.info("Registered single task executor for zSetKey: {}", zSetKey);
    }
    
    /**
     * 注册批量任务执行器
     * 为指定的zSetKey注册一个批量任务执行函数
     * 如果注册了批量执行器，执行时会优先使用批量执行，提高效率
     * 
     * @param zSetKey ZSet的key
     * @param batchExecutor 批量任务执行函数，接收原始bizKey列表作为参数
     */
    public void registerBatchTaskExecutor(String zSetKey, Consumer<List<String>> batchExecutor) {
        if (zSetKey == null || zSetKey.trim().isEmpty()) {
            log.warn("Invalid zSetKey for batch task executor registration: {}", zSetKey);
            return;
        }
        if (batchExecutor == null) {
            log.warn("Batch task executor is null for zSetKey: {}", zSetKey);
            return;
        }
        
        batchTaskExecutorMap.put(zSetKey, batchExecutor);
        log.info("Registered batch task executor for zSetKey: {}", zSetKey);
    }
    
    /**
     * 取消注册单个任务执行器
     * 
     * @param zSetKey ZSet的key
     */
    public void unregisterTaskExecutor(String zSetKey) {
        if (zSetKey == null || zSetKey.trim().isEmpty()) {
            return;
        }
        
        Consumer<String> removed = taskExecutorMap.remove(zSetKey);
        if (removed != null) {
            log.info("Unregistered single task executor for zSetKey: {}", zSetKey);
        }
    }
    
    /**
     * 取消注册批量任务执行器
     * 
     * @param zSetKey ZSet的key
     */
    public void unregisterBatchTaskExecutor(String zSetKey) {
        if (zSetKey == null || zSetKey.trim().isEmpty()) {
            return;
        }
        
        Consumer<List<String>> removed = batchTaskExecutorMap.remove(zSetKey);
        if (removed != null) {
            log.info("Unregistered batch task executor for zSetKey: {}", zSetKey);
        }
    }
    
    /**
     * 取消注册所有任务执行器（单个和批量）
     * 
     * @param zSetKey ZSet的key
     */
    public void unregisterAllTaskExecutors(String zSetKey) {
        unregisterTaskExecutor(zSetKey);
        unregisterBatchTaskExecutor(zSetKey);
    }
    
    /**
     * 检查是否已注册单个任务执行器
     * 
     * @param zSetKey ZSet的key
     * @return 是否已注册
     */
    public boolean hasTaskExecutor(String zSetKey) {
        return zSetKey != null && taskExecutorMap.containsKey(zSetKey);
    }
    
    /**
     * 检查是否已注册批量任务执行器
     * 
     * @param zSetKey ZSet的key
     * @return 是否已注册
     */
    public boolean hasBatchTaskExecutor(String zSetKey) {
        return zSetKey != null && batchTaskExecutorMap.containsKey(zSetKey);
    }
    
    /**
     * 检查是否已注册任何任务执行器（单个或批量）
     * 
     * @param zSetKey ZSet的key
     * @return 是否已注册
     */
    public boolean hasAnyTaskExecutor(String zSetKey) {
        return hasTaskExecutor(zSetKey) || hasBatchTaskExecutor(zSetKey);
    }

    /**
     * 添加任务到ZSet
     * 
     * @param zSetKey ZSet的key
     * @param bizKey 业务key（航线ID）
     * @param delaySeconds 延迟秒数（任务到期时间 = 当前时间 + delaySeconds），如果为null则使用当前时间戳（秒）
     */
    public void addTask(String zSetKey, String bizKey, Long delaySeconds) {
        if (zSetKey == null || bizKey == null) {
            log.warn("Invalid parameters: zSetKey={}, bizKey={}", zSetKey, bizKey);
            return;
        }

        try {
            // 计算到期时间戳（秒）
            long currentTimestampSeconds = System.currentTimeMillis() / 1000;
            long expireTimestamp;
            
            if (delaySeconds == null) {
                // delaySeconds为空，使用当前时间戳（秒）
                expireTimestamp = currentTimestampSeconds;
            } else {
                // 到期时间 = 当前时间 + 延迟秒数
                expireTimestamp = currentTimestampSeconds + delaySeconds;
            }
            
            // 添加到ZSet，score为到期时间戳（秒），自动去重
            // bizKey直接作为member，不包含重试后缀（初始任务重试次数为0）
            redisTemplate.opsForZSet().add(zSetKey, bizKey, expireTimestamp);
            
            log.debug("Added task to ZSet: key={}, bizKey={}, expireTimestamp={} (seconds)", 
                zSetKey, bizKey, expireTimestamp);
        } catch (Exception e) {
            log.error("Failed to add task to ZSet: key={}, bizKey={}", zSetKey, bizKey, e);
        }
    }

    /**
     * 定时任务：每分钟执行一次，每次获取10个到期的任务执行
     * - 如果注册了批量执行器，会批量执行所有任务
     * - 如果没有批量执行器，会按照QPS=2的速度逐个执行（每500ms执行一个任务）
     * 使用默认的ZSet key
     */
    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.MINUTES)
    public void scheduledExecTask() {
        execTask(DEFAULT_ZSET_KEY);
    }

    /**
     * 执行所有任务（并发执行）
     * 所有任务类型会并发执行，提高效率
     * 
     * @return CompletableFuture<Void> 可以用于等待所有任务完成
     */
    public CompletableFuture<Void> execAllTasks() {
        // 获取所有已注册的任务类型（包括单个执行器和批量执行器）
        Set<String> allTaskNames = new HashSet<>();
        allTaskNames.addAll(taskExecutorMap.keySet());
        allTaskNames.addAll(batchTaskExecutorMap.keySet());
        
        if (allTaskNames.isEmpty()) {
            log.debug("No task executors registered, skipping execution");
            return CompletableFuture.completedFuture(null);
        }
        
        log.info("Starting concurrent execution of all tasks, total task types: {}", allTaskNames.size());
        
        // 为每个任务类型创建异步执行任务
        List<CompletableFuture<Void>> futures = allTaskNames.stream()
            .map(taskName -> CompletableFuture.runAsync(() -> {
                try {
                    log.debug("Executing task type: {}", taskName);
                    execTask(taskName);
                } catch (Exception e) {
                    log.error("Error executing task type: {}", taskName, e);
                    // 不抛出异常，让其他任务继续执行
                }
            }, taskExecutorService))
            .collect(Collectors.toList());
        
        // 等待所有任务完成
        CompletableFuture<Void> allTasks = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        
        // 添加完成回调
        allTasks.whenComplete((result, throwable) -> {
            if (throwable != null) {
                log.error("Error during concurrent task execution", throwable);
            } else {
                log.info("All tasks completed successfully, total task types: {}", allTaskNames.size());
            }
        });
        
        return allTasks;
    }
    
    /**
     * 执行所有任务（并发执行，同步等待完成）
     * 阻塞当前线程直到所有任务执行完成
     * 
     * @param timeout 超时时间
     * @param unit 时间单位
     * @throws TimeoutException 如果超时
     * @throws InterruptedException 如果被中断
     */
    public void execAllTasksSync(long timeout, TimeUnit unit) throws TimeoutException, InterruptedException {
        CompletableFuture<Void> future = execAllTasks();
        try {
            future.get(timeout, unit);
        } catch (ExecutionException e) {
            log.error("Error during task execution", e);
            throw new RuntimeException("Task execution failed", e);
        }
    }
    
    /**
     * 执行所有任务（并发执行，同步等待完成，无超时）
     * 阻塞当前线程直到所有任务执行完成
     * 
     * @throws InterruptedException 如果被中断
     */
    public void execAllTasksSync() throws InterruptedException {
        try {
            execAllTasksSync(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            // 不会发生，因为超时时间设置为Long.MAX_VALUE
        }
    }
    
    /**
     * Spring Bean 销毁时关闭线程池
     */
    @PreDestroy
    public void destroy() {
        if (taskExecutorService != null && !taskExecutorService.isShutdown()) {
            log.info("Shutting down LimitTask thread pool");
            taskExecutorService.shutdown();
            try {
                // 等待正在执行的任务完成，最多等待30秒
                if (!taskExecutorService.awaitTermination(30, TimeUnit.SECONDS)) {
                    log.warn("Thread pool did not terminate gracefully, forcing shutdown");
                    taskExecutorService.shutdownNow();
                    // 再等待5秒
                    if (!taskExecutorService.awaitTermination(5, TimeUnit.SECONDS)) {
                        log.error("Thread pool did not terminate");
                    }
                }
            } catch (InterruptedException e) {
                log.error("Interrupted while shutting down thread pool", e);
                taskExecutorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
            log.info("LimitTask thread pool shutdown completed");
        }
    }

    /**
     * 执行任务：获取到期的任务并执行
     * - 如果注册了批量执行器，会批量执行所有任务（提高效率）
     * - 如果没有批量执行器，会按照QPS=2的速度逐个执行（每500ms一个）
     * 
     * @param zSetKey ZSet的key
     */
    public void execTask(String zSetKey) {
        if (zSetKey == null || zSetKey.trim().isEmpty()) {
            log.warn("Invalid zSetKey: {}", zSetKey);
            return;
        }

        try {
            log.info("Starting scheduled task execution for key: {}", zSetKey);
            
            // 获取到期的任务（最多10个，不使用Lua脚本）
            List<String> tasks = fetchExpiredTasks(zSetKey, BATCH_SIZE);
            
            if (tasks == null || tasks.isEmpty()) {
                log.debug("No expired tasks found for key: {}", zSetKey);
                return;
            }

            log.info("Found {} expired tasks to process for key: {}", tasks.size(), zSetKey);
            
            // 检查是否支持批量执行
            if (hasBatchTaskExecutor(zSetKey)) {
                // 使用批量执行模式
                executeTasksInBatch(tasks, zSetKey);
            } else {
                // 使用单个执行模式，按照QPS=2的速度执行任务（每500ms执行一个）
                executeTasksWithRateLimit(tasks, zSetKey);
            }
            
            log.info("Completed processing {} tasks for key: {}", tasks.size(), zSetKey);
            
        } catch (Exception e) {
            log.error("Error during task execution for key: {}", zSetKey, e);
        }
    }

    /**
     * 获取到期的任务（不使用Lua脚本）
     * 
     * @param zSetKey ZSet的key
     * @param limit 最多获取的任务数量
     * @return 到期的任务列表
     */
    private List<String> fetchExpiredTasks(String zSetKey, int limit) {
        try {
            // 当前时间戳（秒）
            long currentTimestampSeconds = System.currentTimeMillis() / 1000;
            
            // 使用ZRANGEBYSCORE获取到期的任务（score <= currentTimestamp）
            // 不使用Lua脚本，直接使用Redis命令
            Set<Object> expiredMembers = redisTemplate.opsForZSet()
                .rangeByScore(zSetKey, 0, currentTimestampSeconds, 0, limit);
            
            if (expiredMembers == null || expiredMembers.isEmpty()) {
                return new ArrayList<>();
            }

            // 将Object转换为String
            List<String> tasks = new ArrayList<>();
            for (Object member : expiredMembers) {
                if (member instanceof String) {
                    tasks.add((String) member);
                }
            }
            
            return tasks;
        } catch (Exception e) {
            log.error("Failed to fetch expired tasks for key: {}", zSetKey, e);
            return new ArrayList<>();
        }
    }

    /**
     * 批量执行任务
     * 一次性执行所有任务，提高效率
     * 
     * @param tasks 任务列表（可能包含重试后缀）
     * @param zSetKey ZSet的key
     */
    private void executeTasksInBatch(List<String> tasks, String zSetKey) {
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        
        try {
            // 获取原始bizKey列表（去掉重试后缀）
            List<String> originalBizKeys = new ArrayList<>();
            for (String bizKey : tasks) {
                originalBizKeys.add(getOriginalBizKey(bizKey));
            }
            
            log.info("Executing batch tasks for zSetKey: {}, count: {}", zSetKey, originalBizKeys.size());
            
            // 查找批量执行器
            Consumer<List<String>> batchExecutor = batchTaskExecutorMap.get(zSetKey);
            if (batchExecutor == null) {
                log.warn("Batch executor not found for zSetKey: {}, falling back to single execution", zSetKey);
                executeTasksWithRateLimit(tasks, zSetKey);
                return;
            }
            
            // 执行批量任务
            batchExecutor.accept(originalBizKeys);
            
            // 批量执行成功，删除所有任务
            for (String bizKey : tasks) {
                removeTask(zSetKey, bizKey);
            }
            
            log.info("Batch tasks executed successfully and removed: count={}", tasks.size());
            
        } catch (Exception e) {
            log.error("Failed to execute batch tasks for zSetKey: {}, count: {}", zSetKey, tasks.size(), e);
            // 批量执行失败，对每个任务进行失败处理
            for (String bizKey : tasks) {
                handleTaskFailure(zSetKey, bizKey);
            }
        }
    }
    
    /**
     * 按照QPS=2的速度执行任务（单个执行模式）
     * 每500ms执行一个任务
     * 
     * @param tasks 任务列表
     * @param zSetKey ZSet的key
     */
    private void executeTasksWithRateLimit(List<String> tasks, String zSetKey) {
        for (int i = 0; i < tasks.size(); i++) {
            String bizKey = tasks.get(i);
            
            try {
                // 获取原始bizKey（去掉重试后缀）
                String originalBizKey = getOriginalBizKey(bizKey);
                
                // 执行任务（根据zSetKey查找对应的执行器，使用原始bizKey）
                executeSingleTask(zSetKey, originalBizKey);
                
                // 任务执行成功，删除任务
                removeTask(zSetKey, bizKey);
                
                log.debug("Task executed successfully and removed: bizKey={}, originalBizKey={}, progress={}/{}", 
                    bizKey, originalBizKey, i + 1, tasks.size());
                
                // 如果不是最后一个任务，需要等待间隔时间（QPS=2，每500ms一个）
                if (i < tasks.size() - 1) {
                    Thread.sleep(REQUEST_INTERVAL_MS);
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Task execution interrupted for bizKey: {}", bizKey, e);
                break;
            } catch (Exception e) {
                log.error("Failed to execute task for bizKey: {}", bizKey, e);
                // 任务执行失败，增加重试次数并重新入队
                handleTaskFailure(zSetKey, bizKey);
            }
        }
    }

    /**
     * 执行单个任务
     * 根据zSetKey查找对应的任务执行器并执行
     * 
     * @param zSetKey ZSet的key，用于查找对应的任务执行器
     * @param bizKey 业务key（原始bizKey，不包含重试后缀）
     */
    private void executeSingleTask(String zSetKey, String bizKey) {
        // 保存旧值，支持嵌套调用场景
        String previousZSetKey = CURRENT_ZSET_KEY.get();
        try {
            // 设置当前执行上下文的zSetKey，供执行器内部获取
            CURRENT_ZSET_KEY.set(zSetKey);
            
            // 查找对应的任务执行器
            Consumer<String> executor = taskExecutorMap.get(zSetKey);
            
            if (executor == null) {
                // 如果没有注册执行器，使用默认实现（向后兼容）
                log.warn("No task executor registered for zSetKey: {}, using default implementation", zSetKey);
                executeDefaultTask(bizKey);
                return;
            }
            
            log.info("Executing task for zSetKey: {}, bizKey: {}", zSetKey, bizKey);
            
            // 执行注册的任务执行器
            executor.accept(bizKey);
            
            log.debug("Task completed for zSetKey: {}, bizKey: {}", zSetKey, bizKey);
            
        } catch (Exception e) {
            log.error("Error executing task for zSetKey: {}, bizKey: {}", zSetKey, bizKey, e);
            throw e;
        } finally {
            // 恢复旧值或清理，防止内存泄漏，支持嵌套调用
            if (previousZSetKey != null) {
                CURRENT_ZSET_KEY.set(previousZSetKey);
            } else {
                CURRENT_ZSET_KEY.remove();
            }
        }
    }
    
    /**
     * 获取当前执行上下文的zSetKey
     * 在任务执行器内部调用此方法获取当前任务的zSetKey
     * 
     * @return 当前执行上下文的zSetKey，如果不在执行上下文中则返回null
     */
    public static String getCurrentZSetKey() {
        return CURRENT_ZSET_KEY.get();
    }
    
    /**
     * 默认任务执行实现（向后兼容）
     * 如果没有注册任务执行器，使用此默认实现
     * 
     * @param bizKey 业务key
     */
    private void executeDefaultTask(String bizKey) {
        try {
            log.info("Executing default task for bizKey: {}", bizKey);
            
            // 默认实现：模拟调用下游接口
            // 实际使用时，应该注册具体的任务执行器
            Thread.sleep(100); // 模拟接口调用耗时
            
            log.debug("Default task completed for bizKey: {}", bizKey);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Task execution interrupted", e);
        } catch (Exception e) {
            log.error("Error executing default task for bizKey: {}", bizKey, e);
            throw e;
        }
    }
    
    /**
     * 删除任务（从ZSet中删除）
     * 
     * @param zSetKey ZSet的key
     * @param bizKey 业务key（可能包含重试后缀，如 "12345_1"）
     */
    private void removeTask(String zSetKey, String bizKey) {
        try {
            // 从ZSet中删除
            redisTemplate.opsForZSet().remove(zSetKey, bizKey);
            
            log.debug("Task removed: bizKey={}", bizKey);
        } catch (Exception e) {
            log.error("Failed to remove task: bizKey={}", bizKey, e);
        }
    }
    
    /**
     * 处理任务失败：增加重试次数并重新入队
     * 
     * @param zSetKey ZSet的key
     * @param bizKey 业务key（可能包含重试后缀，如 "12345_1"）
     */
    private void handleTaskFailure(String zSetKey, String bizKey) {
        try {
            // 获取原始bizKey和当前重试次数
            String originalBizKey = getOriginalBizKey(bizKey);
            int retryCount = getRetryCountFromBizKey(bizKey);
            
            if (retryCount >= MAX_RETRY_COUNT) {
                // 已达到最大重试次数，删除任务并记录失败
                removeTask(zSetKey, bizKey);
                log.warn("Task exceeded max retry count, removed: originalBizKey={}, retryCount={}", 
                    originalBizKey, retryCount);
                return;
            }
            
            // 删除旧的任务（使用旧的bizKey），准备添加新的重试任务
            removeTask(zSetKey, bizKey);
            
            // 增加重试次数，生成新的bizKey
            int newRetryCount = retryCount + 1;
            String newBizKey = originalBizKey + RETRY_SEPARATOR + newRetryCount;
            
            // 计算重试延迟时间（秒）
            // 重试间隔：2^newRetryCount * 60秒
            // 第1次重试：2^1 * 60 = 120秒（2分钟）
            // 第2次重试：2^2 * 60 = 240秒（4分钟）
            // 第3次重试：2^3 * 60 = 480秒（8分钟）
            // 第4次重试：2^4 * 60 = 960秒（16分钟）
            long delaySeconds = (long) Math.pow(RETRY_BASE_MINUTES, newRetryCount) * 60;
            
            // 计算新的到期时间戳（秒）
            long currentTimestampSeconds = System.currentTimeMillis() / 1000;
            long newExpireTimestamp = currentTimestampSeconds + delaySeconds;
            
            // 添加新的任务到ZSet（使用新的bizKey）
            redisTemplate.opsForZSet().add(zSetKey, newBizKey, newExpireTimestamp);
            
            log.info("Task scheduled for retry: originalBizKey={}, newBizKey={}, retryCount={}/{}, nextRetryIn={} seconds", 
                originalBizKey, newBizKey, newRetryCount, MAX_RETRY_COUNT, delaySeconds);
                
        } catch (Exception e) {
            log.error("Failed to handle task failure: bizKey={}", bizKey, e);
        }
    }
    
    /**
     * 从bizKey中获取原始bizKey（去掉重试后缀）
     * 例如："12345_1" -> "12345", "12345" -> "12345"
     * 
     * @param bizKey 业务key（可能包含重试后缀）
     * @return 原始bizKey
     */
    private String getOriginalBizKey(String bizKey) {
        if (bizKey == null || bizKey.isEmpty()) {
            return bizKey;
        }
        
        int lastIndex = bizKey.lastIndexOf(RETRY_SEPARATOR);
        if (lastIndex > 0 && lastIndex < bizKey.length() - 1) {
            // 检查后缀是否为数字
            String suffix = bizKey.substring(lastIndex + 1);
            try {
                Integer.parseInt(suffix);
                // 是数字，说明是重试后缀，返回原始bizKey
                return bizKey.substring(0, lastIndex);
            } catch (NumberFormatException e) {
                // 不是数字，说明不是重试后缀，返回原值
                return bizKey;
            }
        }
        
        return bizKey;
    }
    
    /**
     * 从bizKey中获取重试次数
     * 例如："12345_1" -> 1, "12345" -> 0
     * 
     * @param bizKey 业务key（可能包含重试后缀）
     * @return 重试次数，如果没有重试后缀则返回0
     */
    private int getRetryCountFromBizKey(String bizKey) {
        if (bizKey == null || bizKey.isEmpty()) {
            return 0;
        }
        
        int lastIndex = bizKey.lastIndexOf(RETRY_SEPARATOR);
        if (lastIndex > 0 && lastIndex < bizKey.length() - 1) {
            String suffix = bizKey.substring(lastIndex + 1);
            try {
                return Integer.parseInt(suffix);
            } catch (NumberFormatException e) {
                // 后缀不是数字，返回0
                return 0;
            }
        }
        return 0;
    }
}
