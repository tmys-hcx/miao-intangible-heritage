package com.mic.common.model.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * Feign调用通用用户信息DTO（供其他微服务获取用户信息使用，不含密码）
 */
@Data
public class UserInfoDTO implements Serializable {

    /** 用户ID */
    private Long userId;

    /** 账号 */
    private String account;

    /** 昵称 */
    private String nickname;

    /** 头像 */
    private String avatar;

    /** 手机号 */
    private String phone;

    /** 角色：0管理员 1非遗人 2普通用户 */
    private Integer role;

    /** 状态：0待审核 1正常 2禁用 */
    private Integer status;
}
