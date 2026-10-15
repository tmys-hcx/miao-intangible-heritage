package com.mic.user.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 非遗人申请审核入参DTO（管理员）
 * 审核结论：1审核通过（申请人转为非遗人） / 2审核驳回（需填写驳回原因）
 */
@Data
public class HeritageAuditDTO implements Serializable {

    @NotNull(message = "审核结论不能为空")
    @Min(value = 1, message = "审核结论取值：1审核通过、2审核驳回")
    @Max(value = 2, message = "审核结论取值：1审核通过、2审核驳回")
    private Integer auditStatus;

    /** 审核备注：审核驳回时必填（服务层校验），作为驳回原因 */
    @Size(max = 200, message = "审核备注不能超过200个字符")
    private String remark;
}
