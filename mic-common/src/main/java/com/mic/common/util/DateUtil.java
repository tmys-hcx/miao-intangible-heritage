package com.mic.common.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 日期时间工具
 */
public class DateUtil {

    public static final String DATETIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    public static final String DATE_PATTERN = "yyyy-MM-dd";

    private DateUtil() {
    }

    /** 当前时间字符串：yyyy-MM-dd HH:mm:ss */
    public static String now() {
        return format(LocalDateTime.now());
    }

    /** 格式化：yyyy-MM-dd HH:mm:ss */
    public static String format(LocalDateTime time) {
        return format(time, DATETIME_PATTERN);
    }

    /** 按指定格式格式化 */
    public static String format(LocalDateTime time, String pattern) {
        if (time == null) {
            return null;
        }
        return DateTimeFormatter.ofPattern(pattern).format(time);
    }

    /** 解析：yyyy-MM-dd HH:mm:ss */
    public static LocalDateTime parse(String text) {
        return parse(text, DATETIME_PATTERN);
    }

    /** 按指定格式解析 */
    public static LocalDateTime parse(String text, String pattern) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(text, DateTimeFormatter.ofPattern(pattern));
    }

    /** 解析纯日期：yyyy-MM-dd */
    public static LocalDate parseDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return LocalDate.parse(text, DateTimeFormatter.ofPattern(DATE_PATTERN));
    }
}
