package com.mic.goods.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.mic.common.exception.BusinessException;
import com.mic.common.model.dto.PageDto;
import com.mic.common.result.PageResult;
import com.mic.common.result.Result;
import com.mic.common.result.ResultCodeEnum;
import com.mic.goods.model.dto.OrderCreateDTO;
import com.mic.goods.model.dto.OrderStatusDTO;
import com.mic.goods.model.dto.RefundApplyDTO;
import com.mic.goods.model.dto.RefundRejectDTO;
import com.mic.goods.model.vo.OrderItemVO;
import com.mic.goods.model.vo.PayInfoVO;
import com.mic.goods.model.vo.PayPrepareVO;
import com.mic.goods.model.vo.SellerOrderVO;
import com.mic.goods.service.GoodsOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品订单接口--用户端：创建订单、订单分页、订单详情、申请退款、更新状态、删除订单
 * 商品订单接口--退款处理（非遗人）：同意退款、拒绝退款
 * 商品订单接口--后台订单分页（管理员）：全平台用户订单查询、状态筛选、订单号 / 下单人账号模糊搜索
 * 商品订单接口--沙箱模拟支付：发起支付（登录）→ 电脑端模拟支付页扫二维码 → 手机端确认页免登录（凭一次性令牌）；
 * 一次结算按商品拆分为多张订单共享结算批次号，一次扫码支付整批订单；
 * 批次内订单逐单支付成功 / 超时取消后经 WebSocket（/api/goods/order/ws/pay?orderNo=xxx）推送电脑端
 * 除支付信息 / 模拟支付（手机端凭令牌免登录）外，全部接口需登录（网关鉴权后透传 X-User-Id 可信头），登录用户仅可操作本人订单
 */
@Tag(name = "商品订单接口（用户端）")
@RestController
@Slf4j
@RequestMapping("/api/goods/order")
@RequiredArgsConstructor
public class GoodsOrderController {

    /** 角色：0管理员（后台订单分页） */
    private static final int ROLE_ADMIN = 0;

    private final GoodsOrderService goodsOrderService;

    /**
     * 创建订单（登录用户）：购物车结算，按商品拆分为多张独立订单（共享结算批次号，一次支付整批订单）；
     * orderStatus=0点击支付（初始待付款，逐单锁定库存并写入 Redis 15分钟支付超时标记，
     * 跳转沙箱模拟支付页扫码支付；超时未支付由定时任务逐单自动取消并归还库存）、
     * =1直接购买（初始待发货，逐单锁定库存）；校验地址与购物车选中商品后逐商品生成唯一订单编号写入订单表、
     * 清空对应购物车记录、复制收货地址快照并同步卖家订单至 Redis
     */
    @Operation(summary = "创建订单（登录用户）：购物车结算按商品拆分多张订单（传选中购物车商品ID列表+收货地址ID+备注+初始状态orderStatus），返回批次首张订单编号")
    @PostMapping
    @SentinelResource(value = "order_create", blockHandler = "createOrderBlocked", fallback = "createOrderFallback",
            exceptionsToIgnore = BusinessException.class)
    public Result<String> add(@Valid @RequestBody OrderCreateDTO dto,
                              @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        checkLogin(userId);
        return Result.ok(goodsOrderService.createOrder(dto, userId));
    }

    /**
     * 我的订单分页（登录用户）：按创建时间倒序，可按订单状态筛选，支持订单号 / 商品名称模糊搜索
     */
    @Operation(summary = "我的订单分页（登录用户）：默认每页10条，可传orderStatus筛选（0待付款 1待发货 2已发货 3已收货 4已退款 5已取消 6已评价 7已完成 8退款申请中），keyword模糊搜索订单号/商品名称")
    @GetMapping("/page")
    public Result<PageResult<OrderItemVO>> page(PageDto pageDto,
                                                @RequestParam(value = "orderStatus", required = false) Integer orderStatus,
                                                @RequestParam(value = "keyword", required = false) String keyword,
                                                @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        checkLogin(userId);
        return Result.ok(goodsOrderService.pageMyOrders(pageDto, orderStatus, keyword, userId));
    }

