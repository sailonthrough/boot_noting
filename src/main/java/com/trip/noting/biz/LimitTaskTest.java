package com.trip.noting.biz;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * LimitTask使用示例
 * 演示如何注册任务执行器、添加任务和使用限流任务服务
 */
@Slf4j
@Component
public class LimitTaskTest {

    @Autowired
    private LimitTask limitTask;

    // 定义不同的任务类型ZSet key
    private static final String ROUTE_REFRESH_KEY = "route:refresh:pending";
    private static final String SCORE_REFRESH_KEY = "score:refresh:pending";
    private static final String IMAGE_GENERATE_KEY = "image:generate:pending";

    /**
     * 初始化：注册各种任务类型的执行器
     */
    @PostConstruct
    public void init() {
        // 注册航线图刷新任务执行器
        limitTask.registerTaskExecutor(ROUTE_REFRESH_KEY, (bizKey) -> {
            log.info("执行航线图刷新任务，航线ID: {}", bizKey);
            // 模拟调用下游绘图接口
            try {
                // 这里可以调用实际的业务服务
                // routeService.refreshRouteImage(bizKey);
                // 或者调用HTTP接口
                // restTemplate.postForObject(downstreamUrl, request, responseType);
                
                // 模拟接口调用
                Thread.sleep(100);
                log.info("航线图刷新完成，航线ID: {}", bizKey);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("航线图刷新任务被中断", e);
            } catch (Exception e) {
                log.error("航线图刷新失败，航线ID: {}", bizKey, e);
                throw e; // 抛出异常会触发重试机制
            }
        });

        // 注册分数刷新任务执行器
        limitTask.registerTaskExecutor(SCORE_REFRESH_KEY, (bizKey) -> {
            log.info("执行分数刷新任务，业务ID: {}", bizKey);
            try {
                // 模拟分数计算
                // scoreService.calculateScore(bizKey);
                Thread.sleep(50);
                log.info("分数刷新完成，业务ID: {}", bizKey);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("分数刷新任务被中断", e);
            } catch (Exception e) {
                log.error("分数刷新失败，业务ID: {}", bizKey, e);
                throw e;
            }
        });

        // 注册图片生成任务执行器
        limitTask.registerTaskExecutor(IMAGE_GENERATE_KEY, (bizKey) -> {
            log.info("执行图片生成任务，业务ID: {}", bizKey);
            try {
                // 模拟图片生成
                // imageService.generateImage(bizKey);
                Thread.sleep(200);
                log.info("图片生成完成，业务ID: {}", bizKey);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("图片生成任务被中断", e);
            } catch (Exception e) {
                log.error("图片生成失败，业务ID: {}", bizKey, e);
                throw e;
            }
        });

        // 注册批量任务执行器示例（航线图刷新支持批量执行）
        limitTask.registerBatchTaskExecutor(ROUTE_REFRESH_KEY, (bizKeys) -> {
            log.info("批量执行航线图刷新任务，航线ID列表: {}", bizKeys);
            try {
                // 模拟批量调用下游接口
                // 这里可以调用实际的批量接口，比如：
                // routeService.batchRefreshRouteImage(bizKeys);
                // 或者调用批量HTTP接口
                // restTemplate.postForObject(batchUrl, bizKeys, responseType);
                
                // 模拟批量接口调用（比单个调用快很多）
                Thread.sleep(100 + bizKeys.size() * 10); // 批量调用有基础耗时+每个任务的额外耗时
                log.info("批量航线图刷新完成，航线数量: {}", bizKeys.size());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("批量航线图刷新任务被中断", e);
            } catch (Exception e) {
                log.error("批量航线图刷新失败，航线数量: {}", bizKeys.size(), e);
                throw e; // 抛出异常会触发重试机制
            }
        });

        log.info("所有任务执行器注册完成（包括单个和批量执行器）");
    }

