package com.mic.common.exception;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * 异常工具：封装异常信息获取
 */
public class ExceptionUtil {

    private ExceptionUtil() {
    }

    /**
     * 获取完整异常堆栈信息字符串
     */
    public static String getStackTrace(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        StringWriter stringWriter = new StringWriter();
        try (PrintWriter printWriter = new PrintWriter(stringWriter)) {
            throwable.printStackTrace(printWriter);
        }
        return stringWriter.toString();
    }
}
