package com.trip.noting.algorithm;

import com.alibaba.fastjson2.JSON;

import java.util.*;

public class StackTest {

    public static void main(String[] args) {
        int[] ints = {3, 1, 2, 5, 4};
        System.out.println(JSON.toJSONString(ints));
        int[] ints1 = safeViewDistance(ints);
        System.out.println(JSON.toJSONString(ints1));
    }

    // 第二题
    public static int[] safeViewDistance(int[] h) {
        int n = h.length;
        int[] vis = new int[n];
        int[] stk = new int[n];
        int top = -1;
        System.out.println("=============");
        for (int i = n - 1; i >= 0; i--) {
            while (top >= 0 && h[stk[top]] <= h[i]) top--;
            // 有 blocker → 看到 blocker 为止；无 blocker → 看到末尾 n-1
            int rightmost = (top >= 0) ? stk[top] : n - 1;
            vis[i] = rightmost - i;
            stk[++top] = i;
            System.out.println(JSON.toJSONString(stk));
        }
        System.out.println("=============");
        return vis;
    }


    /**
     * 数组中"不同元素数 ≤ k"的连续子数组个数
     *
     * @param arr 商品类别序列
     * @param k   允许的最大不同类别数
     * @return 合法浏览片段（子数组）数量
     */
    public long countSubarrays(int[] arr, int k) {
        int n = arr.length;
        Map<Integer, Integer> categoryCntMap = new HashMap<>();   // 窗口内每个类别的出现次数
        long ans = 0;
        int left = 0;
        for (int right = 0; right < n; right++) {
            // 加入新元素
            categoryCntMap.merge(arr[right], 1, Integer::sum);
            // 不同类别超标，收缩左边界
            while (categoryCntMap.size() > k) {
                int leftVal = arr[left];
                int c = categoryCntMap.get(leftVal);
                if (c == 1) {
                    // 归零的类别要删掉，否则 size() 错
                    categoryCntMap.remove(leftVal);
                } else {
                    categoryCntMap.put(leftVal, c - 1);
                }
                left++;
            }
            // 以 right 为右端点的合法子数组个数
            ans += right - left + 1;
        }
        return ans;
    }

    /**
     * @param delay    每个节点默认时延
     * @param winSize  每个节点配置后影响的后续节点数
     * @param optDelay 每个节点配置后的时延优化值
     * @param K        最多可配置的节点数
     * @return 最小总处理时延
     */
    public long minTotalDelay(int[] delay, int[] winSize, int[] optDelay, int K) {
        int N = delay.length;
        final long INF = Long.MAX_VALUE / 4;

        // dp[s][k]: bit0=p, bit1=p-1, bit2=p-2 是否配置
        long[][] dp = new long[8][K + 1];
        for (long[] row : dp) Arrays.fill(row, INF);

        // 初始化位置 0
        for (int c = 0; c <= 1 && c <= K; c++) {
            long sub = (c == 1) ? optDelay[0] : 0;
            long contrib = Math.max(0L, (long) delay[0] - sub);
            dp[c][c] = Math.min(dp[c][c], contrib);
        }

        // 推进位置 1..N-1
        for (int p = 1; p < N; p++) {
            long[][] nd = new long[8][K + 1];
            for (long[] row : nd) Arrays.fill(row, INF);

            for (int s = 0; s < 8; s++) {
                for (int k = 0; k <= K; k++) {
                    if (dp[s][k] >= INF) continue;
                    for (int c = 0; c <= 1; c++) {
                        int k2 = k + c;
                        if (k2 > K) continue;
                        long sub = 0;
                        int pm1 = p - 1, pm2 = p - 2, pm3 = p - 3;
                        if (((s >> 0) & 1) == 1 && winSize[pm1] >= 1) sub += optDelay[pm1];
                        if (pm2 >= 0 && ((s >> 1) & 1) == 1 && winSize[pm2] >= 2) sub += optDelay[pm2];
                        if (pm3 >= 0 && ((s >> 2) & 1) == 1 && winSize[pm3] >= 3) sub += optDelay[pm3];
                        if (c == 1) sub += optDelay[p];
                        long contrib = Math.max(0L, (long) delay[p] - sub);
                        int s2 = ((s & 0b011) << 1) | c;
                        nd[s2][k2] = Math.min(nd[s2][k2], dp[s][k] + contrib);
                    }
                }
            }
            dp = nd;
        }

        long ans = INF;
        for (int s = 0; s < 8; s++)
            for (int k = 0; k <= K; k++)
                ans = Math.min(ans, dp[s][k]);
        return ans;
    }

    // public static int rlfCompute(String source, String destination) {
    //     if (source == null || source.isEmpty()) {
    //         return 0;
    //     }
    //     if (destination == null || destination.isEmpty()) {
    //         return 0;
    //     }
    //     refMap(source);
    // }
    //
    // private static List<HashMap<String,Integer>> refMap(String source) {
    //     String[] split = source.split("");
    //     for (int i = 0; i < split.length; i++) {
    //         String s = split[i];
    //         System.out.println(s);
    //     }
    // }

    // 第一题
    public static long rlfCompute(String source, String destination) {
        // RLE
        List<char[]> rleA = rle(source); // 每项 {char, len}
        List<char[]> rleS = rle(destination);
        int k = rleA.size(), p = rleS.size();
        long ans = 0;
        if (k == 1) {
            char c1 = rleA.get(0)[0];
            int l1 = rleA.get(0)[1];
            for (char[] seg : rleS) {
                if (seg[0] == c1 && seg[1] >= l1) {
                    long d = seg[1] - l1 + 1;
                    ans += d * (d + 1) / 2;
                }
            }
        } else {
            for (int i = 0; i + k <= p; i++) {
                boolean ok = true;
                for (int j = 0; j < k && ok; j++) {
                    char[] sg = rleS.get(i + j);
                    char[] ag = rleA.get(j);
                    if (sg[0] != ag[0]) {
                        ok = false;
                        break;
                    }
                    // 中间段必须整段 ≥ A 段长
                    if (j > 0 && j < k - 1 && sg[1] < ag[1]) ok = false;
                }
                if (!ok) continue;
                int r1 = rleS.get(i)[1], la1 = rleA.get(0)[1];
                int rk = rleS.get(i + k - 1)[1], lak = rleA.get(k - 1)[1];
                if (r1 < la1 || rk < lak) continue;
                ans += (long) (r1 - la1 + 1) * (rk - lak + 1);
            }
        }
        System.out.println(ans);
        return ans;
    }

    static List<char[]> rle(String s) {
        List<char[]> res = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            int j = i;
            while (j < s.length() && s.charAt(j) == s.charAt(i)) j++;
            res.add(new char[]{s.charAt(i), (char) (j - i)});
            i = j;
        }
        return res;
    }


}
