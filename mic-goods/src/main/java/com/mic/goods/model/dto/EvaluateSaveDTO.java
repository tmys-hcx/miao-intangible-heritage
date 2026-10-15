package com.mic.goods.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 提交商品评价入参DTO
 * 说明：用户ID由服务端从网关透传头获取；仅订单已收货（status=3）且订单商品与 goodsId 一致时可评价，评价后订单自动流转 6已评价→7已完成
 */
@Data
public class EvaluateSaveDTO implements Serializable {

    @NotNull(message = "订单ID不能为空")
    private Long orderId;

    @NotNull(message = "商品ID不能为空")
    private Long goodsId;

    @NotBlank(message = "评价内容不能为空")
    @Size(max = 500, message = "评价内容最多500字符")
    private String evaluateContent;

    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分取值1~5星")
    @Max(value = 5, message = "评分取值1~5星")
    private Integer score;
}
