package com.mic.user.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_log")
public class SysLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    @NotNull(message = "操作人ID不能为空")
    private Long operateUserId;

    @NotBlank(message = "操作模块不能为空")
    @Size(max = 50, message = "操作模块长度不能超过50字符")
    private String operateModule;

    @NotBlank(message = "操作内容不能为空")
    @Size(max = 255, message = "操作内容不能超过255字符")
    private String operateContent;

    @NotBlank(message = "请求IP不能为空")
    @Size(max = 50, message = "IP地址长度不能超过50字符")
    private String ip;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
