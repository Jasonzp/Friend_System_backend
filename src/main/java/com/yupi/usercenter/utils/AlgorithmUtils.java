package com.yupi.usercenter.utils;

import java.util.List;

public class AlgorithmUtils {


    /**
     * 判断从list1到list2 需要更改多少次
     *
     * @param list1
     * @param list2 dp[i][j] 表示从list1[i] ，到list2[j] ，需要经过几次增、删、改
     * @return
     */
    public static int minDistance(List<String> list1, List<String> list2) {
        int m = list1.size();
        int n = list2.size();
        int[][] dp = new int[m + 1][n + 1];
        for (int i = 1; i < m + 1; i++) {
            dp[i][0] = i;
        }

        for (int j = 1; j < n + 1; j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i < m + 1; i++) {
            for (int j = 1; j < n + 1; j++) {
                if (list1.get(i - 1).equals(list2.get(j - 1))) {
                    // 表示当前不用做增删改查
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = Math.min(dp[i - 1][j - 1], Math.min(dp[i - 1][j], dp[i][j - 1])) + 1;
                }
            }
        }

        return dp[m][n];
    }
}
