package com.mic.goods.service.impl;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.mic.common.exception.BusinessException;
import com.mic.common.result.ResultCodeEnum;
import com.mic.common.util.DateUtil;
import com.mic.goods.model.dto.CartAddDTO;
import com.mic.goods.model.entity.IntangibleGoods;
import com.mic.goods.model.vo.CartItemVO;
import com.mic.goods.service.IntangibleGoodsService;
import com.mic.goods.service.ShoppingCartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 购物车服务实现（Redis 存储）
 * 结构：Hash —— key = miao:cart:{userId}，field = 商品ID，value = JSON{goodsNum, addTime}
 * 说明：购物车数据不落库，全量存储于 Redis；商品展示信息在查询时实时组装
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShoppingCartServiceImpl implements ShoppingCartService {

    /** 商品状态：1审核通过（上架，可加入购物车） */
    private static final int GOODS_STATUS_APPROVED = 1;

    /** Redis 购物车 key 前缀：miao:cart:{userId}（Hash 结构） */
    private static final String REDIS_CART_KEY_PREFIX = "miao:cart:";

    /** 购物车条目 JSON 字段：商品数量 */
    private static final String ITEM_FIELD_GOODS_NUM = "goodsNum";

    /** 购物车条目 JSON 字段：加入时间（yyyy-MM-dd HH:mm:ss，列表排序依据） */
    private static final String ITEM_FIELD_ADD_TIME = "addTime";

    /** 非遗商品服务：校验购物车关联商品的有效性（存在且已上架） */
    private final IntangibleGoodsService intangibleGoodsService;

    /** StringRedisTemplate：购物车 Hash 结构直接使用原生模板（RedisUtil 仅封装字符串值操作） */
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 加入购物车（登录用户）：同一商品重复加入时数量累加
     */
    @Override
    public void addCart(CartAddDTO dto, Long userId) {
        checkGoodsNum(dto.getGoodsNum());
        IntangibleGoods goods = getOnSaleGoods(dto.getGoodsId());
        // 非遗人不能将自己发布的商品加入购物车（服务端权威校验，与下单口径一致）
        if (Objects.equals(goods.getPublisherId(), userId)) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "不能将自己发布的商品加入购物车");
        }

        String cartKey = cartKey(userId);
        String field = String.valueOf(dto.getGoodsId());
        // 同一用户同一商品在 Hash 中仅占一个 field，重复加入则数量累加，保留首次加入时间用于列表排序
        Object raw = stringRedisTemplate.opsForHash().get(cartKey, field);
        int existingNum = raw == null ? 0 : JSONUtil.parseObj(raw.toString()).getInt(ITEM_FIELD_GOODS_NUM, 0);
        int totalNum = existingNum + dto.getGoodsNum();
        // 按累加后的总数量校验（购物车内总量同样受库存与限购约束）
        checkStockAndLimit(goods, totalNum);

        JSONObject item = raw == null ? new JSONObject() : JSONUtil.parseObj(raw.toString());
        item.set(ITEM_FIELD_GOODS_NUM, totalNum);
        item.set(ITEM_FIELD_ADD_TIME, raw == null
                ? DateUtil.now()
                : item.getStr(ITEM_FIELD_ADD_TIME, DateUtil.now()));
        stringRedisTemplate.opsForHash().put(cartKey, field, item.toString());
        log.info("加入购物车成功（Redis）：userId={}, goodsId={}, goodsNum={}", userId, dto.getGoodsId(), totalNum);
    }

    /**
     * 购物车列表（登录用户）：携带商品展示信息，最新加入在前
     */
    @Override
    public List<CartItemVO> listCart(Long userId) {
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(cartKey(userId));
        if (entries.isEmpty()) {
            return List.of();
        }
        // 解析条目并按加入时间倒序（最新加入在前）
        List<CartEntry> items = entries.entrySet().stream()
                .map(entry -> new CartEntry(Long.valueOf(entry.getKey().toString()),
                        JSONUtil.parseObj(entry.getValue().toString())))
                .sorted((a, b) -> b.item().getStr(ITEM_FIELD_ADD_TIME, "").compareTo(a.item().getStr(ITEM_FIELD_ADD_TIME, "")))
                .toList();

        // 批量查询关联商品（一次查询避免逐条查库）；商品已被删除时查不到，对应商品字段为null
        List<Long> goodsIds = items.stream().map(CartEntry::goodsId).distinct().toList();
        Map<Long, IntangibleGoods> goodsMap = intangibleGoodsService.listByIds(goodsIds).stream()
                .collect(Collectors.toMap(IntangibleGoods::getId, goods -> goods));

        return items.stream().map(entry -> {
            CartItemVO vo = new CartItemVO();
            vo.setGoodsId(entry.goodsId());
            vo.setGoodsNum(entry.item().getInt(ITEM_FIELD_GOODS_NUM, 1));
            vo.setCreateTime(DateUtil.parse(entry.item().getStr(ITEM_FIELD_ADD_TIME)));
            IntangibleGoods goods = goodsMap.get(entry.goodsId());
            if (goods != null) {
                vo.setGoodsName(goods.getGoodsName());
                vo.setGoodsImg(goods.getGoodsImg());
                vo.setPrice(goods.getPrice());
                vo.setGoodsDesc(goods.getGoodsDesc());
                vo.setDeliveryTime(goods.getDeliveryTime());
                vo.setStock(goods.getStock());
                vo.setLimitBuyNum(goods.getLimitBuyNum());
                vo.setStatus(goods.getStatus());
            }
            return vo;
        }).toList();
    }

    /**
     * 修改购物车数量（登录用户）：按商品ID定位本人购物车条目
     */
    @Override
    public void updateCartNum(Long goodsId, Integer goodsNum, Long userId) {
        checkGoodsNum(goodsNum);
        String cartKey = cartKey(userId);
        String field = String.valueOf(goodsId);
        Object raw = stringRedisTemplate.opsForHash().get(cartKey, field);
        if (raw == null) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "购物车条目不存在");
        }
        IntangibleGoods goods = getOnSaleGoods(goodsId);
        checkStockAndLimit(goods, goodsNum);

        JSONObject item = JSONUtil.parseObj(raw.toString());
        item.set(ITEM_FIELD_GOODS_NUM, goodsNum);
        stringRedisTemplate.opsForHash().put(cartKey, field, item.toString());
        log.info("修改购物车数量成功（Redis）：userId={}, goodsId={}, goodsNum={}", userId, goodsId, goodsNum);
    }

    /**
     * 删除购物车条目（登录用户）：按商品ID移除本人购物车条目
     */
    @Override
    public void deleteCart(Long goodsId, Long userId) {
        Long removed = stringRedisTemplate.opsForHash().delete(cartKey(userId), String.valueOf(goodsId));
        if (removed == null || removed == 0) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "购物车条目不存在");
        }
        log.info("删除购物车条目成功（Redis）：userId={}, goodsId={}", userId, goodsId);
    }

    /**
     * 批量删除购物车条目（下单成功后清空本次已结算商品）：一次 HDEL 移除多个字段
     */
    @Override
    public void batchDelete(List<Long> goodsIds, Long userId) {
        if (goodsIds == null || goodsIds.isEmpty()) {
            return;
        }
        Object[] fields = goodsIds.stream().map(String::valueOf).toArray();
        stringRedisTemplate.opsForHash().delete(cartKey(userId), fields);
        log.info("批量删除购物车条目成功（Redis）：userId={}, goodsIds={}", userId, goodsIds);
    }

    /** 查询已上架商品：商品不存在 / 已被删除 / 未上架时抛出业务异常 */
    private IntangibleGoods getOnSaleGoods(Long goodsId) {
        IntangibleGoods goods = intangibleGoodsService.getById(goodsId);
        if (goods == null || goods.getStatus() != GOODS_STATUS_APPROVED) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "商品不存在或已下架");
        }
        return goods;
    }

    /** 校验商品数量：不超过当前库存与限购数量 */
    private void checkStockAndLimit(IntangibleGoods goods, int goodsNum) {
        if (goodsNum > goods.getStock()) {
            throw new BusinessException(ResultCodeEnum.STOCK_NOT_ENOUGH, "商品库存不足，当前仅剩" + goods.getStock() + "件");
        }
        if (goods.getLimitBuyNum() != null && goodsNum > goods.getLimitBuyNum()) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "已达到最大购买数量，不能再添加");
        }
    }

    /** 兜底校验商品数量（内部调用绕开 DTO 注解约束时仍有效） */
    private void checkGoodsNum(Integer goodsNum) {
        if (goodsNum == null || goodsNum <= 0) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "商品数量必须大于0");
        }
    }

    /** 本人购物车 Redis key：miao:cart:{userId} */
    private String cartKey(Long userId) {
        return REDIS_CART_KEY_PREFIX + userId;
    }

    /** 购物车条目中间结构：商品ID + Redis 中的条目 JSON */
    private record CartEntry(Long goodsId, JSONObject item) {
    }
}