    /**
     * 示例1：添加航线图刷新任务
     * 当城市信息变更时，触发航线图刷新
     */
    public void example1_AddRouteRefreshTask() {
        // 场景：城市"上海"信息变更，需要刷新所有涉及上海的航线
        String[] routeIds = {"1001", "1002", "1003", "1004", "1005"};
        
        for (String routeId : routeIds) {
            // delaySeconds为null，表示立即执行（到期时间为当前时间戳）
            limitTask.addTask(ROUTE_REFRESH_KEY, routeId, null);
            log.info("已添加航线图刷新任务，航线ID: {}", routeId);
        }
        
        // 任务会被定时任务自动执行，按照QPS=2的速度处理
        // 如果执行失败，会自动重试（最多5次，指数退避）
    }

    /**
     * 示例2：添加延迟执行的分数刷新任务
     * 使用防抖机制，5分钟内多次变更只刷新一次
     */
    public void example2_AddDelayedScoreRefreshTask() {
        String bizId = "score_001";
        
        // 延迟5分钟执行（300秒），实现防抖效果
        long delaySeconds = 5 * 60; // 5分钟
        limitTask.addTask(SCORE_REFRESH_KEY, bizId, delaySeconds);
        
        log.info("已添加延迟分数刷新任务，业务ID: {}, 延迟: {}秒", bizId, delaySeconds);
        
        // 如果在5分钟内再次调用addTask，由于ZSet自动去重，只会保留一个任务
        // 到期后会自动执行
    }

    /**
     * 示例3：批量添加任务
     * 模拟大量任务需要处理
     */
    public void example3_BatchAddTasks() {
        // 模拟100个图片生成任务
        for (int i = 1; i <= 100; i++) {
            String bizKey = "image_" + i;
            // 立即执行
            limitTask.addTask(IMAGE_GENERATE_KEY, bizKey, null);
        }
        
        log.info("已批量添加100个图片生成任务");
        
        // 由于QPS限制为2，这100个任务会按照每500ms一个的速度执行
        // 预计需要 100 * 0.5 = 50秒才能全部执行完成
    }

    /**
     * 示例4：手动触发任务执行
     * 不等待定时任务，立即执行指定ZSet的任务
     */
    public void example4_ManualExecuteTask() {
        // 手动触发航线图刷新任务执行
        limitTask.execTask(ROUTE_REFRESH_KEY);
        
        log.info("已手动触发任务执行，ZSetKey: {}", ROUTE_REFRESH_KEY);
    }

    /**
     * 示例5：演示重试机制
     * 创建一个会失败的任务，观察重试行为
     */
    public void example5_RetryMechanism() {
        // 注册一个会失败的任务执行器（用于演示）
        String testKey = "test:retry:pending";
        limitTask.registerTaskExecutor(testKey, (bizKey) -> {
            log.info("执行测试任务，bizKey: {}", bizKey);
            // 模拟失败（抛出异常）
            throw new RuntimeException("模拟任务执行失败");
        });

        // 添加一个测试任务
        limitTask.addTask(testKey, "test_001", null);
        
        log.info("已添加测试任务，该任务会失败并触发重试机制");
        log.info("重试时间表：");
        log.info("  - 第1次重试：2分钟后");
        log.info("  - 第2次重试：4分钟后");
        log.info("  - 第3次重试：8分钟后");
        log.info("  - 第4次重试：16分钟后");
        log.info("  - 超过5次后，任务会被删除");
    }

    /**
     * 示例6：检查任务执行器是否已注册
     */
    public void example6_CheckExecutor() {
        boolean hasRouteExecutor = limitTask.hasTaskExecutor(ROUTE_REFRESH_KEY);
        boolean hasRouteBatchExecutor = limitTask.hasBatchTaskExecutor(ROUTE_REFRESH_KEY);
        boolean hasScoreExecutor = limitTask.hasTaskExecutor(SCORE_REFRESH_KEY);
        boolean hasUnknownExecutor = limitTask.hasTaskExecutor("unknown:key");
        
        log.info("航线图刷新单个执行器已注册: {}", hasRouteExecutor);
        log.info("航线图刷新批量执行器已注册: {}", hasRouteBatchExecutor);
        log.info("分数刷新执行器已注册: {}", hasScoreExecutor);
        log.info("未知执行器已注册: {}", hasUnknownExecutor);
    }
    
