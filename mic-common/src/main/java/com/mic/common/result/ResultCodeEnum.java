package com.mic.common.result;

import lombok.Getter;

/**
 * 业务响应码枚举（与接口设计文档 2.1 业务码说明保持一致）
 */
@Getter
public enum ResultCodeEnum {

    /** 操作成功 */
    SUCCESS(200, "操作成功"),
    /** 参数校验失败 */
    PARAM_ERROR(400, "参数校验失败"),
    /** 未登录 / token失效 */
    NOT_LOGIN(401, "未登录或登录已过期"),
    /** 没有操作权限（非管理员访问管理接口） */
    FORBIDDEN(403, "没有操作权限"),
    /** 请求的资源不存在（路径不存在或静态资源缺失） */
    NOT_FOUND(404, "请求的资源不存在"),
    /** 账号审核未通过（非遗人账号待审核，无法发布内容） */
    AUDIT_NOT_PASS(405, "账号审核未通过"),
    /** 库存不足，无法下单 */
    STOCK_NOT_ENOUGH(406, "库存不足，无法下单"),
    /** 重复提交（重复点赞、重复预约学习） */
    REPEAT_SUBMIT(408, "请勿重复提交"),
    /** 文件上传失败（图片/视频过大、格式非法） */
    FILE_UPLOAD_ERROR(409, "文件上传失败"),
    /** 账号已被禁用 */
    ACCOUNT_DISABLED(410, "账号已被禁用"),
    /** 旧密码错误，修改密码失败 */
    OLD_PASSWORD_ERROR(411, "旧密码错误，修改密码失败"),
    /** 服务内部异常 */
    ERROR(500, "服务内部异常"),
    /** 文件存储服务异常 */
    FILE_SERVICE_ERROR(502, "文件存储服务异常");

    private final Integer code;
    private final String msg;

    ResultCodeEnum(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
