package com.mic.culture.model.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 非遗文化排版入参DTO（管理员拖拽调整前台默认展示顺序）
 * ids 为拖拽后的文章ID有序列表：数组顺序即前台展示顺序（第一条最靠前）
 */
@Data
public class CultureSortDTO implements Serializable {

    @NotEmpty(message = "排序文章列表不能为空")
    private List<Long> ids;
}
