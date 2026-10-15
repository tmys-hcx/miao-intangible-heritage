package com.mic.common.model.dto;

import lombok.Data;

/**
 * 通用分页入参DTO（query参数）
 * 分页参数：current、size；同时兼容前端 pageNum、pageSize 传参
 */
@Data
public class PageDto {

    /** 当前页码，默认第1页 */
    private Integer current = 1;

    /** 每页条数，默认10条 */
    private Integer size = 10;

    /** 兼容前端 pageNum 传参 */
    public void setPageNum(Integer pageNum) {
        if (pageNum != null && pageNum > 0) {
            this.current = pageNum;
        }
    }

    /** 兼容前端 pageSize 传参 */
    public void setPageSize(Integer pageSize) {
        if (pageSize != null && pageSize > 0) {
            this.size = pageSize;
        }
    }
}