    /**
     * 卖家订单分页（非遗人）：查询本人商品被购买的订单（Redis 卖家订单快照），按下单时间倒序，支持订单号 / 商品名称模糊搜索
     */
    @Operation(summary = "卖家订单分页（非遗人）：本人商品被购买的订单，可传orderStatus筛选（0待付款 1待发货 2已发货 3已收货 4已退款 5已取消 6已评价 7已完成 8退款申请中），keyword模糊搜索订单号/商品名称")
    @GetMapping("/seller/page")
    public Result<PageResult<SellerOrderVO>> sellerPage(PageDto pageDto,
                                                        @RequestParam(value = "orderStatus", required = false) Integer orderStatus,
                                                        @RequestParam(value = "keyword", required = false) String keyword,
                                                        @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        checkLogin(userId);
        return Result.ok(goodsOrderService.pageSellerOrders(pageDto, orderStatus, keyword, userId));
    }

    /**
     * 后台订单分页（管理员）：全部用户订单（含全部状态），按创建时间倒序，
     * 可按订单状态筛选，支持订单号 / 下单人账号模糊搜索
     */
    @Operation(summary = "后台订单分页（管理员）：默认每页10条，可传orderStatus筛选（0待付款 1待发货 2已发货 3已收货 4已退款 5已取消 6已评价 7已完成 8退款申请中），keyword模糊搜索订单号/下单人账号")
    @GetMapping("/admin/page")
    public Result<PageResult<OrderItemVO>> adminPage(PageDto pageDto,
                                                     @RequestParam(value = "orderStatus", required = false) Integer orderStatus,
                                                     @RequestParam(value = "keyword", required = false) String keyword,
                                                     @RequestHeader(value = "X-User-Role", required = false) Integer role) {
        checkAdmin(role);
        return Result.ok(goodsOrderService.pageAdminOrders(pageDto, orderStatus, keyword));
    }

    /**
     * 订单详情（登录用户）：仅可查看本人订单
     */
    @Operation(summary = "订单详情（登录用户）：仅可查看本人订单")
    @GetMapping("/{orderNo}")
    public Result<OrderItemVO> detail(@PathVariable("orderNo") String orderNo,
                                      @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        checkLogin(userId);
        return Result.ok(goodsOrderService.getOrderDetail(orderNo, userId));
    }

    // ———————————————— 沙箱模拟支付（电脑端扫码 + 手机端确认 + WebSocket 推送） ————————————————

    /**
     * 发起支付（登录用户）：0待付款且未超时的本人订单——生成一次性支付令牌写入 Redis（token→批次号，TTL 与剩余支付时长一致），
     * 并将批次内全部待付款订单的支付超时标记刷新为令牌，返回令牌 / 服务端局域网 IP / 剩余支付秒数 / 批次应付总额，
     * 电脑端模拟支付页据此生成手机可访问的确认页二维码（一次扫码支付覆盖批次内全部待付款订单）
     */
    @Operation(summary = "发起支付（登录用户）：0待付款订单按结算批次生成一次性支付令牌+局域网IP+剩余支付秒数+批次应付总额（电脑端生成二维码用）")
    @PostMapping("/pay/prepare/{orderNo}")
    @SentinelResource(value = "pay_prepare", blockHandler = "preparePayBlocked", fallback = "preparePayFallback",
            exceptionsToIgnore = BusinessException.class)
    public Result<PayPrepareVO> preparePay(@PathVariable("orderNo") String orderNo,
                                           @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        checkLogin(userId);
        return Result.ok(goodsOrderService.preparePayment(orderNo, userId));
    }

    /**
     * 支付信息（免登录，手机端扫码确认页）：凭二维码携带的一次性支付令牌查询结算批次——
     * 首张订单号 / 批次应付总额 / 订单笔数 / 批次展示状态；
     * 令牌失效（已支付 / 已超时 / 已取消）时抛业务异常，提示返回电脑端重新发起
     */
    @Operation(summary = "支付信息（免登录，手机端扫码确认页）：凭token查询批次首张订单号/批次应付总额/订单笔数/批次展示状态")
    @GetMapping("/pay/info")
    public Result<PayInfoVO> payInfo(@RequestParam("token") String token) {
        return Result.ok(goodsOrderService.getPayInfo(token));
    }

