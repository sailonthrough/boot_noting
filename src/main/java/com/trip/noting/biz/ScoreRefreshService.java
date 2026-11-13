package com.trip.noting.biz;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 分数防抖刷新服务
 * 用于处理业务数据频繁变动时的分数重新计算
 * 5分钟内多次变动只刷新一次，避免重复计算
 */
@Slf4j
@Service
public class ScoreRefreshService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // Redis key前缀
    private static final String PENDING_REFRESH_ZSET_KEY = "score:refresh:pending_zset";
    private static final String REFRESH_LOCK_KEY = "score:refresh:lock";
    
    // 防抖时间间隔（分钟）
    private static final int DEBOUNCE_MINUTES = 5;
    
    // 锁超时时间（秒）
    private static final int LOCK_TIMEOUT_SECONDS = 30;
    
    // 批量处理大小
    private static final int BATCH_SIZE = 100;
    
    // 时间戳精度（毫秒）

    private static final long TIMESTAMP_PRECISION = 1000L;
    
    // 分布式锁重试次数
    private static final int LOCK_RETRY_TIMES = 3;
    
    // 分布式锁重试间隔（毫秒）
    private static final long LOCK_RETRY_INTERVAL = 1000L;
    
    // 节点标识（用于分布式锁）
    private final String nodeId = generateNodeId();

    /**
     * 初始化服务
     */
    @PostConstruct
    public void init() {
        log.info("ScoreRefreshService initialized on node {} with {} minutes debounce interval", 
            nodeId, DEBOUNCE_MINUTES);
    }

    /**
     * 生成唯一的节点标识
     * 
     * @return 节点标识
     */
    private String generateNodeId() {
        try {
            // 使用主机名 + 随机数生成唯一标识
            String hostname = java.net.InetAddress.getLocalHost().getHostName();
            String random = String.valueOf(System.nanoTime() % 10000);
            return hostname + "-" + random;
        } catch (Exception e) {
            // 如果获取主机名失败，使用随机UUID
            return "node-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        }
    }

    /**
     * 标记业务ID需要刷新分数
     * 将biz_id存储到Redis中，使用ZSet数据结构，score为时间戳
     * 
     * @param bizId 业务ID
     */
    public void markForRefresh(String bizId) {
        if (bizId == null || bizId.trim().isEmpty()) {
            log.warn("Invalid bizId: {}", bizId);
            return;
        }

        try {
            // 计算到期时间戳（当前时间 + 防抖时间）
            long expireTimestamp = System.currentTimeMillis() + (DEBOUNCE_MINUTES * 60 * 1000L);
            
            // 使用Redis的ZSet数据结构，score为到期时间戳，自动去重
            redisTemplate.opsForZSet().add(PENDING_REFRESH_ZSET_KEY, bizId, expireTimestamp);
            
            log.debug("Marked bizId {} for score refresh at timestamp {}", bizId, expireTimestamp);
        } catch (Exception e) {
            log.error("Failed to mark bizId {} for refresh", bizId, e);
        }
    }

    /**
     * 批量标记多个业务ID需要刷新分数
     * 
     * @param bizIds 业务ID列表
     */
    public void markForRefreshBatch(Set<String> bizIds) {
        if (bizIds == null || bizIds.isEmpty()) {
            log.warn("Empty bizIds list");
            return;
        }

        for (String bizId : bizIds) {
            markForRefresh(bizId);
        }
        
        log.info("Marked {} bizIds for batch score refresh", bizIds.size());
    }

    /**
     * 定时任务：每5分钟执行一次分数刷新
     * 获取所有待刷新的业务ID，执行分数计算
     */
    @Scheduled(fixedRate = DEBOUNCE_MINUTES, timeUnit = TimeUnit.MINUTES)
    public void scheduledScoreRefresh() {
        if (!acquireRefreshLock()) {
            log.info("Another refresh task is running, skip this execution");
            return;
        }

        try {
            log.info("Starting scheduled score refresh task");
            
            // 使用原子操作获取并清理待刷新的业务ID
            Set<String> processedBizIds = processPendingBizIds();
            
            if (processedBizIds.isEmpty()) {
                log.info("No pending bizIds for score refresh");
                return;
            }

            log.info("Score refresh task completed for {} bizIds", processedBizIds.size());
            
        } catch (Exception e) {
            log.error("Error during scheduled score refresh", e);
        } finally {
            releaseRefreshLock();
        }
    }

    /**
     * 获取所有待刷新的业务ID
     * 
     * @return 待刷新的业务ID集合
     */
    private Set<String> getPendingRefreshBizIds() {
        try {
            // 直接从ZSet中获取所有元素，性能更好
            Set<Object> members = redisTemplate.opsForZSet().range(PENDING_REFRESH_ZSET_KEY, 0, -1);
            if (members == null || members.isEmpty()) {
                return new java.util.HashSet<>();
            }

            Set<String> bizIds = new java.util.HashSet<>();
            for (Object member : members) {
                if (member instanceof String) {
                    bizIds.add((String) member);
                }
            }
            
            return bizIds;
        } catch (Exception e) {
            log.error("Failed to get pending refresh bizIds", e);
            return new java.util.HashSet<>();
        }
    }

    /**
     * 原子性地处理待刷新的业务ID
     * 使用Redis的原子操作来避免并发问题
     * 
     * @return 已处理的业务ID集合
     */
    private Set<String> processPendingBizIds() {
        Set<String> processedBizIds = new java.util.HashSet<>();
        
        try {
            long currentTimestamp = System.currentTimeMillis();
            
            // 使用Lua脚本原子性地获取并删除到期的业务ID
            String luaScript = 
                "local currentTime = tonumber(ARGV[1]) " +
                "local expiredMembers = redis.call('zrangebyscore', KEYS[1], 0, currentTime) " +
                "if #expiredMembers > 0 then " +
                "    redis.call('zremrangebyscore', KEYS[1], 0, currentTime) " +
                "end " +
                "return expiredMembers";
            
            DefaultRedisScript<java.util.List> script = new DefaultRedisScript<>();
            script.setScriptText(luaScript);
            script.setResultType(java.util.List.class);
            
            @SuppressWarnings("unchecked")
            java.util.List<Object> expiredMembers = redisTemplate.execute(script, 
                java.util.Arrays.asList(PENDING_REFRESH_ZSET_KEY), String.valueOf(currentTimestamp));
            
            if (expiredMembers == null || expiredMembers.isEmpty()) {
                log.debug("Node {}: No expired bizIds found at timestamp {}", nodeId, currentTimestamp);
                return processedBizIds;
            }

            // 将Object转换为String
            for (Object member : expiredMembers) {
                if (member instanceof String) {
                    processedBizIds.add((String) member);
                }
            }

            if (!processedBizIds.isEmpty()) {
                log.info("Node {}: Found {} expired bizIds to process at timestamp {}", 
                    nodeId, processedBizIds.size(), currentTimestamp);
                
                // 执行分数刷新逻辑
                refreshScores(processedBizIds);
                
                log.info("Node {}: Successfully processed {} bizIds", nodeId, processedBizIds.size());
            }
            
        } catch (Exception e) {
            log.error("Error during atomic processing of pending bizIds", e);
        }
        
        return processedBizIds;
    }

    /**
     * 执行分数刷新逻辑
     * 这里需要根据具体业务实现分数计算
     * 
     * @param bizIds 需要刷新分数的业务ID集合
     */
    private void refreshScores(Set<String> bizIds) {
        for (String bizId : bizIds) {
            try {
                // TODO: 实现具体的分数计算逻辑
                // 这里可以调用具体的业务服务来计算分数
                calculateScore(bizId);
                
                log.debug("Score refreshed for bizId: {}", bizId);
            } catch (Exception e) {
                log.error("Failed to refresh score for bizId: {}", bizId, e);
            }
        }
    }

    /**
     * 计算指定业务ID的分数
     * 这是一个示例方法，需要根据实际业务逻辑实现
     * 
     * @param bizId 业务ID
     */
    private void calculateScore(String bizId) {
        // TODO: 实现具体的分数计算逻辑
        // 例如：
        // 1. 查询业务数据
        // 2. 根据业务规则计算分数
        // 3. 更新数据库中的分数
        // 4. 发送通知等
        
        log.info("Calculating score for bizId: {}", bizId);
        
        // 模拟计算过程
        try {
            Thread.sleep(100); // 模拟计算耗时
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 清理已处理的业务ID
     * 注意：这个方法在新的实现中已经不需要了，因为processPendingBizIds会直接清空整个Set
     * 保留这个方法是为了向后兼容
     * 
     * @param bizIds 已处理的业务ID集合
     */
    private void clearProcessedBizIds(Set<String> bizIds) {
        // 在新的实现中，processPendingBizIds会直接清空整个Set
        // 所以这个方法已经不需要了，但为了向后兼容保留
        log.debug("clearProcessedBizIds called for {} bizIds, but not needed in new implementation", bizIds.size());
    }

    /**
     * 获取分布式刷新锁，防止集群环境下多个节点同时执行
     * 
     * @return 是否成功获取锁
     */
    private boolean acquireRefreshLock() {
        for (int i = 0; i < LOCK_RETRY_TIMES; i++) {
            try {
                // 使用节点ID作为锁值，便于调试和监控
                String lockValue = nodeId + "-" + System.currentTimeMillis();
                Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(REFRESH_LOCK_KEY, lockValue, LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                
                if (Boolean.TRUE.equals(acquired)) {
                    log.info("Node {} acquired refresh lock: {}", nodeId, lockValue);
                    return true;
                }
                
                // 如果获取锁失败，等待一段时间后重试
                if (i < LOCK_RETRY_TIMES - 1) {
                    log.debug("Node {} failed to acquire lock, retrying in {} ms (attempt {}/{})", 
                        nodeId, LOCK_RETRY_INTERVAL, i + 1, LOCK_RETRY_TIMES);
                    Thread.sleep(LOCK_RETRY_INTERVAL);
                }
            } catch (Exception e) {
                log.error("Failed to acquire refresh lock on attempt {}", i + 1, e);
                if (i < LOCK_RETRY_TIMES - 1) {
                    try {
                        Thread.sleep(LOCK_RETRY_INTERVAL);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        log.warn("Node {} failed to acquire refresh lock after {} attempts", nodeId, LOCK_RETRY_TIMES);
        return false;
    }

    /**
     * 释放分布式刷新锁
     * 使用Lua脚本确保只有持有锁的节点才能释放锁
     */
    private void releaseRefreshLock() {
        try {
            // 使用Lua脚本确保原子性释放锁
            String luaScript = 
                "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                "    return redis.call('del', KEYS[1]) " +
                "else " +
                "    return 0 " +
                "end";
            
            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            script.setScriptText(luaScript);
            script.setResultType(Long.class);
            
            // 获取当前锁值
            String currentLockValue = (String) redisTemplate.opsForValue().get(REFRESH_LOCK_KEY);
            if (currentLockValue != null && currentLockValue.startsWith(nodeId)) {
                Long result = redisTemplate.execute(script, 
                    java.util.Arrays.asList(REFRESH_LOCK_KEY), currentLockValue);
                
                if (result != null && result > 0) {
                    log.info("Node {} successfully released refresh lock", nodeId);
                } else {
                    log.warn("Node {} failed to release lock, lock may have been taken by another node", nodeId);
                }
            } else {
                log.warn("Node {} cannot release lock, lock value mismatch or lock not found", nodeId);
            }
        } catch (Exception e) {
            log.error("Failed to release refresh lock", e);
        }
    }

    /**
     * 手动触发分数刷新（用于测试或紧急情况）
     * 
     * @return 是否成功执行
     */
    public boolean manualRefresh() {
        if (!acquireRefreshLock()) {
            log.warn("Cannot acquire lock for manual refresh");
            return false;
        }

        try {
            log.info("Manual score refresh triggered");
            Set<String> pendingBizIds = getPendingRefreshBizIds();
            
            if (!pendingBizIds.isEmpty()) {
                refreshScores(pendingBizIds);
                clearProcessedBizIds(pendingBizIds);
                log.info("Manual refresh completed for {} bizIds", pendingBizIds.size());
            } else {
                log.info("No pending bizIds for manual refresh");
            }
            
            return true;
        } catch (Exception e) {
            log.error("Error during manual refresh", e);
            return false;
        } finally {
            releaseRefreshLock();
        }
    }

    /**
     * 获取当前待刷新的业务ID数量
     * 
     * @return 待刷新的业务ID数量
     */
    public long getPendingRefreshCount() {
        try {
            // 直接获取ZSet的大小，性能更好
            Long size = redisTemplate.opsForZSet().size(PENDING_REFRESH_ZSET_KEY);
            return size != null ? size : 0;
        } catch (Exception e) {
            log.error("Failed to get pending refresh count", e);
            return 0;
        }
    }

    /**
     * 批量获取待刷新的业务ID（分页方式）
     * 用于处理大量数据时的性能优化
     * 
     * @param start 开始位置
     * @param count 获取数量
     * @return 业务ID集合
     */
    public Set<String> getPendingRefreshBizIdsBatch(long start, long count) {
        try {
            // 使用ZSet的range方法进行分页获取，性能更好
            Set<Object> members = redisTemplate.opsForZSet().range(PENDING_REFRESH_ZSET_KEY, start, start + count - 1);
            if (members == null || members.isEmpty()) {
                return new java.util.HashSet<>();
            }

            Set<String> bizIds = new java.util.HashSet<>();
            for (Object member : members) {
                if (member instanceof String) {
                    bizIds.add((String) member);
                }
            }
            
            return bizIds;
        } catch (Exception e) {
            log.error("Failed to get pending refresh bizIds batch", e);
            return new java.util.HashSet<>();
        }
    }

    /**
     * 检查指定业务ID是否在待刷新队列中
     * 
     * @param bizId 业务ID
     * @return 是否在队列中
     */
    public boolean isPendingForRefresh(String bizId) {
        if (bizId == null || bizId.trim().isEmpty()) {
            return false;
        }

        try {
            return Boolean.TRUE.equals(redisTemplate.opsForZSet().score(PENDING_REFRESH_ZSET_KEY, bizId) != null);
        } catch (Exception e) {
            log.error("Failed to check if bizId {} is pending for refresh", bizId, e);
            return false;
        }
    }

    /**
     * 移除指定的业务ID（用于手动取消刷新）
     * 
     * @param bizId 业务ID
     * @return 是否成功移除
     */
    public boolean removeFromRefreshQueue(String bizId) {
        if (bizId == null || bizId.trim().isEmpty()) {
            return false;
        }

        try {
            Long removed = redisTemplate.opsForZSet().remove(PENDING_REFRESH_ZSET_KEY, bizId);
            boolean success = removed != null && removed > 0;
            if (success) {
                log.debug("Removed bizId {} from refresh queue", bizId);
            }
            return success;
        } catch (Exception e) {
            log.error("Failed to remove bizId {} from refresh queue", bizId, e);
            return false;
        }
    }

    /**
     * 获取待刷新队列的统计信息
     * 
     * @return 统计信息字符串
     */
    public String getQueueStatistics() {
        try {
            long count = getPendingRefreshCount();
            
            StringBuilder stats = new StringBuilder();
            stats.append("Pending refresh count: ").append(count);
            
            // 获取最早和最晚的到期时间
            Set<Object> allMembers = redisTemplate.opsForZSet().range(PENDING_REFRESH_ZSET_KEY, 0, -1);
            if (allMembers != null && !allMembers.isEmpty()) {
                Double minScore = redisTemplate.opsForZSet().score(PENDING_REFRESH_ZSET_KEY, allMembers.iterator().next());
                Double maxScore = redisTemplate.opsForZSet().score(PENDING_REFRESH_ZSET_KEY, allMembers.toArray()[allMembers.size() - 1]);
                
                if (minScore != null && maxScore != null) {
                    long currentTime = System.currentTimeMillis();
                    long minExpireTime = (long) (minScore - currentTime) / 1000;
                    long maxExpireTime = (long) (maxScore - currentTime) / 1000;
                    
                    stats.append(", Next expire in: ").append(minExpireTime).append(" seconds");
                    stats.append(", Last expire in: ").append(maxExpireTime).append(" seconds");
                }
            }
            
            return stats.toString();
        } catch (Exception e) {
            log.error("Failed to get queue statistics", e);
            return "Error getting statistics";
        }
    }

    /**
     * 获取指定业务ID的到期时间
     * 
     * @param bizId 业务ID
     * @return 到期时间戳，如果不存在返回null
     */
    public Long getBizIdExpireTime(String bizId) {
        if (bizId == null || bizId.trim().isEmpty()) {
            return null;
        }

        try {
            Double score = redisTemplate.opsForZSet().score(PENDING_REFRESH_ZSET_KEY, bizId);
            if (score != null) {
                return score.longValue();
            }
            return null;
        } catch (Exception e) {
            log.error("Failed to get expire time for bizId {}", bizId, e);
            return null;
        }
    }

    /**
     * 获取即将到期的业务ID（用于监控和预警）
     * 
     * @param seconds 多少秒内到期
     * @return 即将到期的业务ID集合
     */
    public Set<String> getExpiringBizIds(long seconds) {
        try {
            long currentTime = System.currentTimeMillis();
            long expireTime = currentTime + (seconds * 1000L);
            
            Set<Object> expiringMembers = redisTemplate.opsForZSet().rangeByScore(
                PENDING_REFRESH_ZSET_KEY, currentTime, expireTime);
            
            if (expiringMembers == null || expiringMembers.isEmpty()) {
                return new java.util.HashSet<>();
            }

            Set<String> bizIds = new java.util.HashSet<>();
            for (Object member : expiringMembers) {
                if (member instanceof String) {
                    bizIds.add((String) member);
                }
            }
            
            return bizIds;
        } catch (Exception e) {
            log.error("Failed to get expiring bizIds", e);
            return new java.util.HashSet<>();
        }
    }

    /**
     * 清理过期的业务ID（用于手动清理）
     * 
     * @return 清理的数量
     */
    public long cleanupExpiredBizIds() {
        try {
            long currentTime = System.currentTimeMillis();
            Long removed = redisTemplate.opsForZSet().removeRangeByScore(
                PENDING_REFRESH_ZSET_KEY, 0, currentTime);
            
            if (removed != null && removed > 0) {
                log.info("Node {}: Cleaned up {} expired bizIds", nodeId, removed);
            }
            
            return removed != null ? removed : 0;
        } catch (Exception e) {
            log.error("Node {}: Failed to cleanup expired bizIds", nodeId, e);
            return 0;
        }
    }

    /**
     * 获取集群节点信息
     * 
     * @return 节点信息
     */
    public String getNodeInfo() {
        try {
            return String.format("Node ID: %s, Host: %s, Timestamp: %d", 
                nodeId, 
                java.net.InetAddress.getLocalHost().getHostName(),
                System.currentTimeMillis());
        } catch (Exception e) {
            return String.format("Node ID: %s, Host: Unknown, Timestamp: %d", 
                nodeId, System.currentTimeMillis());
        }
    }

    /**
     * 检查分布式锁状态
     * 
     * @return 锁状态信息
     */
    public String getLockStatus() {
        try {
            String lockValue = (String) redisTemplate.opsForValue().get(REFRESH_LOCK_KEY);
            if (lockValue == null) {
                return "No lock held";
            }
            
            // 检查锁是否过期
            Long ttl = redisTemplate.getExpire(REFRESH_LOCK_KEY, TimeUnit.SECONDS);
            String status = String.format("Lock held by: %s, TTL: %d seconds", 
                lockValue, ttl != null ? ttl : -1);
            
            if (lockValue.startsWith(nodeId)) {
                status += " (Current node)";
            }
            
            return status;
        } catch (Exception e) {
            return "Error checking lock status: " + e.getMessage();
        }
    }

    /**
     * 强制释放锁（用于紧急情况）
     * 注意：只有管理员或监控系统应该调用此方法
     * 
     * @return 是否成功释放
     */
    public boolean forceReleaseLock() {
        try {
            Boolean released = redisTemplate.delete(REFRESH_LOCK_KEY);
            if (Boolean.TRUE.equals(released)) {
                log.warn("Node {}: Force released refresh lock", nodeId);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("Node {}: Failed to force release lock", nodeId, e);
            return false;
        }
    }

    /**
     * 获取集群健康状态
     * 
     * @return 健康状态信息
     */
    public String getClusterHealthStatus() {
        try {
            StringBuilder status = new StringBuilder();
            status.append("Node: ").append(nodeId).append("\n");
            status.append("Lock Status: ").append(getLockStatus()).append("\n");
            status.append("Queue Size: ").append(getPendingRefreshCount()).append("\n");
            status.append("Queue Statistics: ").append(getQueueStatistics()).append("\n");
            status.append("Redis Connection: OK");
            
            return status.toString();
        } catch (Exception e) {
            return String.format("Node: %s, Status: ERROR - %s", nodeId, e.getMessage());
        }
    }

    public void redisCmd(){
        // ===== ZSet 常用命令示例 =====
        
        // 1. 按score范围查询（升序）
        // redis 命令 score升序获取一个元素 ：ZRANGEBYSCORE key 0「时间戳」withscores limit 0 1
        // redis 命令 score升序获取100个元素 ：ZRANGEBYSCORE key 0「时间戳」withscores limit 0 100
        // redis 命令 按照score获取元素获取100个 ：ZRANGEBYSCORE key 0「开始时间戳」「结束时间戳」withscores limit 0 100
        
        // 2. 按score范围查询（降序）
        // redis 命令 score降序获取一个元素 ：ZREVRANGEBYSCORE key +inf「时间戳」withscores limit 0 1
        // redis 命令 score降序获取100个元素 ：ZREVRANGEBYSCORE key +inf「时间戳」withscores limit 0 100
        // redis 命令 按照score和元素的值获取元素 ：ZREVRANGEBYSCORE key +inf「开始时间戳」withscores limit 0 100
        
        // 3. 按索引范围查询
        // ZRANGE key 0 99 withscores  # 获取前100个元素（按score升序）
        // ZREVRANGE key 0 99 withscores  # 获取前100个元素（按score降序）
        
        // 4. 统计和删除命令
        // ZCARD key  # 获取集合大小
        // ZCOUNT key min max  # 统计指定score范围内的元素数量
        // ZREM key member1 member2  # 删除指定元素
        // ZREMRANGEBYSCORE key min max  # 删除指定score范围内的元素
        // ZREMRANGEBYRANK key start stop  # 删除指定排名范围内的元素
        
        // 5. 条件删除命令（类似SQL的WHERE条件）
        // ZREM key member  # 删除指定元素（不检查score）
        // ZREMRANGEBYSCORE key min max  # 删除指定score范围内的所有元素
        // 注意：Redis没有直接支持 "DELETE FROM zsetkey WHERE key='hello' AND score<=200" 的单条命令
        // 但可以通过以下方式实现：
        // 方式1: 先检查score，再删除（两步操作，非原子性）
        // 方式2: 使用Lua脚本实现原子性操作
        // 方式3: 使用ZREMRANGEBYSCORE删除整个范围，然后重新添加符合条件的元素
        
        // 5. 元素操作
        // ZADD key score member  # 添加元素
        // ZSCORE key member  # 获取元素score
        // ZRANK key member  # 获取元素排名（升序，从0开始）
        // ZREVRANK key member  # 获取元素排名（降序，从0开始）
        
        // 补充说明：
        // ZREVRANGEBYSCORE: 按score降序排列（从大到小）
        // ZRANGEBYSCORE: 按score升序排列（从小到大）
        // withscores: 同时返回元素值和score
        // limit offset count: 分页获取，offset从0开始，count为获取数量
        // -inf/+inf: 表示负无穷/正无穷，用于获取所有元素
        // (min (max: 表示开区间，不包含边界值
        // [min [max: 表示闭区间，包含边界值
        
        // ===== 实际使用场景示例 =====
        // 场景1: 获取所有到期的任务（当前时间戳之前）
        // ZRANGEBYSCORE pending_tasks 0 1640995200000 withscores
        
        // 场景2: 获取即将到期的任务（未来5分钟内）
        // ZRANGEBYSCORE pending_tasks 1640995200000 1640995500000 withscores
        
        // 场景3: 获取优先级最高的任务（score最小）
        // ZRANGE pending_tasks 0 0 withscores
        
        // 场景4: 获取优先级最低的任务（score最大）
        // ZREVRANGE pending_tasks 0 0 withscores
        
        // 场景5: 分页获取任务列表
        // ZRANGE pending_tasks 0 9 withscores  # 第1页，每页10条
        // ZRANGE pending_tasks 10 19 withscores  # 第2页，每页10条
        
        // 场景6: 统计特定时间范围内的任务数量
        // ZCOUNT pending_tasks 1640995200000 1640995500000
        
        // ===== 条件删除实现方案 =====
        // 需求：删除元素'hello'，但只有当score<=200时才删除
        
        // 方案1: 两步操作（非原子性，有并发风险）
        // 步骤1: ZSCORE zsetkey hello  # 获取元素score
        // 步骤2: 如果score<=200，则执行 ZREM zsetkey hello
        
        // 方案2: Lua脚本实现（推荐，原子性操作）
        // 脚本内容：
        // local member = ARGV[1]
        // local maxScore = tonumber(ARGV[2])
        // local score = redis.call('zscore', KEYS[1], member)
        // if score and tonumber(score) <= maxScore then
        //     return redis.call('zrem', KEYS[1], member)
        // else
        //     return 0
        // end
        
        // 方案3: 批量操作（适合删除多个元素）
        // ZREMRANGEBYSCORE zsetkey 0 200  # 删除score<=200的所有元素
        // 然后重新添加需要保留的元素
        
        // 实际使用示例：
        // 删除过期任务：ZREMRANGEBYSCORE pending_tasks 0 1640995200000
        // 删除低优先级任务：ZREMRANGEBYSCORE task_queue 0 100
        // 删除指定时间范围内的任务：ZREMRANGEBYSCORE scheduled_tasks 1640995200000 1640995500000
    }

    /**
     * 条件删除元素（类似SQL的WHERE条件）
     * 只有当元素的score满足条件时才删除
     * 
     * @param bizId 业务ID
     * @param maxScore 最大score值（包含）
     * @return 是否成功删除
     */
    public boolean removeBizIdIfScoreLessThan(String bizId, double maxScore) {
        if (bizId == null || bizId.trim().isEmpty()) {
            return false;
        }

        try {
            // 使用Lua脚本实现原子性条件删除
            String luaScript = 
                "local member = ARGV[1] " +
                "local maxScore = tonumber(ARGV[2]) " +
                "local score = redis.call('zscore', KEYS[1], member) " +
                "if score and tonumber(score) <= maxScore then " +
                "    return redis.call('zrem', KEYS[1], member) " +
                "else " +
                "    return 0 " +
                "end";
            
            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            script.setScriptText(luaScript);
            script.setResultType(Long.class);
            
            Long result = redisTemplate.execute(script, 
                java.util.Arrays.asList(PENDING_REFRESH_ZSET_KEY), bizId, String.valueOf(maxScore));
            
            boolean removed = result != null && result > 0;
            if (removed) {
                log.info("Node {}: Conditionally removed bizId {} with score <= {}", nodeId, bizId, maxScore);
            } else {
                log.debug("Node {}: BizId {} not removed, score condition not met", nodeId, bizId);
            }
            
            return removed;
        } catch (Exception e) {
            log.error("Node {}: Failed to conditionally remove bizId {}", nodeId, bizId, e);
            return false;
        }
    }

    /**
     * 批量条件删除元素
     * 删除所有score在指定范围内的元素
     * 
     * @param minScore 最小score值（包含）
     * @param maxScore 最大score值（包含）
     * @return 删除的元素数量
     */
    public long removeBizIdsByScoreRange(double minScore, double maxScore) {
        try {
            Long removed = redisTemplate.opsForZSet().removeRangeByScore(
                PENDING_REFRESH_ZSET_KEY, minScore, maxScore);
            
            long count = removed != null ? removed : 0;
            if (count > 0) {
                log.info("Node {}: Removed {} bizIds with score between {} and {}", 
                    nodeId, count, minScore, maxScore);
            }
            
            return count;
        } catch (Exception e) {
            log.error("Node {}: Failed to remove bizIds by score range", nodeId, e);
            return 0;
        }
    }
}
