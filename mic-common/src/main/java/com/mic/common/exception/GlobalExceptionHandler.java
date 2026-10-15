package com.mic.common.exception;

import com.mic.common.result.Result;
import com.mic.common.result.ResultCodeEnum;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 全局异常处理器：统一将异常转换为 Result 返回，保证前端解析结构一致
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常 */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常 -> code: {}, msg: {}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /** 请求体参数校验失败（@RequestBody + @Valid） */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .findFirst()
                .orElse(ResultCodeEnum.PARAM_ERROR.getMsg());
        log.warn("参数校验失败 -> {}", msg);
        return Result.fail(ResultCodeEnum.PARAM_ERROR, msg);
    }

    /** 查询参数对象绑定校验失败（@ModelAttribute + @Valid） */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .findFirst()
                .orElse(ResultCodeEnum.PARAM_ERROR.getMsg());
        log.warn("参数绑定失败 -> {}", msg);
        return Result.fail(ResultCodeEnum.PARAM_ERROR, msg);
    }

    /** 单参数校验失败（@Validated + @RequestParam / @PathVariable） */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolationException(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .findFirst()
                .orElse(ResultCodeEnum.PARAM_ERROR.getMsg());
        log.warn("参数校验失败 -> {}", msg);
        return Result.fail(ResultCodeEnum.PARAM_ERROR, msg);
    }

    /** 路径变量 / 请求参数类型转换失败（如把 /api/goods/publish 中的 publish 当作商品ID转换）：按参数错误提示，避免兜底误报为系统异常 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        String msg = "参数 " + e.getName() + " 类型不正确";
        log.warn("参数类型不匹配 -> {} = {}", e.getName(), e.getValue());
        return Result.fail(ResultCodeEnum.PARAM_ERROR, msg);
    }

    /** 请求方式不支持（如 GET 请求打到仅支持 POST/PUT/DELETE 的接口）：明确提示调用方，避免兜底误报为服务内部异常 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleHttpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        Set<HttpMethod> supported = e.getSupportedHttpMethods();
        String supportedText = supported == null ? ""
                : supported.stream().map(HttpMethod::name).collect(Collectors.joining("/"));
        String hint = supportedText.isEmpty() ? "" : "，该接口仅支持 " + supportedText + " 请求";
        log.warn("请求方式不支持 -> {} {}{}", request.getMethod(), request.getRequestURI(), hint);
        return Result.fail(ResultCodeEnum.PARAM_ERROR, "请求方式不支持：" + e.getMethod() + hint);
    }

    /** 请求路径不存在（如浏览器直接访问根路径、favicon.ico 等静态资源）：按 404 语义返回，避免兜底误报为系统异常 */
    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNoResourceFoundException(NoResourceFoundException e, HttpServletRequest request) {
        log.warn("请求路径不存在 -> {} {}", request.getMethod(), request.getRequestURI());
        return Result.fail(ResultCodeEnum.NOT_FOUND);
    }

    /** 上传文件超出大小限制（spring.servlet.multipart.max-file-size）：按文件上传失败提示，避免兜底误报为系统异常 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        log.warn("文件上传失败 -> 超出大小限制: {}", e.getMessage());
        return Result.fail(ResultCodeEnum.FILE_UPLOAD_ERROR, "上传文件过大，请压缩后重试");
    }

    /** 兜底异常 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.fail(ResultCodeEnum.ERROR);
    }
}