    /**
     * 模拟支付（免登录，手机端扫码确认页）：手机端点「模拟支付」——一次支付覆盖批次内全部待付款订单：
     * 逐单条件更新订单（0待付款→1待发货，防重复支付；已单独取消的订单自动跳过）、
     * 逐单清除 Redis 支付会话（超时标记 / 令牌），并通过 WebSocket 向电脑端模拟支付页逐单推送支付成功消息
     */
    @Operation(summary = "模拟支付（免登录，手机端扫码确认页）：凭token将批次内全部待付款订单置为1待发货，成功后 WebSocket 逐单推送电脑端")
    @PostMapping("/pay/mock")
    @SentinelResource(value = "pay_mock", blockHandler = "mockPayBlocked", fallback = "mockPayFallback",
            exceptionsToIgnore = BusinessException.class)
    public Result<Void> mockPay(@RequestParam("token") String token) {
        goodsOrderService.mockPay(token);
        return Result.ok();
    }

    /**
     * 申请退款（登录用户）：未收货前的订单（1待发货 / 2已发货）可申请，须填写退款原因，
     * 提交后订单进入 8退款申请中，同步至非遗人「我卖出的」等待审核（同意退款 / 驳回后可重新申请）
     */
    @Operation(summary = "申请退款（登录用户）：未收货前的订单（1待发货 2已发货）可申请（传退款原因refundReason），订单进入 8退款申请中 等待非遗人审核")
    @PostMapping("/refund/{orderNo}")
    public Result<Void> refund(@PathVariable("orderNo") String orderNo,
                               @Valid @RequestBody RefundApplyDTO dto,
                               @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        checkLogin(userId);
        goodsOrderService.refundOrder(orderNo, dto.getRefundReason(), userId);
        return Result.ok();
    }

    /**
     * 同意退款（非遗人）：退款申请中的订单（8）由本订单卖家之一同意后流转为 4已退款，并恢复商品库存
     */
    @Operation(summary = "同意退款（非遗人）：8退款申请中 → 4已退款（恢复库存），仅本订单卖家可操作")
    @PutMapping("/refund/agree/{orderNo}")
    public Result<Void> agreeRefund(@PathVariable("orderNo") String orderNo,
                                    @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        checkLogin(userId);
        goodsOrderService.agreeRefund(orderNo, userId);
        return Result.ok();
    }

    /**
     * 拒绝退款（非遗人）：退款申请中的订单（8）由本订单卖家之一驳回（须填写驳回原因），
     * 订单恢复到申请前状态（1待发货 / 2已发货），买家可修改原因后重新申请
     */
    @Operation(summary = "拒绝退款（非遗人）：驳回退款申请（传驳回原因rejectReason），订单恢复到申请前状态，买家可重新申请")
    @PutMapping("/refund/reject/{orderNo}")
    public Result<Void> rejectRefund(@PathVariable("orderNo") String orderNo,
                                     @Valid @RequestBody RefundRejectDTO dto,
                                     @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        checkLogin(userId);
        goodsOrderService.rejectRefund(orderNo, dto.getRejectReason(), userId);
        return Result.ok();
    }

    /**
     * 更新订单状态（登录用户）：1付款（买家：0待付款→1待发货，库存已在下单时锁定，仅流转状态并清除支付会话）、
     * 2发货（卖家：1待发货→2已发货）、3收货（买家：2已发货→3已收货）、5取消（买家：0待付款→5已取消，归还库存）
     */
    @Operation(summary = "更新订单状态（登录用户）：orderStatus=1付款（0待付款→1待发货）、2发货（1待发货→2已发货）、3收货（2已发货→3已收货）、5取消（0待付款→5已取消，归还库存）")
    @PutMapping("/status/{orderNo}")
    public Result<Void> updateStatus(@PathVariable("orderNo") String orderNo,
                                     @Valid @RequestBody OrderStatusDTO dto,
                                     @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        checkLogin(userId);
        goodsOrderService.updateOrderStatus(orderNo, dto.getOrderStatus(), userId);
        return Result.ok();
    }