    /**
     * 示例7：批量执行示例
     * 演示批量执行的优势：当有批量执行器时，会优先使用批量执行，提高效率
     */
    public void example7_BatchExecution() {
        log.info("========== 批量执行示例 ==========");
        
        // 添加多个任务
        String[] routeIds = {"route_001", "route_002", "route_003", "route_004", "route_005"};
        for (String routeId : routeIds) {
            limitTask.addTask(ROUTE_REFRESH_KEY, routeId, null);
        }
        
        log.info("已添加 {} 个航线刷新任务", routeIds.length);
        log.info("由于注册了批量执行器，这些任务会批量执行，而不是逐个执行");
        log.info("批量执行的优势：");
        log.info("  - 减少网络往返次数");
        log.info("  - 提高执行效率");
        log.info("  - 降低系统负载");
        
        // 手动触发执行，观察批量执行效果
        limitTask.execTask(ROUTE_REFRESH_KEY);
    }
    
    /**
     * 示例8：单个执行 vs 批量执行对比
     * 演示如何选择使用单个执行器还是批量执行器
     */
    public void example8_SingleVsBatch() {
        log.info("========== 单个执行 vs 批量执行对比 ==========");
        
        // 场景1：只有单个执行器（分数刷新）
        log.info("场景1：分数刷新任务（只有单个执行器）");
        limitTask.addTask(SCORE_REFRESH_KEY, "score_001", null);
        limitTask.addTask(SCORE_REFRESH_KEY, "score_002", null);
        limitTask.execTask(SCORE_REFRESH_KEY);
        log.info("结果：会按照QPS=2的速度逐个执行（每500ms一个）");
        
        // 场景2：有批量执行器（航线刷新）
        log.info("场景2：航线刷新任务（有批量执行器）");
        limitTask.addTask(ROUTE_REFRESH_KEY, "route_001", null);
        limitTask.addTask(ROUTE_REFRESH_KEY, "route_002", null);
        limitTask.addTask(ROUTE_REFRESH_KEY, "route_003", null);
        limitTask.execTask(ROUTE_REFRESH_KEY);
        log.info("结果：会批量执行，一次性处理所有任务");
    }

    /**
     * 完整使用示例
     * 演示完整的工作流程
     */
    public void completeExample() {
        log.info("========== LimitTask 完整使用示例 ==========");
        
        // 1. 注册任务执行器（已在@PostConstruct中完成）
        log.info("步骤1: 任务执行器已在初始化时注册");
        
        // 2. 添加任务
        log.info("步骤2: 添加任务");
        example1_AddRouteRefreshTask();
        
        // 3. 检查执行器
        log.info("步骤3: 检查任务执行器");
        example6_CheckExecutor();
        
        // 4. 手动触发执行（可选）
        log.info("步骤4: 手动触发任务执行");
        example4_ManualExecuteTask();
        
        log.info("========== 示例完成 ==========");
        log.info("提示：任务会按照QPS=2的速度自动执行");
        log.info("提示：如果任务失败，会自动重试（最多5次）");
    }
    
