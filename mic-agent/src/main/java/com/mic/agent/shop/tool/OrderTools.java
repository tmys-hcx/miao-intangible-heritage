package com.mic.agent.shop.tool;

import com.mic.agent.shop.context.AgentContext;
import com.mic.agent.shop.feign.OrderFeignClient;
import com.mic.agent.shop.model.dto.ShopOrderStatusDTO;
import com.mic.agent.shop.model.dto.ShopRefundApplyDTO;
import com.mic.agent.shop.model.vo.ShopOrderGoodsItemVO;
import com.mic.agent.shop.model.vo.ShopOrderVO;
import com.mic.common.model.dto.PageDto;
import com.mic.common.result.PageResult;
import com.mic.common.result.Result;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单（我的订单）工具集：由 ChatAssistant 通过 @AiService(tools = {"shopTools", "orderTools"}) 挂载给大模型调用。
 * 订单数据与订单操作（查询 / 取消 / 申请退款）必须经 OpenFeign 调用订单接口获取与提交，禁止大模型编造；
 * 订单归属由 FeignAuthRelayConfig 透传的 X-User-Id 可信头保证——所有工具只作用于当前登录用户本人的订单，
 * 无法查看或修改他人订单；提示词中已约束"先查询状态、与用户核对确认再执行取消 / 退款"。
 * 异常兜底：所有工具内的远程调用与业务异常均捕获后返回友好文本（而非抛错中断整轮对话）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTools {

    /** 后端统一业务成功码 */
    private static final int SUCCESS_CODE = 200;

    /** 订单列表单次展示条数（控制返回文本长度，避免大模型上下文膨胀） */
    private static final int ORDER_PAGE_SIZE = 10;

    /** 未登录统一提示（与 ShopTools 保持一致文案） */
    private static final String NOT_LOGIN_TIP = "当前未登录，无法执行该操作。请先登录后再继续（登录后重新发送消息即可）。";

    /** 远程服务异常统一提示 */
    private static final String SERVICE_ERROR_TIP = "服务暂时不可用，请稍后再试。";

    private final OrderFeignClient orderFeignClient;

    // ———————————————————— 订单查询 ————————————————————

    /**
     * 查询当前登录用户订单列表（可按状态筛选 / 关键词搜索），对应订单接口 GET /api/goods/order/page
     */
    @Tool(name = "listMyOrders", value = "查询当前登录用户本人的订单列表，可按订单状态筛选、可按订单编号或商品名称关键词搜索。用户询问订单状态、物流情况、哪些订单待发货或已发货、购买记录时调用。订单状态取值：待付款0、待发货1、已发货2、已收货3、已退款4、已取消5、已评价6、已完成7、退款申请中8；查询全部订单时状态传空")
    public String listMyOrders(@P("订单状态筛选编号：待付款0、待发货1、已发货2、已收货3、已退款4、已取消5、已评价6、已完成7、退款申请中8；不按状态筛选时传空") String orderStatus,
                               @P("搜索关键词：订单编号或商品名称（模糊匹配）；不搜索时传空") String keyword) {
        String loginTip = loginTipIfNeeded();
        if (loginTip != null) {
            return loginTip;
        }
        // 数字类参数统一用 String 接收再解析：大模型常用空字符串表示不筛选，直接声明为 Integer 会触发参数转换异常
        Integer orderStatusValue = parseOptionalInt(orderStatus);
        if (StringUtils.hasText(orderStatus)
                && (orderStatusValue == null || orderStatusValue < 0 || orderStatusValue > 8)) {
            return "订单状态筛选编号无效：有效取值为 0-8（待付款0、待发货1、已发货2、已收货3、已退款4、已取消5、已评价6、已完成7、退款申请中8），或传空查询全部订单。";
        }
        try {
            PageDto pageDto = new PageDto();
            pageDto.setSize(ORDER_PAGE_SIZE);
            Result<PageResult<ShopOrderVO>> result = orderFeignClient.pageOrders(
                    pageDto,
                    orderStatusValue,
                    StringUtils.hasText(keyword) ? keyword.trim() : null);
            if (!success(result)) {
                return "查询订单失败：" + failMsg(result);
            }
            PageResult<ShopOrderVO> page = result.getData();
            List<ShopOrderVO> orders = page == null ? null : page.getRecords();
            if (orders == null || orders.isEmpty()) {
                return orderStatusValue == null
                        ? "你还没有任何订单记录，可以看看商城里的非遗好物。"
                        : "你没有" + statusName(orderStatusValue) + "的订单。";
            }
            long total = page.getTotal() == null ? orders.size() : page.getTotal();
            StringBuilder text = orderStatusValue == null
                    ? new StringBuilder("你共有 ").append(total).append(" 笔订单：")
                    : new StringBuilder("你有 ").append(total).append(" 笔").append(statusName(orderStatusValue)).append("订单：");
            for (int i = 0; i < orders.size(); i++) {
                text.append("\n第 ").append(i + 1).append(" 笔：").append(orderSummary(orders.get(i)));
            }
            if (total > orders.size()) {
                text.append("\n以上为最近 ").append(orders.size()).append(" 笔，当前共 ").append(total)
                        .append(" 笔，可提供订单编号让我精确查询。");
            }
            return text.toString();
        } catch (Exception e) {
            log.error("[Agent工具] 查询订单列表失败：orderStatus={}, keyword={}", orderStatus, keyword, e);
            return SERVICE_ERROR_TIP;
        }
    }

    // ———————————————————— 取消订单 ————————————————————

    /**
     * 取消待付款订单（0待付款→5已取消），对应订单接口 PUT /api/goods/order/status/{orderNo}；
     * 工具内先查订单详情确认状态，已支付订单不改状态、直接返回引导申请退款的文案
     */
    @Tool(name = "cancelOrder", value = "取消当前登录用户的待付款订单（仅待付款订单可取消，取消后库存自动归还）。调用前提：已与用户确认取消意愿。若订单已支付则不能直接取消，应引导用户申请退款")
    public String cancelOrder(@P("要取消的订单编号（来自订单查询结果）") String orderNo) {
        String loginTip = loginTipIfNeeded();
        if (loginTip != null) {
            return loginTip;
        }
        if (!StringUtils.hasText(orderNo)) {
            return "请提供要取消的订单编号（可以先说\"查看我的订单\"，我帮你查询）。";
        }
        orderNo = orderNo.trim();
        try {
            Result<ShopOrderVO> detailResult = orderFeignClient.orderDetail(orderNo);
            if (!success(detailResult)) {
                return "查询订单失败：" + failMsg(detailResult);
            }
            ShopOrderVO order = detailResult.getData();
            if (order == null) {
                return "没有找到订单编号为 " + orderNo + " 的订单，请核对订单编号后再告诉我。";
            }
            Integer status = order.getOrderStatus();
            if (status == null) {
                return "暂时无法确认该订单的状态，请稍后再试。";
            }
            // 待付款：执行取消（服务端白名单兜底：仅 0待付款→5已取消，归还库存）
            if (status == 0) {
                ShopOrderStatusDTO dto = new ShopOrderStatusDTO();
                dto.setOrderStatus(5);
                Result<Void> cancelResult = orderFeignClient.updateStatus(orderNo, dto);
                if (!success(cancelResult)) {
                    return "取消订单失败：" + failMsg(cancelResult);
                }
                // 若本轮会话刚创建该订单并附带支付引导，取消后清除，避免前端再弹扫码支付
                AgentContext context = AgentContext.current();
                if (context != null) {
                    context.setPayOrderNo(null);
                }
                return "订单" + orderNo + "已成功取消，库存已归还。";
            }
            // 已支付等状态：不能直接取消
            if (status == 1 || status == 2 || status == 3 || status == 6 || status == 7) {
                return "订单" + orderNo + "当前状态为" + statusName(status)
                        + "（已支付），不能直接取消；如果不需要了，我可以帮你提交退款申请。";
            }
            if (status == 4) {
                return "订单" + orderNo + "已退款完成，无需取消。";
            }
            if (status == 5) {
                return "订单" + orderNo + "已经是已取消状态，无需重复取消。";
            }
            if (status == 8) {
                return "订单" + orderNo + "正在退款申请中，请等待审核结果，无需取消。";
            }
            return "订单" + orderNo + "当前状态为" + statusName(status) + "，暂无法取消。";
        } catch (Exception e) {
            log.error("[Agent工具] 取消订单失败：orderNo={}", orderNo, e);
            return SERVICE_ERROR_TIP;
        }
    }

    // ———————————————————— 申请退款 ————————————————————

    /**
     * 申请订单退款，对应订单接口 POST /api/goods/order/refund/{orderNo}；
     * 工具内先查订单详情核对状态：仅已支付且未收货（1待发货 / 2已发货）可提交，提交后进入 8退款申请中
     */
    @Tool(name = "applyRefund", value = "为当前登录用户提交订单退款申请（仅已支付且未收货的订单可申请，即待发货或已发货；提交后订单进入退款申请中，等待审核）。调用前提：已核对订单信息，并取得用户明确的退款意愿与退款原因")
    public String applyRefund(@P("要申请退款的订单编号（来自订单查询结果）") String orderNo,
                              @P("退款原因（必填，向用户收集，如：买错了、不想要了、尺寸不合适）") String refundReason) {
        String loginTip = loginTipIfNeeded();
        if (loginTip != null) {
            return loginTip;
        }
        if (!StringUtils.hasText(orderNo)) {
            return "请提供要申请退款的订单编号（可以先说\"查看我的订单\"，我帮你查询）。";
        }
        orderNo = orderNo.trim();
        if (!StringUtils.hasText(refundReason)) {
            return "请先告诉我退款原因（比如：买错了、不想要了），我再帮你提交退款申请。";
        }
        try {
            Result<ShopOrderVO> detailResult = orderFeignClient.orderDetail(orderNo);
            if (!success(detailResult)) {
                return "查询订单失败：" + failMsg(detailResult);
            }
            ShopOrderVO order = detailResult.getData();
            if (order == null) {
                return "没有找到订单编号为 " + orderNo + " 的订单，请核对订单编号后再告诉我。";
            }
            Integer status = order.getOrderStatus();
            if (status == null) {
                return "暂时无法确认该订单的状态，请稍后再试。";
            }
            // 已支付且未收货：提交退款申请
            if (status == 1 || status == 2) {
                ShopRefundApplyDTO dto = new ShopRefundApplyDTO();
                dto.setRefundReason(refundReason.trim());
                Result<Void> refundResult = orderFeignClient.applyRefund(orderNo, dto);
                if (!success(refundResult)) {
                    return "提交退款申请失败：" + failMsg(refundResult);
                }
                return "退款申请已提交成功，订单" + orderNo + "进入退款申请中，请等待管理员审核，进度可在" +
                        "个人中心的我的订单里查看。";
            }
            if (status == 0) {
                return "订单" + orderNo + "还未付款，无需申请退款；如果不想要了，我可以帮你直接取消订单。";
            }
            if (status == 8) {
                return "订单" + orderNo + "已经在退款申请中，请耐心等待审核结果。";
            }
            if (status == 4) {
                return "订单" + orderNo + "已退款完成，无需重复申请。";
            }
            if (status == 5) {
                return "订单" + orderNo + "已取消，无需申请退款。";
            }
            if (status == 3 || status == 6 || status == 7) {
                return "订单" + orderNo + "当前状态为" + statusName(status)
                        + "，已收货的订单不支持在助手里申请退款；如有售后需求，可以到个人中心的我的订单里联系卖家协商处理。";
            }
            return "订单" + orderNo + "当前状态为" + statusName(status) + "，暂不支持申请退款。";
        } catch (Exception e) {
            log.error("[Agent工具] 申请退款失败：orderNo={}", orderNo, e);
            return SERVICE_ERROR_TIP;
        }
    }

    // ———————————————————— 内部辅助 ————————————————————

    /** 业务结果是否成功（Result.code == 200） */
    private boolean success(Result<?> result) {
        return result != null && Integer.valueOf(SUCCESS_CODE).equals(result.getCode());
    }

    /** 业务失败提示文案（兜底防 null） */
    private String failMsg(Result<?> result) {
        if (result == null || !StringUtils.hasText(result.getMsg())) {
            return "未知错误";
        }
        return result.getMsg();
    }

    /** 需要登录的操作：未登录时返回统一提示，已登录返回 null */
    private String loginTipIfNeeded() {
        AgentContext context = AgentContext.current();
        return context == null || !context.loggedIn() ? NOT_LOGIN_TIP : null;
    }

    /** 模型参数解析：数字类参数统一按字符串接收（大模型常用空字符串表示无值），空 / 非数字返回 null */
    private Integer parseOptionalInt(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 订单状态中文名：0待付款 1待发货 2已发货 3已收货 4已退款 5已取消 6已评价 7已完成 8退款申请中 */
    private String statusName(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case 0 -> "待付款";
            case 1 -> "待发货";
            case 2 -> "已发货";
            case 3 -> "已收货";
            case 4 -> "已退款";
            case 5 -> "已取消";
            case 6 -> "已评价";
            case 7 -> "已完成";
            case 8 -> "退款申请中";
            default -> "未知";
        };
    }

    /** 单笔订单摘要（纯中文句子）：商品为X，数量1件，订单编号xxx，订单金额699元，当前状态为待发货。 */
    private String orderSummary(ShopOrderVO order) {
        StringBuilder text = new StringBuilder("商品为").append(goodsBrief(order))
                .append("，数量").append(nullSafe(order.getTotalNum())).append("件")
                .append("，订单编号").append(nullSafe(order.getOrderNo()))
                .append("，订单金额").append(money(order.getTotalPrice()))
                .append("，当前状态为").append(statusName(order.getOrderStatus()));
        // 退款申请中：附最近一次退款原因
        if (order.getOrderStatus() != null && order.getOrderStatus() == 8
                && StringUtils.hasText(order.getRefundReason())) {
            text.append("，退款原因：").append(order.getRefundReason());
        }
        // 驳回后可重新申请：附驳回原因
        if (StringUtils.hasText(order.getRefundRejectReason()) && order.getOrderStatus() != null
                && (order.getOrderStatus() == 1 || order.getOrderStatus() == 2)) {
            text.append("，此前退款申请被驳回，驳回原因：").append(order.getRefundRejectReason()).append("，可重新申请退款");
        }
        return text.append("。").toString();
    }

    /** 订单内商品名称简述（多商品顿号连接，明细为空时兜底文案） */
    private String goodsBrief(ShopOrderVO order) {
        List<ShopOrderGoodsItemVO> items = order.getItems();
        if (items == null || items.isEmpty()) {
            return "订单内商品";
        }
        StringBuilder brief = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                brief.append("、");
            }
            brief.append(nullSafe(items.get(i).getGoodsName()));
        }
        return brief.toString();
    }

    /** 金额展示：去掉多余小数位并带中文单位（699元 / 69.5元），为空时提示待定 */
    private String money(BigDecimal value) {
        return value == null ? "待定" : value.stripTrailingZeros().toPlainString() + "元";
    }

    /** 字段空值兜底展示 */
    private String nullSafe(Object value) {
        return value == null ? "未知" : String.valueOf(value);
    }
}
