package com.mic.user.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 修改密码入参DTO（个人中心-修改信息弹窗）
 * 说明：需校验原密码，新密码长度6-20位（与注册一致）且不能与原密码相同
 */
@Data
public class UserPasswordUpdateDTO implements Serializable {

    @NotBlank(message = "原密码不能为空")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 20, message = "新密码长度必须6-20位")
    private String newPassword;
}
