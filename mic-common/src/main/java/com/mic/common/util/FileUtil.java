package com.mic.common.util;

import java.util.Locale;
import java.util.Set;

/**
 * 文件工具：文件名处理、后缀校验（图片/视频白名单）
 */
public class FileUtil {

    /** 允许上传的图片扩展名 */
    private static final Set<String> IMAGE_EXTENSIONS =
            Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp");

    /** 允许上传的视频扩展名（仅保留最常见格式） */
    private static final Set<String> VIDEO_EXTENSIONS = Set.of("mp4");

    private FileUtil() {
    }

    /**
     * 获取文件扩展名（小写，不含点；无扩展名返回空串）
     */
    public static String getExtension(String filename) {
        if (filename == null || filename.isBlank()) {
            return "";
        }
        int index = filename.lastIndexOf('.');
        if (index < 0 || index == filename.length() - 1) {
            return "";
        }
        return filename.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    /** 是否为允许上传的图片 */
    public static boolean isImage(String filename) {
        return IMAGE_EXTENSIONS.contains(getExtension(filename));
    }

    /** 是否为允许上传的视频 */
    public static boolean isVideo(String filename) {
        return VIDEO_EXTENSIONS.contains(getExtension(filename));
    }

    /** 是否为允许上传的文件（图片或视频） */
    public static boolean isAllowed(String filename) {
        return isImage(filename) || isVideo(filename);
    }

    /**
     * 生成唯一存储文件名：UUID + 原扩展名
     */
    public static String generateFileName(String originalFilename) {
        String extension = getExtension(originalFilename);
        return extension.isEmpty() ? UUIDUtil.uuid() : UUIDUtil.uuid() + "." + extension;
    }
}