    /**
     * 示例9：并发执行所有任务
     * 演示如何使用 execAllTasks 方法并发执行所有任务类型
     */
    public void example9_ConcurrentExecution() {
        log.info("========== 并发执行所有任务示例 ==========");
        
        // 添加不同类型的任务
        limitTask.addTask(ROUTE_REFRESH_KEY, "route_001", null);
        limitTask.addTask(SCORE_REFRESH_KEY, "score_001", null);
        limitTask.addTask(IMAGE_GENERATE_KEY, "image_001", null);
        
        log.info("已添加多种类型的任务");
        log.info("使用 execAllTasks() 并发执行所有任务类型");
        
        // 方式1：异步执行，不等待完成
        CompletableFuture<Void> future = limitTask.execAllTasks();
        future.whenComplete((result, throwable) -> {
            if (throwable != null) {
                log.error("任务执行失败", throwable);
            } else {
                log.info("所有任务类型执行完成");
            }
        });
        
        log.info("任务已提交，异步执行中...");
    }
    
    /**
     * 示例10：同步等待所有任务完成
     * 演示如何使用 execAllTasksSync 方法同步等待所有任务完成
     */
    public void example10_SyncExecution() {
        log.info("========== 同步执行所有任务示例 ==========");
        
        // 添加不同类型的任务
        limitTask.addTask(ROUTE_REFRESH_KEY, "route_002", null);
        limitTask.addTask(SCORE_REFRESH_KEY, "score_002", null);
        
        log.info("已添加多种类型的任务");
        log.info("使用 execAllTasksSync() 同步等待所有任务完成");
        
        try {
            // 方式2：同步执行，等待所有任务完成（带超时）
            limitTask.execAllTasksSync(30, TimeUnit.SECONDS);
            log.info("所有任务类型执行完成");
        } catch (TimeoutException e) {
            log.error("任务执行超时", e);
        } catch (InterruptedException e) {
            log.error("任务执行被中断", e);
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 示例11：并发执行性能对比
     * 对比串行执行和并发执行的性能差异
     */
    public void example11_PerformanceComparison() {
        log.info("========== 并发执行性能对比 ==========");
        
        // 添加多个不同类型的任务
        for (int i = 1; i <= 5; i++) {
            limitTask.addTask(ROUTE_REFRESH_KEY, "route_" + i, null);
            limitTask.addTask(SCORE_REFRESH_KEY, "score_" + i, null);
            limitTask.addTask(IMAGE_GENERATE_KEY, "image_" + i, null);
        }
        
        log.info("已添加15个任务（3种类型，每种5个）");
        
        // 并发执行
        long startTime = System.currentTimeMillis();
        try {
            limitTask.execAllTasksSync(60, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("执行失败", e);
        }
        long endTime = System.currentTimeMillis();
        
        log.info("并发执行耗时: {} ms", (endTime - startTime));
        log.info("说明：不同类型的任务会并发执行，提高整体效率");
    }

    /**
     * 模拟业务场景：城市变更触发航线刷新
     * 这是实际业务中最常见的场景
     */
    public void simulateCityChangeScenario() {
        log.info("========== 模拟业务场景：城市变更触发航线刷新 ==========");
        
        // 场景：MySQL binlog监听到城市"上海"的信息变更
        String cityId = "shanghai";
        log.info("检测到城市信息变更，城市ID: {}", cityId);
        
        // 查询该城市涉及的所有航线（模拟）
        String[] affectedRoutes = {"route_001", "route_002", "route_003", "route_004", 
                                   "route_005", "route_006", "route_007", "route_008"};
        
        log.info("查询到 {} 条涉及该城市的航线，开始添加刷新任务", affectedRoutes.length);
        
        // 批量添加任务，delaySeconds为null表示立即执行
        for (String routeId : affectedRoutes) {
            limitTask.addTask(ROUTE_REFRESH_KEY, routeId, null);
        }
        
        log.info("已添加 {} 个航线刷新任务到队列", affectedRoutes.length);
        log.info("任务将按照QPS=2的速度执行（每500ms一个）");
        log.info("预计需要 {} 秒完成所有任务", (affectedRoutes.length * 500) / 1000.0);
        log.info("如果任务失败，会自动重试（指数退避：2分钟、4分钟、8分钟、16分钟）");
    }
}
