package com.trip.noting.algorithm;

import java.util.Random;

/**
 * 跳表实现
 */
public class SkipList<T extends Comparable<T>> {
    
    // 常量定义
    private static final int MAX_LEVEL = 32;    // 最大层数
    private static final double P = 0.5;        // 节点晋升概率
    
    // 成员变量
    private SkipListNode<T> head;               // 头节点（哑节点）
    private int currentLevel;                   // 当前最大层数
    private int size;                           // 元素个数
    private Random random;                      // 随机数生成器
    
    /**
     * 构造函数
     */
    public SkipList() {
        this.head = new SkipListNode<>(null, MAX_LEVEL);
        this.currentLevel = 0;
        this.size = 0;
        this.random = new Random();
    }
    
    /**
     * 随机生成节点层数（抛硬币法）
     */
    private int randomLevel() {
        int level = 0;
        // 当随机数小于 P 且未超过最大层数时，层数+1
        while (random.nextDouble() < P && level < MAX_LEVEL) {
            level++;
        }
        return level;
    }
    
    /**
     * 查找操作 - 核心方法
     * @return 更新路径数组，记录每层的前驱节点
     */
    private SkipListNode<T>[] find(T value) {
        SkipListNode<T>[] update = new SkipListNode[MAX_LEVEL + 1];
        SkipListNode<T> current = head;
        
        // 从最高层开始查找
        for (int i = currentLevel; i >= 0; i--) {
            // 当前层向前查找，直到下一个节点为null或值大于等于目标值
            while (current.forwards[i] != null 
                    && current.forwards[i].value.compareTo(value) < 0) {
                current = current.forwards[i];
            }
            // 记录当前层的前驱节点
            update[i] = current;
        }
        return update;
    }
    
    /**
     * 查找元素是否存在
     */
    public boolean contains(T value) {
        if (value == null) return false;
        
        SkipListNode<T> current = head;
        // 从最高层开始查找
        for (int i = currentLevel; i >= 0; i--) {
            while (current.forwards[i] != null 
                    && current.forwards[i].value.compareTo(value) < 0) {
                current = current.forwards[i];
            }
        }
        
        // 检查最底层的下一个节点
        current = current.forwards[0];
        return current != null && current.value.equals(value);
    }
    
    /**
     * 插入元素
     */
    public void insert(T value) {
        if (value == null) return;
        
        // 1. 查找插入位置的前驱节点路径
        SkipListNode<T>[] update = find(value);
        
        // 2. 检查是否已存在
        SkipListNode<T> current = update[0].forwards[0];
        if (current != null && current.value.equals(value)) {
            // 如果允许重复值，可以修改这里
            return; // 值已存在
        }
        
        // 3. 随机生成新节点的层数
        int newLevel = randomLevel();
        
        // 4. 如果新节点层数高于当前层数，更新头节点指针
        if (newLevel > currentLevel) {
            for (int i = currentLevel + 1; i <= newLevel; i++) {
                update[i] = head;
            }
            currentLevel = newLevel;
        }
        
        // 5. 创建新节点
        SkipListNode<T> newNode = new SkipListNode<>(value, newLevel);
        
        // 6. 逐层更新指针（类似链表插入）
        for (int i = 0; i <= newLevel; i++) {
            newNode.forwards[i] = update[i].forwards[i];
            update[i].forwards[i] = newNode;
        }
        
        size++;
    }
    
    /**
     * 删除元素
     */
    public boolean delete(T value) {
        if (value == null) return false;
        
        // 1. 查找要删除节点的前驱节点路径
        SkipListNode<T>[] update = find(value);
        
        // 2. 检查要删除的节点是否存在
        SkipListNode<T> nodeToDelete = update[0].forwards[0];
        if (nodeToDelete == null || !nodeToDelete.value.equals(value)) {
            return false; // 节点不存在
        }
        
        // 3. 逐层更新指针（类似链表删除）
        for (int i = 0; i <= nodeToDelete.level; i++) {
            if (update[i].forwards[i] != nodeToDelete) {
                break; // 当前层没有该节点
            }
            update[i].forwards[i] = nodeToDelete.forwards[i];
        }
        
        // 4. 更新跳表层数
        while (currentLevel > 0 && head.forwards[currentLevel] == null) {
            currentLevel--;
        }
        
        size--;
        return true;
    }
    
    /**
     * 获取第一个元素（最小值）
     */
    public T getFirst() {
        SkipListNode<T> first = head.forwards[0];
        return first != null ? first.value : null;
    }
    
    /**
     * 获取元素个数
     */
    public int size() {
        return size;
    }
    
    /**
     * 判断是否为空
     */
    public boolean isEmpty() {
        return size == 0;
    }
    
    /**
     * 清空跳表
     */
    public void clear() {
        for (int i = 0; i <= MAX_LEVEL; i++) {
            head.forwards[i] = null;
        }
        currentLevel = 0;
        size = 0;
    }
    
    /**
     * 可视化打印跳表
     */
    public void print() {
        System.out.println("SkipList (size=" + size + ", level=" + currentLevel + "):");
        
        for (int i = currentLevel; i >= 0; i--) {
            System.out.print("Level " + i + ": head");
            SkipListNode<T> node = head.forwards[i];
            
            while (node != null) {
                System.out.print(" -> " + node.value);
                node = node.forwards[i];
            }
            System.out.println();
        }
        System.out.println();
    }
    
    /**
     * 获取指定层数的链表
     */
    public void printLevel(int level) {
        if (level < 0 || level > currentLevel) {
            System.out.println("Invalid level");
            return;
        }
        
        System.out.print("Level " + level + ": ");
        SkipListNode<T> node = head.forwards[level];
        
        while (node != null) {
            System.out.print(node.value + " ");
            node = node.forwards[level];
        }
        System.out.println();
    }
}