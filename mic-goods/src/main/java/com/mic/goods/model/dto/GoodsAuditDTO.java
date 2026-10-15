package com.mic.goods.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 非遗商品审核入参DTO（管理员审核商品）
 * 审核结论：1审核通过（上架，前台可见） / 2审核拒绝（打回，前台不可见，需填写不合格原因）
 * 审核结论与备注（remark）审核后存入 Redis，供商品审核界面与非遗人查看
 */
@Data
public class GoodsAuditDTO implements Serializable {

    @NotNull(message = "审核状态不能为空")
    @Min(value = 1, message = "审核状态取值：1审核通过、2审核拒绝")
    @Max(value = 2, message = "审核状态取值：1审核通过、2审核拒绝")
    private Integer auditStatus;

    /** 审核备注：审核拒绝（打回）时必填（服务层校验），作为不合格原因供非遗人修改后重新提交 */
    @Size(max = 200, message = "审核备注不能超过200字符")
    private String remark;
}
