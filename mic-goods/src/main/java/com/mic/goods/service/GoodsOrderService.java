package com.mic.goods.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mic.common.model.dto.PageDto;
import com.mic.common.result.PageResult;
import com.mic.goods.model.dto.OrderCreateDTO;
import com.mic.goods.model.entity.GoodsOrder;
import com.mic.goods.model.vo.OrderItemVO;
import com.mic.goods.model.vo.PayInfoVO;
import com.mic.goods.model.vo.PayPrepareVO;
import com.mic.goods.model.vo.SellerOrderVO;

/**
 * 商品订单服务接口
 * 拆单规则：一次购物车结算勾选的每个商品创建一张独立订单（各单独立金额 / 库存 / 发货 / 退款 / 评价），
 * 同批次订单共享结算批次号 batch_no，支付时整批一起支付（一次扫码支付覆盖批次内全部待付款订单）
 * 订单状态流转（标准闭环）：0待付款（沙箱模拟支付前置状态）→（买家付款）→ 1待发货
 * →（卖家发货）→ 2已发货 →（买家确认收货）→ 3已收货 →（买家评价）→ 6已评价 →（自动）→ 7已完成；
 * 1/2 阶段买家可申请退款（填写原因）→ 8退款申请中（待非遗人审核）：
 * 非遗人同意 → 4已退款（终态，恢复库存）；非遗人驳回（填写原因）→ 回到申请前状态，买家可重新申请；
 * 0待付款可主动取消，或超15分钟未支付由定时任务自动取消 → 5已取消（终态）
 * 库存策略：所有订单创建时即锁定库存；待付款订单超时自动取消 / 买家取消 / 删除时逐条归还；退款成功（非遗人同意）时逐条恢复
 * 沙箱模拟支付：发起支付（preparePayment）生成一次性令牌（token→batchNo）→ 手机扫码确认页（getPayInfo / mockPay）→
 * 批次内订单逐单支付成功 WebSocket 推送电脑端（PAID）；超时由定时任务逐单取消并推送（TIMEOUT）
 */
public interface GoodsOrderService extends IService<GoodsOrder> {

    /**
     * 创建订单（购物车结算 / 立即购买直购，按商品拆分为多张订单）：勾选的每个商品创建一张独立订单并共享结算批次号（batchNo），
     * dto.orderStatus=0 点击支付 / 取消创建（初始待付款，逐单锁定库存并写入 Redis 15分钟支付超时标记，
     * 跳转沙箱模拟支付页扫码支付；一次支付覆盖批次内全部待付款订单；超时未支付由定时任务逐单自动取消并归还库存）、
     * =1 直接创建待发货订单（兼容模式，逐单锁定库存）；两种状态均写入 DB、同步卖家订单至 Redis（购物车结算另清空对应购物车条目）
     * 直购模式（dto.buyNowNum 非空，商品详情页「立即购买」入口）：不读购物车、不校验购物车条目，
     * 按商品ID + 直购数量直接结算，仅支持单个商品，下单后不清空购物车
     * 流程：校验初始状态 → 校验地址归属 → 组装结算商品条目（直购 / 购物车两种来源）→ 生成批次号 → 逐商品（写订单与明细 → 锁定库存 → 写支付超时标记（待付款）→ 同步卖家订单）→ 清购物车（仅购物车结算）
     * @param dto 选中商品ID列表 + 收货地址ID + 订单备注（选填）+ 订单初始状态（0待付款 1待发货，不传默认1）+ 直购数量（选填，非空=立即购买模式）
     * @param userId 当前登录用户ID（网关透传）
     * @return 批次首张订单编号（结算页据此跳转沙箱模拟支付页，本次支付覆盖批次内全部待付款订单）
     */
    String createOrder(OrderCreateDTO dto, Long userId);

