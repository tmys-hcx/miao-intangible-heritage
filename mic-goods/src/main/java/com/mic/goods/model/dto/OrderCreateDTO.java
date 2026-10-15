package com.mic.goods.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 创建订单入参DTO（购物车结算 / 立即购买直购：选中商品ID列表 + 收货地址 + 订单备注 + 初始订单状态）
 * 说明：用户ID由服务端从网关透传头获取；订单金额以服务端商品价格计算，不信任前端传值；
 * 一次结算勾选的每个商品创建一张独立订单（共享结算批次号，支付时整批一起支付）；
 * 立即购买（buyNowNum 非空）不经过购物车，按商品ID + 直购数量直接结算（仅支持单个商品，不清购物车）
 */
@Data
public class OrderCreateDTO implements Serializable {

    /** 选中结算的商品ID列表（购物车以商品ID作为条目标识，每个商品结算后生成一张独立订单；立即购买时仅传单个商品ID） */
    @NotEmpty(message = "请选择要结算的商品")
    private List<Long> goodsIds;

    /** 选中收货地址ID */
    @NotNull(message = "请选择收货地址")
    private Long addressId;

    /** 订单备注（选填） */
    @Size(max = 512, message = "订单备注长度不能超过512字符")
    private String orderRemark;

    /** 订单初始状态：0待付款（点击支付创建，创建时即逐单锁定库存并写入 Redis 15分钟支付超时标记，超时未支付自动取消）；1待发货（直接购买创建，创建时同样锁定库存），不传默认1 */
    private Integer orderStatus;

    /** 立即购买（直购）数量：非空=直购模式，不读购物车、按该数量直接结算 goodsIds 中的单个商品且下单后不清购物车；为 null=购物车结算模式（按购物车中的数量结算并清空对应条目） */
    @Min(value = 1, message = "购买数量不能小于1")
    private Integer buyNowNum;
}
