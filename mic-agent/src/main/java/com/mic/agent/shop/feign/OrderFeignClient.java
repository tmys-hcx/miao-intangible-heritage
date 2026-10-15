package com.mic.agent.shop.feign;

import com.mic.agent.shop.model.dto.ShopOrderStatusDTO;
import com.mic.agent.shop.model.dto.ShopRefundApplyDTO;
import com.mic.agent.shop.model.vo.ShopOrderVO;
import com.mic.common.model.dto.PageDto;
import com.mic.common.model.feign.FeignConstant;
import com.mic.common.result.PageResult;
import com.mic.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 订单服务 Feign 客户端（Agent 直连）：
 * 说明：本项目订单域归属交易域 mic-goods（/api/goods/order，与购物车 / 地址同库同事务），
 * 未拆分独立 mic-order 服务，故仍按服务名 mic-goods 调用（contextId 区分，避免与 GoodsFeignClient 的
 * Feign 配置 Bean 名冲突）；若后续订单拆分为独立微服务，只需把 name 改为新服务名。
 * 覆盖订单查询（我的订单分页 / 订单详情）、取消订单（改状态）、申请退款接口契约；
 * 用户身份不通过方法参数传递——由 FeignAuthRelayConfig 拦截器统一透传 X-User-Id / X-User-Role 可信头，
 * 下游接口据此把订单查询与操作限定在当前登录用户本人，无法查看或操作他人订单。
 */
@FeignClient(name = FeignConstant.SERVICE_GOODS, contextId = "orderFeignClient", path = "/api/goods/order")
public interface OrderFeignClient {

    /**
     * 我的订单分页（登录用户）：仅返回本人订单，支持按状态筛选 / 订单编号或商品名称关键词搜索
     *
     * @param pageDto     分页参数（current / size）
     * @param orderStatus 订单状态筛选：0待付款 1待发货 2已发货 3已收货 4已退款 5已取消 6已评价 7已完成 8退款申请中（可空）
     * @param keyword     关键词：匹配订单编号 / 商品名称（可空）
     */
    @GetMapping("/page")
    Result<PageResult<ShopOrderVO>> pageOrders(@SpringQueryMap PageDto pageDto,
                                               @RequestParam(value = "orderStatus", required = false) Integer orderStatus,
                                               @RequestParam(value = "keyword", required = false) String keyword);

    /**
     * 订单详情（登录用户）：仅能查询本人订单（服务端校验归属）
     */
    @GetMapping("/{orderNo}")
    Result<ShopOrderVO> orderDetail(@PathVariable("orderNo") String orderNo);

    /**
     * 更新订单状态（登录用户）：取消订单传 orderStatus=5（仅 0待付款→5已取消，归还库存，服务端白名单兜底校验）
     */
    @PutMapping("/status/{orderNo}")
    Result<Void> updateStatus(@PathVariable("orderNo") String orderNo, @RequestBody ShopOrderStatusDTO dto);

    /**
     * 申请退款（登录用户）：仅已支付且未收货的订单（1待发货 / 2已发货）可申请，
     * 提交后订单进入 8退款申请中，等待审核
     */
    @PostMapping("/refund/{orderNo}")
    Result<Void> applyRefund(@PathVariable("orderNo") String orderNo, @RequestBody ShopRefundApplyDTO dto);
}
