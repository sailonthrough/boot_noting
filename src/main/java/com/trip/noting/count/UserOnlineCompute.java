package com.trip.noting.count;

import com.alibaba.fastjson2.JSON;
import lombok.Data;
import org.apache.commons.collections4.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

public class UserOnlineCompute {

    // 字符串计算  数字  加减  括号  空格忽略
    public int calculate(String s) {
        // Deque<Integer> stack = new ArrayDeque<>();

        if (s == null || s.isEmpty()) {
            return 0;
        }
        Stack<Character> stack = new Stack<>();
        int compute = 0;
        boolean sum = true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            // 判断是数字
            if (c >= '0' && c <= '9') {
                if (stack.empty()) {
                    if (sum) {
                        compute = compute + (c - '0');
                    } else {
                        compute = compute - (c - '0');
                    }
                } else {
                    stack.push(c);
                }
            } else if (c == '(') {
                stack.push(c);
            } else if (c == ')') {
                // 数据出栈计算
                int tempCount = 0;
                Character pop = stack.pop();
                Character peek = stack.peek();
                if (peek == '+') {
                    tempCount = tempCount + (pop - '0');
                } else if (peek == '-') {
                    tempCount = tempCount - (pop - '0');
                } else if (peek == '(') {
                    compute = compute + tempCount;
                }
                stack.pop();
            } else if (c == ' ') {
                continue;
            } else if (c == '+' && stack.empty()) {
                sum = true;
            } else if (c == '-' && stack.empty()) {
                sum = false;
            } else if (c == '+' && !stack.empty()) {
                stack.push(c);
            } else if (c == '-' && !stack.empty()) {
                stack.push(c);
            }
        }
        return compute;
    }

    // 符号栈 + 累加 result/sign 的标准实现，支持多位数、加减、括号、空格
    public int calculate1(String s) {
        if (s == null || s.isEmpty()) {
            return 0;
        }
        Stack<Integer> stack = new Stack<>();
        int result = 0;
        int number = 0;
        int sign = 1;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                number = number * 10 + (c - '0');
            } else if (c == '+') {
                result += sign * number;
                number = 0;
                sign = 1;
            } else if (c == '-') {
                result += sign * number;
                number = 0;
                sign = -1;
            } else if (c == '(') {
                // 把当前结果和符号入栈，进入括号后重新开始累加
                stack.push(result);
                stack.push(sign);
                result = 0;
                sign = 1;
            } else if (c == ')') {
                result += sign * number;
                number = 0;
                // 弹出括号外的符号和结果，与括号内的结果合并
                result = result * stack.pop() + stack.pop();
            } else if (c == ' ') {
                continue;
            }
        }
        result += sign * number;
        return result;
    }


    public static void main(String[] args) {
        List<Object> list = new ArrayList<>();
        list.add(new LoginRecord() {
            {
                this.userId = 1;
                this.loginTime = 2;
                this.logoutTime = 3;
            }
        });
        list.add(new LoginRecord() {
            {
                this.userId = 1;
                this.loginTime = 10;
                this.logoutTime = 12;
            }
        });
        list.add(new LoginRecord() {
            {
                this.userId = 1;
                this.loginTime = 3;
                this.logoutTime = 8;
            }
        });
        // list.sort(Comparator.comparing((k1, k2) -> k1.)); System.out.println(JSON.toJSONString(list));

    }

    /**
     * 示例:
     * 用户A: [1,5], [3,8], [10,12]
     * 合并后: [1,8], [10,12] → 总时长 = 7 + 2 = 9
     * <p>
     * 用户B: [2,6]
     * 总时长 = 4
     */

    public Map<Integer, Long> calcOnlineDuration(List<LoginRecord> records) {
        Map<Integer, Long> resMap = new HashMap<>();
        // 合并区间再SUM
        if (CollectionUtils.isEmpty(records)) {
            return resMap;
        }
        Map<Integer, List<LoginRecord>> userOnlineListMap = records.stream().collect(Collectors.groupingBy(x -> x.userId));
        userOnlineListMap.forEach((k, v) -> {
            v.sort(Comparator.comparingLong(x->x.loginTime));
            List<LoginRecord> newTimeList = new ArrayList<>();
            long startTime_ = 0;
            long endTime_ = 0;
            for (int i = 0; i < v.size(); i++) {
                LoginRecord loginRecord = v.get(i);
                if (startTime_ == 0 && endTime_ == 0) {
                    startTime_ = loginRecord.loginTime;
                    endTime_ = loginRecord.logoutTime;
                    continue;
                }
                while (i < v.size() && loginRecord.loginTime >= endTime_) {
                    endTime_ = loginRecord.logoutTime;
                    i++;
                }
                LoginRecord newRecord = new LoginRecord();
                newRecord.userId = loginRecord.userId;
                newRecord.loginTime = startTime_;
                newRecord.logoutTime = endTime_;
                newTimeList.add(newRecord);
                startTime_ = 0;
                endTime_ = 0;
            }
            long sum = 0;
            for (LoginRecord loginRecord : newTimeList) {
                sum = sum + (loginRecord.logoutTime - loginRecord.loginTime);
            }
            resMap.put(k, sum);
        });
        return resMap;
    }

    public Map<Integer, Long> calcOnlineDuration1(List<LoginRecord> records) {
        Map<Integer, Long> resMap = new HashMap<>();
        if (CollectionUtils.isEmpty(records)) {
            return resMap;
        }
        Map<Integer, List<LoginRecord>> userOnlineListMap = records.stream().collect(Collectors.groupingBy(x -> x.userId));
        userOnlineListMap.forEach((userId, list) -> {
            list.sort(Comparator.comparingLong(x -> x.loginTime));

            long curStart = list.get(0).loginTime;
            long curEnd = list.get(0).logoutTime;
            long sum = 0;

            for (int i = 1; i < list.size(); i++) {
                LoginRecord record = list.get(i);
                if (record.loginTime <= curEnd) {
                    curEnd = Math.max(curEnd, record.logoutTime);
                } else {
                    sum += curEnd - curStart;
                    curStart = record.loginTime;
                    curEnd = record.logoutTime;
                }
            }
            sum += curEnd - curStart;

            resMap.put(userId, sum);
        });
        return resMap;
    }

    public static class LoginRecord {
        public int userId;
        public long loginTime;
        public long logoutTime;
    }
}