    /**
     * 我的订单分页（登录用户）：按创建时间倒序，可按订单状态筛选，支持订单号 / 商品名称模糊搜索
     * @param pageDto 分页参数（默认第1页、每页10条）
     * @param orderStatus 订单状态筛选（可选，空则查全部：0待付款 1待发货 2已发货 3已收货 4已退款 5已取消 6已评价 7已完成 8退款申请中）
     * @param keyword 搜索关键词（可选，模糊匹配订单号或商品名称，空则查全部）
     * @param userId 当前登录用户ID
     * @return 分页结果（含收货信息快照与商品明细）
     */
    PageResult<OrderItemVO> pageMyOrders(PageDto pageDto, Integer orderStatus, String keyword, Long userId);

    /**
     * 卖家订单分页（非遗人）：查询本人商品被购买的订单（Redis 卖家订单快照），支持订单号 / 商品名称模糊搜索
     * @param pageDto 分页参数（默认第1页、每页10条）
     * @param orderStatus 订单状态筛选（可选，空则查全部：0待付款 1待发货 2已发货 3已收货 4已退款 5已取消 6已评价 7已完成 8退款申请中）
     * @param keyword 搜索关键词（可选，模糊匹配订单号或商品名称，空则查全部）
     * @param sellerId 当前登录非遗人ID
     * @return 分页结果（按下单时间倒序）
     */
    PageResult<SellerOrderVO> pageSellerOrders(PageDto pageDto, Integer orderStatus, String keyword, Long sellerId);

    /**
     * 后台订单分页（管理员）：全部用户订单（含全部状态），按创建时间倒序，
     * 可按订单状态筛选，支持订单号 / 下单人账号模糊搜索
     * @param pageDto 分页参数（默认第1页、每页10条）
     * @param orderStatus 订单状态筛选（可选，空则查全部：0待付款 1待发货 2已发货 3已收货 4已退款 5已取消 6已评价 7已完成 8退款申请中）
     * @param keyword 搜索关键词（可选，模糊匹配订单编号或下单人账号，空则查全部）
     * @return 分页结果（含下单人账号、收货信息快照与商品明细）
     */
    PageResult<OrderItemVO> pageAdminOrders(PageDto pageDto, Integer orderStatus, String keyword);

    /**
     * 订单详情（登录用户）：仅可查看本人订单
     * @param orderNo 订单编号
     * @param userId 当前登录用户ID
     * @return 订单详情（含收货信息快照与商品明细）
     */
    OrderItemVO getOrderDetail(String orderNo, Long userId);

    /**
     * 发起支付（登录用户，沙箱模拟支付）：0待付款且未超时的本人订单——
     * 生成一次性支付令牌写入 Redis（miao:pay:token:{token}→batchNo，一次结算拆分的多张订单共享），
     * 并将批次内全部待付款订单的支付超时标记值刷新为令牌（两者 TTL 均为剩余支付时长），
     * 返回令牌 / 服务端局域网 IP / 剩余支付秒数 / 批次应付总额供电脑端生成确认页二维码
     * @param orderNo 订单编号（批次内任一张待付款订单，页面跳转默认批次首单）
     * @param userId 当前登录用户ID
     * @return 支付令牌 + 局域网IP + 剩余支付秒数 + 本次应付总金额
     */
    PayPrepareVO preparePayment(String orderNo, Long userId);

    /**
     * 支付信息（免登录，手机端扫码确认页）：凭支付令牌查询结算批次——批次首张订单号 / 批次应付总额 / 订单笔数 / 批次展示状态；
     * 令牌不存在（已支付 / 已超时 / 已取消）时抛出业务异常提示返回电脑端重新发起
     * @param token 支付令牌（二维码链接携带）
     * @return 首张订单号 + 批次应付总额 + 订单笔数 + 批次展示状态
     */
    PayInfoVO getPayInfo(String token);

