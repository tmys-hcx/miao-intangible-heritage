package com.mic.common.util;

import com.mic.common.model.vo.StatsTrendVO;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端首页数据统计工具：近7天趋势的「分组查询结果 → 逐日补零」组装
 * 使用约定：业务服务用 MyBatis-Plus listMaps 按 DATE_FORMAT(create_time, '%m-%d') 分组查询后，
 * 先经 toCountByDate 转为映射，再由 fillLast7Days 生成含今天在内、日期升序、缺数补0的趋势列表
 */
public final class StatsUtil {

    /** 趋势日期标签格式：与前端折线图横轴一致（MM-dd） */
    private static final DateTimeFormatter TREND_FORMATTER = DateTimeFormatter.ofPattern("MM-dd");

    private StatsUtil() {
    }

    /**
     * 分组查询结果转「日期标签 → 数量」映射
     * @param rows listMaps 返回的分组结果（每行含日期标签与数量两列）
     * @param dateKey 日期标签列名（SQL 别名，格式须为 MM-dd）
     * @param countKey 数量列名（SQL 别名）
     */
    public static Map<String, Long> toCountByDate(List<Map<String, Object>> rows, String dateKey, String countKey) {
        Map<String, Long> countByDate = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object date = row.get(dateKey);
            Object count = row.get(countKey);
            if (date != null) {
                countByDate.put(date.toString(), count == null ? 0L : ((Number) count).longValue());
            }
        }
        return countByDate;
    }

    /**
     * 组装近7天趋势点（含今天，日期升序，无数据的日期补0），供前端折线图直接渲染
     * @param countByDate toCountByDate 生成的「日期标签 → 数量」映射
     */
    public static List<StatsTrendVO> fillLast7Days(Map<String, Long> countByDate) {
        List<StatsTrendVO> trend = new ArrayList<>(7);
        LocalDate today = LocalDate.now();
        for (int i = 6; i >= 0; i--) {
            String label = today.minusDays(i).format(TREND_FORMATTER);
            trend.add(new StatsTrendVO(label, countByDate.getOrDefault(label, 0L)));
        }
        return trend;
    }
}
