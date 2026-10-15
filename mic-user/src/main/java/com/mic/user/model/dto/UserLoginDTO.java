package com.mic.user.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户登录入参DTO
 */
@Data
public class UserLoginDTO implements Serializable {

    @NotBlank(message = "账号不能为空")
    @Size(min = 4, max = 12, message = "账号长度必须4-12个字符")
    private String account;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度必须6-20位")
    private String password;

    @NotBlank(message = "验证码标识不能为空")
    private String captchaKey;

    @NotBlank(message = "验证码不能为空")
    private String captchaCode;
}