    /**
     * 删除订单（登录用户）：逻辑删除，进行中订单（1待发货 / 2已发货 / 3已收货 / 8退款申请中）需先完成交易 / 处理退款申请 / 取消
     */
    @Operation(summary = "删除订单（登录用户）：逻辑删除，进行中订单需先完成交易 / 处理退款申请 / 取消")
    @DeleteMapping("/{orderNo}")
    public Result<Void> delete(@PathVariable("orderNo") String orderNo,
                               @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        checkLogin(userId);
        goodsOrderService.deleteOrder(orderNo, userId);
        return Result.ok();
    }

    /**
     * 校验登录态：网关鉴权通过后透传 X-User-Id 可信头，缺失视为未登录
     */
    private void checkLogin(Long userId) {
        if (userId == null) {
            throw new BusinessException(ResultCodeEnum.NOT_LOGIN);
        }
    }

    /**
     * 校验管理员角色：网关鉴权通过后透传 X-User-Role 可信头，非0（管理员）拒绝操作
     */
    private void checkAdmin(Integer role) {
        if (role == null || role != ROLE_ADMIN) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN, "无操作权限，仅管理员可查看后台订单");
        }
    }

    // ———————————————— Sentinel 限流 / 熔断降级兜底 ————————————————
    // blockHandler：资源被限流或熔断打开（BlockException 及其子类）时执行，返回友好降级提示；
    // fallback：资源内抛系统异常（业务异常已 exceptionsToIgnore 排除）时执行，返回降级提示；
    // 规范：方法签名与原方法参数一致 + 末尾 BlockException / Throwable，返回值类型与原方法一致。

    /** 创建订单：限流 / 熔断被拒时降级返回（order_create：QPS 限流） */
    public Result<String> createOrderBlocked(OrderCreateDTO dto, Long userId, BlockException ex) {
        log.warn("[Sentinel] 创建订单触发限流保护（{}），blockHandler 降级", ex.getClass().getSimpleName());
        return Result.fail(ResultCodeEnum.ERROR, "当前下单人数较多，请稍后再试。");
    }

    /** 创建订单：系统异常时降级返回 */
    public Result<String> createOrderFallback(OrderCreateDTO dto, Long userId, Throwable t) {
        log.error("[Sentinel] 创建订单执行异常，fallback 降级", t);
        return Result.fail(ResultCodeEnum.ERROR, "下单服务暂时不可用，请稍后重试。");
    }

    /** 发起支付：限流 / 熔断被拒时降级返回（pay_prepare：异常比例熔断，熔断打开期间所有请求直接拒绝） */
    public Result<PayPrepareVO> preparePayBlocked(String orderNo, Long userId, BlockException ex) {
        log.warn("[Sentinel] 发起支付触发熔断保护（{}），blockHandler 降级", ex.getClass().getSimpleName());
        return Result.fail(ResultCodeEnum.ERROR, "支付服务繁忙，请稍后再试。");
    }

    /** 发起支付：系统异常时降级返回 */
    public Result<PayPrepareVO> preparePayFallback(String orderNo, Long userId, Throwable t) {
        log.error("[Sentinel] 发起支付执行异常，fallback 降级", t);
        return Result.fail(ResultCodeEnum.ERROR, "支付服务暂时不可用，请稍后重试。");
    }

    /** 模拟支付：限流 / 熔断被拒时降级返回（pay_mock：慢调用比例熔断） */
    public Result<Void> mockPayBlocked(String token, BlockException ex) {
        log.warn("[Sentinel] 模拟支付触发熔断保护（{}），blockHandler 降级", ex.getClass().getSimpleName());
        return Result.fail(ResultCodeEnum.ERROR, "支付处理繁忙，请稍等片刻后重试。");
    }

    /** 模拟支付：系统异常时降级返回 */
    public Result<Void> mockPayFallback(String token, Throwable t) {
        log.error("[Sentinel] 模拟支付执行异常，fallback 降级", t);
        return Result.fail(ResultCodeEnum.ERROR, "支付处理出现异常，请稍后重试。");
    }
}
