# QPS 控制功能说明

## 概述

`FlushData` 类现在包含了完整的 QPS（每秒查询数）控制功能，可以有效控制对下游接口的调用频率，避免对下游服务造成过大压力。

## 主要特性

### 1. QPS 限流控制
- **令牌桶算法**：使用 Semaphore 实现令牌桶限流
- **时间窗口控制**：1秒时间窗口内的请求数限制
- **可配置限制**：默认最大 QPS 为 100，可根据下游接口能力调整

### 2. 批量处理
- **分批处理**：将大量数据分批处理，每批默认 10 条
- **异步执行**：使用 CompletableFuture 异步处理每个批次
- **并发控制**：控制并发数量，避免资源耗尽

### 3. 错误处理
- **异常隔离**：单个请求失败不影响其他请求
- **错误日志**：记录详细的错误信息
- **优雅降级**：支持部分失败场景

## 配置参数

```java
// QPS 控制相关配置
private static final int MAX_QPS = 100;           // 最大 QPS，根据下游接口能力调整
private static final int BATCH_SIZE = 10;         // 批量处理大小
private static final long RATE_LIMIT_INTERVAL = 1000; // 限流时间窗口（毫秒）
```

## 使用方法

### 基本调用
```java
@Autowired
private FlushData flushData;

// 调用方法
List<Long> bizIds = Arrays.asList(1L, 2L, 3L, ...);
flushData.flushImage(bizIds);
```

### 动态调整 QPS
```java
// 动态调整 QPS 限制
flushData.setMaxQPS(50);

// 获取当前 QPS 统计信息
String stats = flushData.getQPSStats();
System.out.println(stats);
```

## 工作原理

### 1. QPS 控制流程
```
请求进入 → 检查时间窗口 → 检查当前计数 → 等待或直接处理 → 更新计数器
```

### 2. 批量处理流程
```
数据列表 → 分批 → 等待 QPS 限制 → 异步处理批次 → 等待完成
```

### 3. 限流算法
- **时间窗口**：1秒为一个时间窗口
- **计数器**：每个时间窗口内最多处理 MAX_QPS 个请求
- **等待机制**：达到限制时自动等待到下一个时间窗口

## 性能优化建议

### 1. 调整 QPS 限制
- 根据下游接口的实际承载能力设置 MAX_QPS
- 监控下游接口的响应时间和错误率
- 动态调整 QPS 限制

### 2. 优化批量大小
- 根据网络延迟和下游接口性能调整 BATCH_SIZE
- 较小的批量大小可以更好地控制并发
- 较大的批量大小可以提高吞吐量

### 3. 监控和告警
- 监控实际 QPS 和响应时间
- 设置错误率告警
- 记录详细的性能指标

## 扩展功能

### 1. 添加监控指标
```java
// 可以集成 Micrometer 或自定义监控
private final MeterRegistry meterRegistry;

public void recordMetrics(String operation, long duration) {
    meterRegistry.timer("flush_image_duration").record(duration, TimeUnit.MILLISECONDS);
}
```

### 2. 支持多种限流策略
```java
// 可以支持滑动窗口、漏桶等算法
public interface RateLimiter {
    boolean tryAcquire();
    void acquire();
}
```

### 3. 配置外部化
```java
// 将配置参数外部化到配置文件
@Value("${flush.qps.max:100}")
private int maxQPS;

@Value("${flush.batch.size:10}")
private int batchSize;
```

## 注意事项

1. **线程安全**：所有方法都是线程安全的，支持并发调用
2. **资源管理**：合理设置线程池大小，避免资源耗尽
3. **异常处理**：单个请求失败不会影响整体流程
4. **性能监控**：定期监控 QPS 和响应时间指标
5. **配置调优**：根据实际业务场景调整参数

## 测试

运行测试类验证功能：
```bash
mvn test -Dtest=FlushDataTest
```

测试包括：
- 基本 QPS 控制测试
- 并发 QPS 控制测试
- QPS 动态调整测试