    /**
     * 模拟支付（免登录，手机端扫码确认页）：手机端点击「模拟支付」——一次支付覆盖结算批次内全部待付款订单：
     * 逐单条件更新抢占（仅 0待付款 → 1待发货，防止重复扫码支付；批次内已被单独取消的订单自动跳过）、
     * 逐单同步卖家订单快照、清除支付会话，最后逐单 WebSocket 推送电脑端支付成功消息
     * @param token 支付令牌（二维码链接携带）
     */
    void mockPay(String token);

    /**
     * 申请退款（登录用户）：未收货前的订单（1待发货 / 2已发货）可申请，须填写退款原因，
     * 订单状态置 8退款申请中（等待非遗人审核），并同步卖家订单快照（携带退款原因）；同意后恢复库存
     * @param orderNo 订单编号
     * @param refundReason 退款申请原因（买家填写）
     * @param userId 当前登录用户ID
     */
    void refundOrder(String orderNo, String refundReason, Long userId);

    /**
     * 同意退款（非遗人）：退款申请中的订单（8）由本订单卖家之一同意后流转为 4已退款（终态），
     * 并逐条恢复商品库存、同步卖家订单快照
     * @param orderNo 订单编号
     * @param sellerId 当前登录非遗人ID（须为本订单卖家之一）
     */
    void agreeRefund(String orderNo, Long sellerId);

    /**
     * 驳回退款申请（非遗人）：退款申请中的订单（8）由本订单卖家之一驳回，须填写驳回原因，
     * 订单恢复到申请前状态（1待发货 / 2已发货），买家可修改原因后重新申请
     * @param orderNo 订单编号
     * @param rejectReason 退款驳回原因（非遗人填写）
     * @param sellerId 当前登录非遗人ID（须为本订单卖家之一）
     */
    void rejectRefund(String orderNo, String rejectReason, Long sellerId);

    /**
     * 更新订单状态（登录用户）：按目标状态分派校验与流转
     * 1付款（买家）：0待付款 → 1待发货（库存已在下单时锁定，付款仅流转状态并清除支付会话）
     * 2发货（卖家）：1待发货 → 2已发货（校验当前用户为本订单卖家之一）
     * 3收货（买家）：2已发货 → 3已收货
     * 5取消（买家）：0待付款 → 5已取消（逐条归还下单时锁定的库存并清除支付会话）
     * @param orderNo 订单编号
     * @param targetStatus 目标状态：1付款 2发货 3收货 5取消（4已退款走同意退款接口；8退款申请中走申请退款接口；6已评价 / 7已完成由评价接口自动流转）
     * @param userId 当前登录用户ID
     */
    void updateOrderStatus(String orderNo, Integer targetStatus, Long userId);

    /**
     * 删除订单（登录用户）：逻辑删除，仅可删除本人订单；进行中订单（1待发货 / 2已发货 / 3已收货 / 8退款申请中）需先完成交易 / 退款处理 / 取消；
     * 删除 0待付款 订单时逐条归还下单时锁定的库存并清除支付会话
     * @param orderNo 订单编号
     * @param userId 当前登录用户ID
     */
    void deleteOrder(String orderNo, Long userId);

    /**
     * 待付款订单超时自动取消（定时任务调用）：0待付款且创建超过15分钟未支付的订单 → 5已取消，
     * 逐条归还下单时锁定的库存、清除 Redis 支付会话（超时标记 / 令牌），并 WebSocket 推送超时通知（TIMEOUT）
     * @return 本次取消的订单数
     */
    int cancelTimeoutOrders();

    /**
     * 评价完成后自动流转订单状态（评价服务调用）：3已收货 → 6已评价 →（自动）→ 7已完成，并同步卖家订单快照
     * @param orderId 订单ID
     */
    void autoFinishAfterEvaluate(Long orderId);

    /**
     * 校验订单明细是否包含指定商品（评价校验：多商品订单中仅包含的商品可被评价）
     * @param orderId 订单ID
     * @param goodsId 商品ID
     * @return 订单明细中存在该商品返回 true
     */
    boolean orderContainsGoods(Long orderId, Long goodsId);
}
