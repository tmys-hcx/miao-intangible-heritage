package com.mic.user.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 非遗人申请入参DTO（普通用户提交）
 * 说明：身份信息、申请原因、证明材料为申请内容三要素
 */
@Data
public class HeritageApplyDTO implements Serializable {

    @NotBlank(message = "身份信息不能为空")
    @Size(max = 500, message = "身份信息不能超过500个字符")
    private String identityInfo;

    @NotBlank(message = "申请原因不能为空")
    @Size(max = 500, message = "申请原因不能超过500个字符")
    private String applyReason;

    /** 证明材料图片地址（OSS地址，1-6张，JSON数组存储） */
    @NotEmpty(message = "请上传证明材料")
    @Size(max = 6, message = "证明材料最多上传6张")
    private List<String> materialUrls;
}
