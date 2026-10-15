package com.mic.common.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 统一返回结果封装类
 * 结构：{ code、msg、data }，业务码定义见 ResultCodeEnum
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> implements Serializable {

    /** 业务状态码：200成功，其余见 ResultCodeEnum */
    private Integer code;

    /** 提示信息 */
    private String msg;

    /** 响应数据 */
    private T data;

    /** 操作成功（无数据） */
    public static <T> Result<T> ok() {
        return new Result<>(ResultCodeEnum.SUCCESS.getCode(), ResultCodeEnum.SUCCESS.getMsg(), null);
    }

    /** 操作成功（带数据） */
    public static <T> Result<T> ok(T data) {
        return new Result<>(ResultCodeEnum.SUCCESS.getCode(), ResultCodeEnum.SUCCESS.getMsg(), data);
    }

    /** 操作成功（自定义提示信息） */
    public static <T> Result<T> ok(T data, String msg) {
        return new Result<>(ResultCodeEnum.SUCCESS.getCode(), msg, data);
    }

    /** 业务失败（响应码枚举） */
    public static <T> Result<T> fail(ResultCodeEnum resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMsg(), null);
    }

    /** 业务失败（响应码枚举 + 自定义提示信息） */
    public static <T> Result<T> fail(ResultCodeEnum resultCode, String msg) {
        return new Result<>(resultCode.getCode(), msg, null);
    }

    /** 业务失败（自定义状态码 + 提示信息） */
    public static <T> Result<T> fail(Integer code, String msg) {
        return new Result<>(code, msg, null);
    }
}
