package com.trip.noting.algorithm;

class SkipListNode<T extends Comparable<T>> {
    T value;                    // 节点值
    int level;                  // 节点所在层数
    SkipListNode<T>[] forwards; // 前进指针数组

    @SuppressWarnings("unchecked")
    public SkipListNode(T value, int level) {
        this.value = value;
        this.level = level;
        this.forwards = new SkipListNode[level + 1]; // 包含level 0
    }

    @Override
    public String toString() {
        return String.format("Node{value=%s, level=%d}", value, level);
    }
}