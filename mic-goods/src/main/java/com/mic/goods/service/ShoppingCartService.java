package com.mic.goods.service;

import com.mic.goods.model.dto.CartAddDTO;
import com.mic.goods.model.vo.CartItemVO;

import java.util.List;

/**
 * 购物车服务接口（Redis 存储）
 * 结构：Hash —— key = miao:cart:{userId}，field = 商品ID，value = JSON{goodsNum, addTime}
 */
public interface ShoppingCartService {

    /**
     * 加入购物车（登录用户）：同一商品重复加入时数量累加
     * @param dto 商品ID与数量
     * @param userId 当前登录用户ID（网关透传）
     */
    void addCart(CartAddDTO dto, Long userId);

    /**
     * 购物车列表（登录用户）：查询本人购物车，携带商品展示信息，最新加入在前
     * @param userId 当前登录用户ID
     * @return 购物车列表项（商品已删除时商品字段为null）
     */
    List<CartItemVO> listCart(Long userId);

    /**
     * 修改购物车数量（登录用户）：按商品ID定位本人购物车条目
     * @param goodsId 商品ID（Redis 购物车以商品ID作为条目标识）
     * @param goodsNum 修改后的数量
     * @param userId 当前登录用户ID
     */
    void updateCartNum(Long goodsId, Integer goodsNum, Long userId);

    /**
     * 删除购物车条目（登录用户）：按商品ID移除本人购物车条目
     * @param goodsId 商品ID（Redis 购物车以商品ID作为条目标识）
     * @param userId 当前登录用户ID
     */
    void deleteCart(Long goodsId, Long userId);

    /**
     * 批量删除购物车条目（下单成功后清空本次已结算商品）
     * @param goodsIds 商品ID列表（Redis 购物车以商品ID作为条目标识）
     * @param userId 当前登录用户ID
     */
    void batchDelete(List<Long> goodsIds, Long userId);
}
