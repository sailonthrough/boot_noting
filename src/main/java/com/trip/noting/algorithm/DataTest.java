package com.trip.noting.algorithm;

import com.alibaba.fastjson2.JSON;
import com.trip.noting.controller.HelloController;
import org.junit.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class DataTest {


    // LRU  最近最常用的缓存   时间复杂度   put  get  clear

    // HasHMap   key   value    k   v    LinkList   淘汰策略




    public static void main(String[] args) {
        String str = "aaa123bbb124";
        // String substring = str.substring(3, 4);
        // System.out.println(substring);
        String s = maxSubString(str);
        System.out.println(s);
    }

    // 给定一个字符串，输出字符串中连续最长的数字串。如果存在长度一样的连续数字串
    //（多个最长的数字串），返回最大的连续数字串。样例：
    // abcd12345ed125ss123058789， 输出123058789。
    // 字符串长度在[0, 50000]范围内。

    public String maxSubString(String s) {
        // 135   145
        if (s == null || s.isEmpty()) {
            return "";
        }
        int left = -1, right = -1;
        int maxLength = 0;
        String resStr = "";
        //
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                // 定位到左边的窗口
                if (left == -1) {
                    left = i;
                } else {
                    right = i;
                }
            } else {
                if (left >= 0 && right >= 0 && right > left) {
                    int tempMaxLength = right - left + 1;
                    String substring = s.substring(left, right+1);
                    if (tempMaxLength == maxLength) {
                        // 对比两个字符串
                        resStr = compareStr(resStr, substring);
                    } else if (tempMaxLength > maxLength) {
                        resStr = substring;
                    }
                    maxLength=Math.max(maxLength,tempMaxLength);
                }
                left = -1;
                right = -1;
            }
        }
        if (left >= 0 && right >= 0 && right > left) {
            int tempMaxLength = right - left + 1;
            String substring = s.substring(left, right+1);
            if (tempMaxLength == maxLength) {
                // 对比两个字符串
                resStr = compareStr(resStr, substring);
            } else if (tempMaxLength > maxLength) {
                resStr = substring;
            }
        }
        return resStr;
    }

    private static String compareStr(String resStr, String newStr) {
        for (int i = 0; i < resStr.length(); i++) {
            char c1 = resStr.charAt(i);
            char c2 = newStr.charAt(i);
            if (c1 > c2) {
                return resStr;
            } else if (c1 < c2) {
                return newStr;
            }
        }
        return resStr;
    }


    @Test
    public void testSkipList() {
        long l = System.currentTimeMillis() / 1000;
        long epochSecond = LocalDateTime.parse("2026-08-30 00:00:00", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).atZone(ZoneId.systemDefault()).toEpochSecond();
        System.out.println(l);
        System.out.println(epochSecond);
    }

    @Test
    public void testSkipList1() {
        List<HelloController.User> users = JSON.parseArray("", HelloController.User.class);
        System.out.println(users);

        Queue<Integer> queue = new ArrayDeque<>();
        // Stack<Integer> stack = new ArrayDeque<>();
        // stack.push()
        // queue.offer()

        Deque<Integer> stack = new ArrayDeque<>();
        stack.push(1);   // 入栈
        stack.push(2);

    }

    @Test
    public void testTime() throws InterruptedException {


    }
}
