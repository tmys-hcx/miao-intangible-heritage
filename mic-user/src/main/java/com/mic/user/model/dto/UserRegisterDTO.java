package com.mic.user.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户注册入参DTO（仅普通用户）
 * 说明：注册不开放非遗人身份，普通用户后续在个人中心申请成为非遗人
 */
@Data
public class UserRegisterDTO implements Serializable {

    @NotBlank(message = "账号不能为空")
    @Size(min = 4, max = 12, message = "账号长度必须4-12个字符")
    private String account;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度必须6-20位")
    private String password;

    /** 昵称（可选，未填时默认取账号） */
    @Size(max = 50, message = "昵称不能超过50个字符")
    private String nickname;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "验证码标识不能为空")
    private String captchaKey;

    @NotBlank(message = "验证码不能为空")
    private String captchaCode;
}
