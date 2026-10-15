package com.mic.user.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 图形验证码返回VO
 */
@Data
public class CaptchaVO implements Serializable {

    /** 验证码标识（提交登录/注册时原样带回） */
    private String captchaKey;

    /** 验证码图片（data:image/png;base64 格式） */
    private String imageBase64;
}
