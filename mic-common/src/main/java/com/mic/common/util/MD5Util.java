package com.mic.common.util;

import cn.hutool.crypto.digest.DigestUtil;

/**
 * 密码MD5加密工具（密码禁止明文存储）
 */
public class MD5Util {

    private MD5Util() {
    }

    /**
     * MD5加密（32位小写）
     */
    public static String encrypt(String input) {
        return DigestUtil.md5Hex(input);
    }

    /**
     * 校验明文与密文是否匹配
     */
    public static boolean matches(String rawInput, String encrypted) {
        return encrypt(rawInput).equals(encrypted);
    }
}
