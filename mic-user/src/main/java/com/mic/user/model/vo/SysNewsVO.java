package com.mic.user.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 资讯公告返回VO
 */
@Data
public class SysNewsVO implements Serializable {

    private Long id;

    private String title;

    private String content;

    private LocalDateTime createTime;
}
