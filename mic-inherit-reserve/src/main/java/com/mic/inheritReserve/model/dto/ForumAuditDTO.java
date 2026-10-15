package com.mic.inheritReserve.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 论坛帖子审核入参DTO（管理员审核帖子）
 * 审核结论：1审核通过（前台即刻展示） / 2审核拒绝（驳回，需填写驳回原因）
 */
@Data
public class ForumAuditDTO implements Serializable {

    @NotNull(message = "审核状态不能为空")
    @Min(value = 1, message = "审核状态取值：1审核通过、2审核拒绝")
    @Max(value = 2, message = "审核状态取值：1审核通过、2审核拒绝")
    private Integer auditStatus;

    /** 审核备注：审核拒绝（驳回）时必填（服务层校验），作为驳回原因供作者查看 */
    @Size(max = 200, message = "审核备注不能超过200字符")
    private String remark;
}
