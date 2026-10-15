package com.mic.common.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 生成唯一ID/订单编号工具
 */
public class UUIDUtil {

    private static final DateTimeFormatter ORDER_NO_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private UUIDUtil() {
    }

    /** 32位无横线UUID（验证码标识、文件名等） */
    public static String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** 标准UUID（含横线） */
    public static String uuidWithDash() {
        return UUID.randomUUID().toString();
    }

    /** 订单编号：时间戳（毫秒级）+ 6位随机数 */
    public static String orderNo() {
        String prefix = ORDER_NO_FORMATTER.format(LocalDateTime.now());
        String suffix = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        return prefix + suffix;
    }
}
