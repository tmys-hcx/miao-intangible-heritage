package com.mic.common.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 登录成功返回用户基础信息VO（不含密码等敏感字段）
 */
@Data
public class LoginUserVO implements Serializable {

    /** 用户ID */
    private Long id;

    /** 账号 */
    private String account;

    /** 昵称 */
    private String nickname;

    /** 头像 */
    private String avatar;

    /** 手机号 */
    private String phone;

    /** 身份信息（非遗人申请前置校验 / 个人中心完善信息提示使用） */
    private String identityInfo;

    /** 角色：0管理员 1非遗人 2普通用户 */
    private Integer role;

    /** 状态：0待审核 1正常 2禁用 */
    private Integer status;
}
