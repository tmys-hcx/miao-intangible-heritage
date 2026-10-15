package com.mic.goods.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mic.goods.model.entity.GoodsOrder;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 商品订单表Mapper
 */
public interface GoodsOrderMapper extends BaseMapper<GoodsOrder> {

    /**
     * 后台订单分页（管理端订单管理）：全部用户订单（含全部状态），可按订单状态筛选，
     * 支持订单号 / 下单人账号模糊搜索
     * LEFT JOIN sys_user（同库）关联下单人账号，填充瞬态字段 userAccount（账号缺失时为 null）
     * @param page MyBatis-Plus 分页对象（由分页插件自动分页与统计总数）
     * @param orderStatus 订单状态筛选（null=全部：0待付款 1待发货 2已发货 3已收货 4已退款 5已取消 6已评价 7已完成 8退款申请中）
     * @param keyword 搜索关键词（null/空=不筛选；匹配订单编号或下单人账号）
     */
    IPage<GoodsOrder> selectAdminOrderPage(IPage<GoodsOrder> page,
                                           @Param("orderStatus") Integer orderStatus,
                                           @Param("keyword") String keyword);

    /**
     * 查询用户昵称（sys_user 同库）：订单 / 退款通知文案使用（如「买家 小满 拍下了…」）
     * @param userId 用户ID
     * @return 昵称（用户不存在 / 昵称为空时返回 null，调用方兜底）
     */
    @Select("SELECT nickname FROM sys_user WHERE id = #{userId} AND is_deleted = 0")
    String selectNicknameById(@Param("userId") Long userId);
}
