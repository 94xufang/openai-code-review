package com.xufun.sdk.utils;

import java.util.Random;

/** 生成日志文件名用的随机后缀（字母数字混合）。 */
public class RandomStingUtils {
    public static String randomNumeric(int length) {
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        Random random = new Random();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(characters.charAt(random.nextInt(characters.length())));
        }
        return sb.toString();
    }
}
