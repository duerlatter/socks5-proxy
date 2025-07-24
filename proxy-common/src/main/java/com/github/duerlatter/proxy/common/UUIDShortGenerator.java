package com.github.duerlatter.proxy.common;

import java.util.UUID;

/**
 * @author fsren
 * @date 2025-07-09
 */
public class UUIDShortGenerator {

    private static final char[] CHAR_MAP = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();

    public static String generateShortUuid() {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        // UUID 32个16进制字符，分成6段，每段取部分转成对应字符
        StringBuilder shortUuid = new StringBuilder();

        // 每次取4个16进制字符，转成一个int，然后映射成62进制字符
        for (int i = 0; i < 6; i++) {
            String segment = uuid.substring(i * 4, i * 4 + 4);
            int x = Integer.parseInt(segment, 16);
            shortUuid.append(CHAR_MAP[x % 62]);
        }
        return shortUuid.toString();
    }

    public static void main(String[] args) {
        for (int i = 0; i < 10; i++) {
            System.out.println(generateShortUuid());
        }
    }
}