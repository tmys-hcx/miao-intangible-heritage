package com.mic.user.model.vo;

import com.mic.common.model.vo.LoginUserVO;
import lombok.Data;

import java.io.Serializable;

/**
 * 登录返回VO：token + 用户信息 + 按角色区分的跳转地址
 */
@Data
public class UserLoginVO implements Serializable {

    /** JWT令牌，请求头携带格式：Authorization: Bearer token */
    private String token;

    /** 用户基础信息 */
    private LoginUserVO userInfo;

    /** 登录后跳转地址：管理员→/admin（后台管理页），非遗人/普通用户→/（前台首页） */
    private String redirectUrl;
}
