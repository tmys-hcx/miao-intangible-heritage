package com.mic.user.model.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 修改个人信息入参DTO（部分更新：null 字段不修改）
 * 可修改：昵称、手机号、身份信息、头像
 */
@Data
public class UserInfoUpdateDTO implements Serializable {

    @Size(min = 1, max = 50, message = "昵称长度必须1-50个字符")
    private String nickname;

    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @Size(max = 500, message = "身份信息不能超过500个字符")
    private String identityInfo;

    @Size(max = 512, message = "头像地址不能超过512个字符")
    private String avatar;
}
