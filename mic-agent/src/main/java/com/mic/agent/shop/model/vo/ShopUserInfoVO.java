package com.mic.agent.shop.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 当前登录用户信息VO（Agent 侧契约类）：
 * 与 mic-user 用户信息接口（GET /api/user/info）返回的 LoginUserVO 字段对齐，仅保留问答需要的字段
 */
@Data
public class ShopUserInfoVO implements Serializable {

    /** 用户ID */
    private Long id;

    /** 账号 */
    private String account;

    /** 昵称 */
    private String nickname;

    /** 手机号 */
    private String phone;

    /** 角色：0管理员 1非遗人 2普通用户 */
    private Integer role;
}
