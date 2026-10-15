package com.mic.common.exception;

import com.mic.common.result.ResultCodeEnum;
import lombok.Getter;

/**
 * 自定义业务异常：由全局异常处理器统一转换为Result返回
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 业务状态码 */
    private final Integer code;

    public BusinessException(ResultCodeEnum resultCode) {
        super(resultCode.getMsg());
        this.code = resultCode.getCode();
    }

    public BusinessException(ResultCodeEnum resultCode, String msg) {
        super(msg);
        this.code = resultCode.getCode();
    }

    public BusinessException(Integer code, String msg) {
        super(msg);
        this.code = code;
    }
}
