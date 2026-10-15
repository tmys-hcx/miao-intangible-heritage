package com.mic.goods.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 更新订单状态入参DTO
 * 目标状态：1付款（买家：0待付款→1待发货） / 2发货（卖家：1待发货→2已发货）
 * / 3收货（买家：2已发货→3已收货） / 5取消（买家：0待付款→5已取消）
 * 说明：8退款申请中走申请退款接口、4已退款走非遗人同意退款接口，6已评价 / 7已完成由评价接口自动流转，均不可经本接口提交（服务端白名单兜底校验）
 */
@Data
public class OrderStatusDTO implements Serializable {

    @NotNull(message = "订单状态不能为空")
    @Min(value = 1, message = "订单状态取值：1付款、2发货、3收货、5取消")
    @Max(value = 5, message = "订单状态取值：1付款、2发货、3收货、5取消")
    private Integer orderStatus;
}
