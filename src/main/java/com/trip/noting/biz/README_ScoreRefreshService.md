# ScoreRefreshService 分数防抖刷新服务

## 概述

`ScoreRefreshService` 是一个用于处理业务数据频繁变动时分数重新计算的防抖服务。它解决了短时间内多次数据变更导致的重复计算问题，通过Redis实现高效的防抖机制。

## 核心特性

### 1. 防抖机制
- **时间窗口**: 5分钟内多次变动只刷新一次
- **自动去重**: 使用Redis ZSet数据结构自动去重
- **时间排序**: 按到期时间戳自动排序，优先处理到期的数据
- **智能清理**: 只清理已处理且时间戳一致的数据，避免误删

### 2. 并发安全
- **原子操作**: 使用Redis的原子操作避免并发问题
- **分布式锁**: 防止集群环境下多个节点同时执行
- **Lua脚本**: 使用Lua脚本确保操作的原子性
- **节点标识**: 每个节点有唯一标识，便于监控和调试
- **锁重试机制**: 支持锁获取失败时的重试

### 3. 性能优化
- **ZSet数据结构**: 使用Redis ZSet，支持时间排序和范围查询
- **增量处理**: 只处理到期的数据，无需全量扫描
- **批量处理**: 支持批量标记和批量获取
- **分页查询**: 支持大量数据的分页处理
- **时间窗口**: 精确的时间控制，避免无效处理

## 主要方法

### 核心方法

#### `markForRefresh(String bizId)`
标记业务ID需要刷新分数
```java
scoreRefreshService.markForRefresh("biz_001");
```

#### `markForRefreshBatch(Set<String> bizIds)`
批量标记多个业务ID
```java
Set<String> bizIds = new HashSet<>(Arrays.asList("biz_001", "biz_002"));
scoreRefreshService.markForRefreshBatch(bizIds);
```

#### `scheduledScoreRefresh()`
定时任务，每5分钟执行一次分数刷新（自动调用）

#### `manualRefresh()`
手动触发分数刷新
```java
boolean success = scoreRefreshService.manualRefresh();
```

### 查询方法

#### `getPendingRefreshCount()`
获取当前待刷新的业务ID数量
```java
long count = scoreRefreshService.getPendingRefreshCount();
```

#### `isPendingForRefresh(String bizId)`
检查指定业务ID是否在待刷新队列中
```java
boolean pending = scoreRefreshService.isPendingForRefresh("biz_001");
```

#### `getQueueStatistics()`
获取队列统计信息
```java
String stats = scoreRefreshService.getQueueStatistics();
// 输出: "Pending refresh count: 5, Next expire in: 120 seconds, Last expire in: 300 seconds"
```

### 管理方法

#### `removeFromRefreshQueue(String bizId)`
从刷新队列中移除指定的业务ID
```java
boolean removed = scoreRefreshService.removeFromRefreshQueue("biz_001");
```

#### `getPendingRefreshBizIdsBatch(long start, long count)`
分页获取待刷新的业务ID
```java
Set<String> batch = scoreRefreshService.getPendingRefreshBizIdsBatch(0, 10);
```

#### `getBizIdExpireTime(String bizId)`
获取指定业务ID的到期时间
```java
Long expireTime = scoreRefreshService.getBizIdExpireTime("biz_001");
```

#### `getExpiringBizIds(long seconds)`
获取即将到期的业务ID
```java
Set<String> expiring = scoreRefreshService.getExpiringBizIds(60); // 60秒内到期
```

#### `cleanupExpiredBizIds()`
清理过期的业务ID
```java
long cleaned = scoreRefreshService.cleanupExpiredBizIds();
```

### 集群管理方法

#### `getNodeInfo()`
获取当前节点信息
```java
String nodeInfo = scoreRefreshService.getNodeInfo();
```

#### `getLockStatus()`
检查分布式锁状态
```java
String lockStatus = scoreRefreshService.getLockStatus();
```

#### `forceReleaseLock()`
强制释放锁（紧急情况使用）
```java
boolean released = scoreRefreshService.forceReleaseLock();
```

#### `getClusterHealthStatus()`
获取集群健康状态
```java
String health = scoreRefreshService.getClusterHealthStatus();
```

## 使用示例

### 1. 基本使用
```java
@Service
public class BusinessService {
    
    @Autowired
    private ScoreRefreshService scoreRefreshService;
    
    public void updateBusinessData(String bizId) {
        // 更新业务数据
        // ...
        
        // 标记需要刷新分数
        scoreRefreshService.markForRefresh(bizId);
    }
}
```

### 2. 批量处理
```java
public void batchUpdateBusinessData(List<String> bizIds) {
    // 批量更新业务数据
    // ...
    
    // 批量标记需要刷新分数
    scoreRefreshService.markForRefreshBatch(new HashSet<>(bizIds));
}
```

### 3. 监控和调试
```java
// 获取队列状态
long pendingCount = scoreRefreshService.getPendingRefreshCount();
String stats = scoreRefreshService.getQueueStatistics();

// 检查特定业务ID状态
boolean isPending = scoreRefreshService.isPendingForRefresh("biz_001");

// 手动刷新（用于测试或紧急情况）
boolean success = scoreRefreshService.manualRefresh();
```

## 配置说明

### Redis配置
确保Redis配置正确：
```yaml
spring:
  redis:
    host: 127.0.0.1
    port: 6379
    # 集群环境下的配置
    cluster:
      nodes:
        - 127.0.0.1:7001
        - 127.0.0.1:7002
        - 127.0.0.1:7003
    # 连接池配置
    lettuce:
      pool:
        max-active: 8
        max-idle: 8
        min-idle: 0
        max-wait: -1ms
```

### 定时任务配置
服务使用Spring的`@Scheduled`注解，确保启用定时任务：
```java
@EnableScheduling
@SpringBootApplication
public class Application {
    // ...
}
```

## 性能特点

### 优势
1. **高效存储**: 使用Redis ZSet，O(log N)时间复杂度添加/删除
2. **自动去重**: 无需手动处理重复数据
3. **时间排序**: 自动按到期时间排序，优先处理到期的数据
4. **增量处理**: 只处理到期的数据，无需全量扫描
5. **原子操作**: 避免并发问题
6. **智能清理**: 时间戳一致性检查，避免误删数据
7. **内存友好**: 自动过期清理，支持范围查询

### 适用场景
- 高频数据变更场景
- 需要防抖处理的业务逻辑
- 分布式环境下的分数计算
- 大量数据的批量处理

## 注意事项

1. **Redis依赖**: 服务完全依赖Redis，确保Redis可用性
2. **内存使用**: 大量业务ID会占用Redis内存，注意监控
3. **网络延迟**: Redis网络延迟会影响服务性能
4. **业务实现**: `calculateScore`方法需要根据具体业务实现
5. **集群环境**: 确保所有节点时间同步，避免时间戳不一致
6. **分布式锁**: 锁超时时间要合理设置，避免死锁
7. **节点标识**: 每个节点应有唯一标识，便于问题排查

## 扩展建议

1. **监控告警**: 添加队列大小和刷新频率监控
2. **降级策略**: Redis不可用时的降级处理
3. **批量优化**: 根据业务量调整批量处理大小
4. **异步处理**: 分数计算可以异步执行，提高响应速度
