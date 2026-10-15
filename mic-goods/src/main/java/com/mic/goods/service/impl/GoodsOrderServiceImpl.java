package com.mic.goods.service.impl;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mic.common.exception.BusinessException;
import com.mic.common.model.dto.NoticeCreateDTO;
import com.mic.common.model.dto.PageDto;
import com.mic.common.model.mq.MqConstant;
import com.mic.common.result.PageResult;
import com.mic.common.result.ResultCodeEnum;
import com.mic.common.util.DateUtil;
import com.mic.common.util.UUIDUtil;
import com.mic.goods.config.PayWebSocketHandler;
import com.mic.goods.mapper.GoodsOrderItemMapper;
import com.mic.goods.mapper.GoodsOrderMapper;
import com.mic.goods.model.dto.OrderCreateDTO;
import com.mic.goods.model.entity.GoodsOrder;
import com.mic.goods.model.entity.GoodsOrderItem;
import com.mic.goods.model.entity.IntangibleGoods;
import com.mic.goods.model.entity.UserAddress;
import com.mic.goods.model.vo.CartItemVO;
import com.mic.goods.model.vo.OrderGoodsItemVO;
import com.mic.goods.model.vo.OrderItemVO;
import com.mic.goods.model.vo.PayInfoVO;
import com.mic.goods.model.vo.PayPrepareVO;
import com.mic.goods.model.vo.SellerOrderVO;
import com.mic.goods.service.GoodsOrderService;
import com.mic.goods.service.IntangibleGoodsService;
import com.mic.goods.service.ShoppingCartService;
import com.mic.goods.service.UserAddressService;
import com.mic.goods.tcc.StockTccAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.seata.spring.annotation.GlobalTransactional;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 商品订单服务实现（购物车结算 → 按商品拆分为多张订单）
 * 拆单规则：一次结算勾选的每个商品创建一张独立订单（各单独立金额 / 库存 / 发货 / 退款 / 评价），
 * 同批次订单共享结算批次号 batch_no，支付时整批一起支付（一次扫码支付覆盖批次内全部待付款订单）
 * 订单状态流转（标准闭环）：0待付款（沙箱模拟支付前置状态）→（买家付款）→ 1待发货
 * →（卖家发货）→ 2已发货 →（买家确认收货）→ 3已收货 →（买家评价）→ 6已评价 →（自动）→ 7已完成；
 * 待发货 / 已发货 阶段买家可申请退款（填写原因）→ 8退款申请中（待非遗人审核）：
 * 非遗人同意 → 4已退款（终态，恢复库存）；非遗人驳回（填写原因）→ 回到申请前状态，买家可重新申请；
 * 0待付款超15分钟未支付由定时任务自动取消 → 5已取消（终态）
 * 库存策略（Seata TCC 分布式事务）：下单开启全局事务，逐商品 Try 在 Redis 原子预扣可用库存（防并发超卖）并写冻结流水，
 * 本地事务成功后 TC 自动 Confirm 将 DB 库存落库扣减；任一步失败 TC 自动 Cancel 还原 Redis 预扣；
 * 待付款订单超时自动取消 / 买家取消 / 删除时逐条归还（DB + Redis 同步回补）；退款成功（非遗人同意）时逐条恢复
 * 沙箱模拟支付：结算页点击支付生成批次 0待付款 订单并逐单写入 Redis 支付超时标记（miao:pay:order:{orderNo}，TTL 15分钟）；
 * 电脑端模拟支付页发起支付生成一次性令牌（miao:pay:token:{token}→batchNo）并建立 WebSocket 订阅；
 * 手机扫码确认页点击「模拟支付」→ 批次内全部待付款订单 0待付款→1待发货、清除支付会话、逐单 WebSocket 推送支付成功（PAID）；
 * 超时未支付 → 定时任务逐单自动取消（归还库存、清除支付会话）并推送超时通知（TIMEOUT）
 * 卖家订单：下单时按卖家分组生成快照存入 Redis（miao:seller:order:{sellerId}，field=订单号，value=JSON），供非遗人订单界面查询；
 * 买家申请退款时同步快照状态 8 与退款原因，非遗人端随即出现同意 / 拒绝入口
 * 订单通知（RabbitMQ 异步）：支付成功 / 发货 / 退款申请 / 退款结果事件组装 NoticeCreateDTO 消息投递 mic-notice
 * （交换机 mic.notice.exchange → 队列 mic.order.notice.queue），由通知服务消费落库 notification 表；发送失败仅告警不影响订单主流程
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoodsOrderServiceImpl extends ServiceImpl<GoodsOrderMapper, GoodsOrder> implements GoodsOrderService {

    /** 订单状态：0待付款（结算页点击支付创建订单的初始状态，创建时即锁定库存并写入 Redis 支付超时标记；超15分钟未支付自动取消并归还库存） */
    private static final int STATUS_PENDING_PAY = 0;

    /** 订单状态：1待发货（直接购买创建的初始状态 / 待付款订单模拟支付成功后进入该状态，库存已在订单创建时锁定，卖家发货操作的前置状态） */
    private static final int STATUS_WAIT_SHIP = 1;

    /** 订单状态：2已发货（卖家发货后，买家确认收货的前置状态；该阶段仍可申请退款） */
    private static final int STATUS_SHIPPED = 2;

    /** 订单状态：3已收货（买家确认收货后，买家评价操作的前置状态） */
    private static final int STATUS_RECEIVED = 3;

    /** 订单状态：4已退款（非遗人同意退款后逐条恢复商品库存；终态） */
    private static final int STATUS_REFUNDED = 4;

    /** 订单状态：5已取消（买家主动取消或超时未支付自动取消；终态） */
    private static final int STATUS_CANCELLED = 5;

    /** 订单状态：6已评价（买家提交评价后的过渡状态，随后自动流转为已完成） */
    private static final int STATUS_EVALUATED = 6;

    /** 订单状态：7已完成（评价后自动完成；终态） */
    private static final int STATUS_FINISHED = 7;

    /** 订单状态：8退款申请中（买家已提交退款申请，等待非遗人审核：同意→4已退款；驳回→恢复申请前状态） */
    private static final int STATUS_REFUND_APPLY = 8;

    /** 待付款订单超时时长（分钟）：超过该时长未支付的订单由定时任务自动取消 */
    private static final int PAY_TIMEOUT_MINUTES = 15;

    /** 商品状态：1审核通过（上架，可下单） */
    private static final int GOODS_STATUS_APPROVED = 1;

    /** Redis 卖家订单 key 前缀：miao:seller:order:{sellerId}（Hash：field=订单号，value=JSON快照） */
    private static final String REDIS_SELLER_ORDER_KEY_PREFIX = "miao:seller:order:";

    /** Redis 支付超时标记 key 前缀：miao:pay:order:{orderNo} → 支付令牌（空=尚未生成令牌；TTL=支付剩余时长，超时后由定时任务取消订单） */
    private static final String REDIS_PAY_ORDER_KEY_PREFIX = "miao:pay:order:";

    /** Redis 支付令牌 key 前缀：miao:pay:token:{payToken} → 订单编号（手机端确认页免登录查看 / 支付凭据；TTL=支付剩余时长） */
    private static final String REDIS_PAY_TOKEN_KEY_PREFIX = "miao:pay:token:";

    /** 通知类型：5购物订单（买家支付成功 → 卖家「新订单」；卖家发货 → 买家「已发货」） */
    private static final int NOTIFY_TYPE_ORDER = 5;

    /** 通知类型：6退款通知（买家申请 → 卖家；非遗人同意 / 驳回 → 买家） */
    private static final int NOTIFY_TYPE_REFUND = 6;

    /** 通知业务类型：order商品订单（biz_id 存订单编号，点击跳转用） */
    private static final String NOTIFY_BIZ_TYPE_ORDER = "order";

    /** 通知标题最大长度（与 notification.title varchar(128) 对齐） */
    private static final int NOTIFY_TITLE_MAX = 128;

    /** 通知内容最大长度（与 notification.content varchar(512) 对齐） */
    private static final int NOTIFY_CONTENT_MAX = 512;

    /** 非遗商品服务：校验下单商品有效性 + 扣减 / 恢复库存 */
    private final IntangibleGoodsService intangibleGoodsService;

    /** 收货地址服务：下单时校验地址归属并复制为订单收货信息快照 */
    private final UserAddressService userAddressService;

    /** 购物车服务：读取选中商品条目，下单成功后清空对应条目 */
    private final ShoppingCartService shoppingCartService;

    /** 订单明细Mapper */
    private final GoodsOrderItemMapper goodsOrderItemMapper;

    /** StringRedisTemplate：清购物车 / 卖家订单 Hash / 支付会话直接使用原生模板 */
    private final StringRedisTemplate stringRedisTemplate;

    /** 模拟支付 WebSocket 推送处理器：支付成功 / 超时取消时向电脑端订阅会话推送消息 */
    private final PayWebSocketHandler payWebSocketHandler;

    /** RabbitMQ 发送模板：订单通知消息异步投递（发货 → 买家 / 退款申请 → 卖家等，mic-notice 消费落库；发送失败仅告警不影响订单主流程） */
    private final RabbitTemplate rabbitTemplate;

    /** 库存预留 TCC 事务动作（Seata 代理）：下单 Try 在 Redis 预扣可用库存，Confirm/Cancel 由 TC 自动调度 */
    private final StockTccAction stockTccAction;



    /**
     * 创建订单（购物车结算 / 立即购买直购，按商品拆分多张订单）：勾选的每个商品创建一张独立订单（共享结算批次号 batchNo），
     * 各单独立计算金额、独立锁定库存、独立写入 Redis 支付超时标记并同步卖家订单快照；
     * 一次结算只需支付一次：支付会话按批次组织（token→batchNo），支付成功后批次内订单整批进入待发货
     * dto.orderStatus=0 点击支付 / 取消创建（初始待付款，创建时锁定库存并写入 Redis 15分钟支付超时标记，
     * 跳转沙箱模拟支付页扫码支付；超时未支付由定时任务逐单自动取消并归还库存）、
     * =1 直接创建待发货订单（兼容模式，创建时同样锁定库存）；两种状态均同步卖家订单至 Redis（购物车结算另清空对应购物车记录）
     * 直购模式：dto.buyNowNum 非空时按商品ID + 直购数量构造结算条目（仅支持单个商品，不读购物车、不清购物车）
     * 事务保证（Seata TCC 分布式事务）：@GlobalTransactional 开启全局事务，库存预留为 TCC 分支（Redis Try → DB Confirm），
     * 订单 / 明细 / 库存预扣任一步失败整体回滚（订单 DB 随本地事务回滚，Redis 预扣由 TC 调度 Cancel 还原）；
     * Redis 购物车清理随事务末尾执行，数据以 MySQL 为准
     * @return 批次首张订单编号（结算页据此跳转沙箱模拟支付页，本次支付覆盖批次内全部待付款订单）
     */
    @Override
    @GlobalTransactional(rollbackFor = Exception.class)
    @Transactional(rollbackFor = Exception.class)
    public String createOrder(OrderCreateDTO dto, Long userId) {
        // 0. 校验订单初始状态：0待付款（点击支付创建，创建时锁定库存并跳转沙箱模拟支付页）1待发货（直接购买创建，同样锁定库存），不传默认待发货
        int orderStatus = dto.getOrderStatus() == null ? STATUS_WAIT_SHIP : dto.getOrderStatus();
        if (orderStatus != STATUS_PENDING_PAY && orderStatus != STATUS_WAIT_SHIP) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "订单初始状态仅支持：0待付款、1待发货");
        }

        // 1. 校验收货地址归属（不存在 / 非本人所有时抛出业务异常）
        UserAddress address = userAddressService.getOwnAddress(dto.getAddressId(), userId);

        // 2. 组装本次结算的商品条目：立即购买（dto.buyNowNum 非空）不经过购物车，按商品ID + 直购数量直接结算；
        //    购物车结算查询本人购物车并仅保留本次选中商品（购物车以商品ID为条目标识）
        List<Long> selectedIds = dto.getGoodsIds().stream().distinct().toList();
        boolean buyNow = dto.getBuyNowNum() != null;
        List<CartItemVO> cartItems;
        if (buyNow) {
            // 立即购买：仅支持单个商品直购，数量以前端选择的直购数量为准（商品有效性 / 库存 / 限购在下方逐商品循环中统一校验）
            if (selectedIds.size() != 1 || dto.getBuyNowNum() < 1) {
                throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "立即购买仅支持单个商品且数量须大于0");
            }
            CartItemVO directItem = new CartItemVO();
            directItem.setGoodsId(selectedIds.get(0));
            directItem.setGoodsNum(dto.getBuyNowNum());
            cartItems = List.of(directItem);
        } else {
            cartItems = shoppingCartService.listCart(userId).stream()
                    .filter(item -> selectedIds.contains(item.getGoodsId()))
                    .toList();
            if (cartItems.isEmpty()) {
                throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "选中商品已不在购物车，请返回购物车重新选择");
            }
        }

        // 3. 生成结算批次号：本次结算按商品拆分的多张订单共享，支付会话按批次组织（一次支付覆盖整批订单）
        String batchNo = UUIDUtil.orderNo();

        // 4. 逐商品创建独立订单：一个商品一张订单（金额独立计算、独立锁库存、可独立发货 / 退款 / 评价）
        List<String> orderNos = new ArrayList<>();
        for (CartItemVO cartItem : cartItems) {
            // 4.1 校验商品（上架 / 库存 / 限购），金额以服务端价格计算，不信任前端传值
            IntangibleGoods goods = getOnSaleGoods(cartItem.getGoodsId());
            // 非遗人不能下单自己发布的商品：自买自卖会虚增销量、破坏交易与通知的真实性
            if (Objects.equals(goods.getPublisherId(), userId)) {
                throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "不能购买自己发布的商品，请选择其他商品下单");
            }
            int goodsNum = cartItem.getGoodsNum();
            checkStockAndLimit(goods, goodsNum);

            // 4.2 订单编号先行生成（作为 TCC 库存预留凭据，冻结流水与订单由此关联）；
            //     写订单主表：复制收货地址快照（地址后续被修改 / 删除不影响已有订单）
            String orderNo = UUIDUtil.orderNo();
            GoodsOrder order = new GoodsOrder();
            order.setOrderNo(orderNo);
            order.setBatchNo(batchNo);
            order.setUserId(userId);
            order.setAddressId(address.getId());
            order.setReceiverName(address.getReceiverName());
            order.setReceiverPhone(address.getReceiverPhone());
            order.setReceiverAddress(address.getProvince() + address.getCity() + address.getDistrict() + address.getDetailAddress());
            order.setOrderRemark(dto.getOrderRemark());
            order.setTotalPrice(goods.getPrice().multiply(BigDecimal.valueOf(goodsNum)));
            order.setOrderStatus(orderStatus);
            save(order);

            // 4.3 写订单明细（单件商品快照，补订单ID / 编号后入库）
            GoodsOrderItem item = new GoodsOrderItem();
            item.setOrderId(order.getId());
            item.setOrderNo(order.getOrderNo());
            item.setGoodsId(goods.getId());
            item.setPublisherId(goods.getPublisherId());
            item.setGoodsName(goods.getGoodsName());
            item.setGoodsImg(goods.getGoodsImg());
            item.setPrice(goods.getPrice());
            item.setGoodsNum(goodsNum);
            goodsOrderItemMapper.insert(item);

            // 4.4 TCC Try 预扣库存：Redis 原子扣减可用库存并写冻结流水（独立事务，不随本地下单事务回滚）；
            //     库存不足抛异常→全局回滚（TC 调度 Cancel 还原已预扣的其他商品）；
            //     全局事务提交后 TC 自动 Confirm 将 DB 库存落库扣减（含售罄自动下架）
            stockTccAction.tryDeduct(orderNo, goods.getId(), goodsNum);

            // 4.5 待付款订单写入 Redis 支付超时标记（TTL 15分钟：沙箱模拟支付流程使用，支付 / 取消后清除）
            if (orderStatus == STATUS_PENDING_PAY) {
                stringRedisTemplate.opsForValue().set(REDIS_PAY_ORDER_KEY_PREFIX + order.getOrderNo(), "",
                        PAY_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            }

            // 4.6 同步卖家订单到 Redis（单商品订单：快照即本条明细；点击支付订单快照状态即为1待发货，卖家界面随即出现发货入口）
            syncSellerOrders(order, List.of(item), userId);

            // 4.7 直接创建待发货订单（兼容模式）时通知卖家有新订单（0待付款订单在模拟支付成功后通知，见 mockPay）
            if (orderStatus == STATUS_WAIT_SHIP) {
                sendOrderPaidNotice(order, List.of(item));
            }
            orderNos.add(order.getOrderNo());
        }

        // 5. 清空购物车中本次已下单的商品条目（Redis；待付款与待发货均清空，下单即从购物车移出）；
        //    立即购买不经过购物车，不做清理
        if (!buyNow) {
            shoppingCartService.batchDelete(selectedIds, userId);
        }

        log.info("创建订单成功（{}，按商品拆分为 {} 张订单，批次={}）：userId={}, addressId={}, 商品件数={}, 首单号={}",
                orderStatus == STATUS_WAIT_SHIP ? "待发货" : "待付款", orderNos.size(), batchNo,
                userId, address.getId(), cartItems.stream().mapToInt(CartItemVO::getGoodsNum).sum(), orderNos.get(0));
        return orderNos.get(0);
    }



    // ———————————————— 沙箱模拟支付（电脑端扫码 + 手机端确认 + WebSocket 推送） ————————————————

    /**
      发起支付生成二维码，前端PayView.vue渲染，preparePayment提供数据
     */
    @Override
    public PayPrepareVO preparePayment(String orderNo, Long userId) {
        GoodsOrder order = getOwnOrder(orderNo, userId);
        if (order.getOrderStatus() != STATUS_PENDING_PAY) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "仅待付款订单可发起支付");
        }
        // 保障批次号存在（历史单商品订单无批次号时补写，兼容批次支付逻辑）
        String batchNo = ensureBatchNo(order);
        // 本次支付覆盖的订单集合：批次内全部待付款订单（批次支付）；历史单商品订单即订单自身
        List<GoodsOrder> payOrders = listPendingBatchOrders(order);
        // 剩余支付时长（秒）= 批次内最早下单时间 + 15分钟 - 当前时间（与定时任务取消判断口径一致）；已耗尽则等待定时任务取消
        LocalDateTime earliest = payOrders.stream()
                .map(GoodsOrder::getCreateTime)
                .filter(Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .orElse(order.getCreateTime());
        long elapsed = earliest == null
                ? 0 : Duration.between(earliest, LocalDateTime.now()).getSeconds();
        long remaining = PAY_TIMEOUT_MINUTES * 60L - elapsed;
        if (remaining <= 0) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "订单支付时间已过，请返回重新下单");
        }
        // 生成一次性支付令牌：手机端确认页凭令牌免登录查看 / 支付整批订单，避免链接直接暴露订单号
        String payToken = UUID.randomUUID().toString().replace("-", "");
        stringRedisTemplate.opsForValue().set(REDIS_PAY_TOKEN_KEY_PREFIX + payToken, batchNo, remaining, TimeUnit.SECONDS);
        // 批次内所有待付款订单的超时标记刷新为令牌（支付 / 取消 / 超时清理时反查删除令牌键）
        for (GoodsOrder payOrder : payOrders) {
            stringRedisTemplate.opsForValue().set(REDIS_PAY_ORDER_KEY_PREFIX + payOrder.getOrderNo(), payToken, remaining, TimeUnit.SECONDS);
        }

        PayPrepareVO vo = new PayPrepareVO();
        vo.setPayToken(payToken);
        vo.setLanIp(resolveLanIp());
        vo.setExpireSeconds((int) remaining);
        // 本次应付总金额 = 批次内待付款订单金额合计（一次结算多商品拆单后一起支付）
        vo.setTotalPrice(payOrders.stream()
                .map(GoodsOrder::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        return vo;
    }

    /**
     * 支付信息（免登录，手机端扫码确认页）：凭支付令牌查询结算批次——返回批次首张订单号 / 批次应付总额 /
     * 订单笔数 / 批次展示状态（含待付款→0待付款；全部已取消→5已取消；其余已支付→1待发货）；
     * 令牌不存在（已支付 / 已超时 / 已取消）时抛出业务异常提示返回电脑端重新发起
     */
    @Override
    public PayInfoVO getPayInfo(String token) {
        // 根据支付令牌读取结算批次号，令牌失效直接抛异常
        String batchNo = readPayBatchNo(token);
        // 根据批次号，查询该批次下全部关联订单
        List<GoodsOrder> orders = listBatchOrders(batchNo);
        // 取批次第一条订单，用于页面展示订单编号
        GoodsOrder first = orders.get(0);

        /*
         * 应付总额 / 订单笔数口径与电脑端发起支付保持一致
         * 优先筛选批次内状态为待付款的订单；
         * 场景：部分订单被单独取消，剩余仍可支付，只统计可支付的订单
         */
        List<GoodsOrder> payable = orders.stream()
                .filter(o -> o.getOrderStatus() == STATUS_PENDING_PAY)
                .toList();

        // 兜底逻辑：如果没有待付款订单（全部已支付/全部已取消），使用全批次订单，防止页面无数据渲染报错
        List<GoodsOrder> display = payable.isEmpty() ? orders : payable;

        PayInfoVO vo = new PayInfoVO();
        // 设置批次首张订单编号，用于页面展示
        vo.setOrderNo(first.getOrderNo());
        // 累加计算展示集合内订单的总应付金额
        vo.setTotalPrice(display.stream().map(GoodsOrder::getTotalPrice).reduce(BigDecimal.ZERO, BigDecimal::add));
        // 设置需要展示的订单总笔数
        vo.setOrderCount(display.size());
        // 调用工具方法，根据批次全部订单，计算出整个批次综合展示状态
        vo.setOrderStatus(computeBatchDisplayStatus(orders));
        return vo;
    }

    /**
    支付回调
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void mockPay(String token) {
        // 通过支付令牌读取本次对应的支付批次号，一个批次下可包含多笔订单
        String batchNo = readPayBatchNo(token);
        // 根据批次号查询该批次下所有关联的订单集合
        List<GoodsOrder> orders = listBatchOrders(batchNo);

        // 记录本批次成功完成支付的订单数量
        int paidCount = 0;
        // 标记：批次内是否存在已经被支付过的订单，用于区分异常提示文案
        boolean anyAlreadyPaid = false;

        // 循环处理批次内每一笔订单，使用数据库条件更新做状态抢占（乐观锁防重复支付）
        for (GoodsOrder order : orders) {
            /*
             * 条件更新抢占：仅当订单状态 = 0待付款时，才更新为1待发货
             * 返回值claimed = false：代表条件不匹配，该订单已经被支付 / 已经被超时取消，不做处理，防止重复执行业务逻辑
             */
            boolean claimed = lambdaUpdate()
                    .eq(GoodsOrder::getId, order.getId())
                    .eq(GoodsOrder::getOrderStatus, STATUS_PENDING_PAY)
                    .set(GoodsOrder::getOrderStatus, STATUS_WAIT_SHIP)
                    .update();

            // 抢占失败，该订单不能执行支付逻辑
            if (!claimed) {
                // 查询订单最新状态
                GoodsOrder latest = getById(order.getId());
                // 如果订单不是已取消，说明是已经被支付，标记该状态
                if (latest != null && latest.getOrderStatus() != STATUS_CANCELLED) {
                    anyAlreadyPaid = true;
                }
                continue;
            }

            // ----------------------抢占成功，订单状态由待付款变更为待发货，执行后续业务----------------------
            String orderNo = order.getOrderNo();
            // 查询当前订单的商品明细
            List<GoodsOrderItem> items = listOrderItems(order.getId());
            // 同步更新卖家端订单快照，更新状态为待发货，卖家后台出现发货操作入口
            syncSellerOrderStatus(orderNo, items, STATUS_WAIT_SHIP);
            // 清除Redis中该订单相关支付令牌、超时标记等支付会话缓存
            cleanupPayKeys(orderNo);
            // WebSocket推送支付成功消息，通知电脑端浏览器页面，弹窗提示并引导跳转我的订单页面
            payWebSocketHandler.pushPaid(orderNo);
            // MQ 投递通知消息，给卖家推送【买家已支付，等待发货】消息（mic-notice 消费落库）；发送失败不打断主支付流程
            sendOrderPaidNotice(order, items);
            // 成功支付计数+1
            paidCount++;
        }

        // 本批次没有任何订单支付成功，抛出业务异常，区分两种提示
        if (paidCount == 0) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR,
                    anyAlreadyPaid ? "该订单已支付，无需重复操作" : "订单已取消或超时，无法支付");
        }

        log.info("沙箱模拟支付成功（手机端扫码确认）：batchNo={}, 批次订单数={}, 本次支付订单数={}", batchNo, orders.size(), paidCount);
    }





    /**
     * 我的订单分页（登录用户）：按创建时间倒序，可按订单状态筛选，支持订单号 / 商品名称模糊搜索
     */
    @Override
    public PageResult<OrderItemVO> pageMyOrders(PageDto pageDto, Integer orderStatus, String keyword, Long userId) {
        Page<GoodsOrder> page = new Page<>(pageDto.getCurrent(), pageDto.getSize());
        String kw = keyword == null ? "" : keyword.trim();
        // 逻辑删除自动过滤（is_deleted=0）；orderStatus 为空查全部；
        // keyword 非空时模糊匹配订单号，或商品明细中存在同名商品（EXISTS 子查询，{0} 参数化占位防注入）
        lambdaQuery()
                .eq(GoodsOrder::getUserId, userId)
                .eq(orderStatus != null, GoodsOrder::getOrderStatus, orderStatus)
                .and(!kw.isEmpty(), wrapper -> wrapper
                        .like(GoodsOrder::getOrderNo, kw)
                        .or()
                        .apply("EXISTS (SELECT 1 FROM goods_order_item i WHERE i.order_id = goods_order.id "
                                + "AND i.is_deleted = 0 AND i.goods_name LIKE CONCAT('%', {0}, '%'))", kw))
                .orderByDesc(GoodsOrder::getCreateTime)
                .orderByDesc(GoodsOrder::getId)
                .page(page);
        return PageResult.of(page, toOrderItemVOs(page.getRecords()));
    }

    /**
     * 卖家订单分页（非遗人）：来自 Redis 卖家订单快照，按下单时间倒序（Redis 数据量小，采用内存分页），
     * 支持订单号 / 商品名称模糊搜索
     */
    @Override
    public PageResult<SellerOrderVO> pageSellerOrders(PageDto pageDto, Integer orderStatus, String keyword, Long sellerId) {
        String kw = keyword == null ? "" : keyword.trim().toLowerCase();
        List<SellerOrderVO> orders = readSellerOrders(sellerId).stream()
                .filter(vo -> orderStatus == null || orderStatus.equals(vo.getOrderStatus()))
                .filter(vo -> kw.isEmpty() || matchesSellerKeyword(vo, kw))
                .sorted(Comparator.comparing(SellerOrderVO::getCreateTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        int current = pageDto.getCurrent() == null || pageDto.getCurrent() < 1 ? 1 : pageDto.getCurrent();
        int size = pageDto.getSize() == null || pageDto.getSize() < 1 ? 10 : pageDto.getSize();
        int from = Math.min((current - 1) * size, orders.size());
        int to = Math.min(from + size, orders.size());

        Page<SellerOrderVO> page = new Page<>(current, size);
        page.setRecords(orders.subList(from, to));
        page.setTotal(orders.size());
        return PageResult.of(page);
    }

    /**
     * 卖家订单关键词匹配（忽略大小写）：订单号或任一商品名称包含关键词即命中
     * @param lowerKw 已转小写的关键词（调用方保证非空）
     */
    private boolean matchesSellerKeyword(SellerOrderVO vo, String lowerKw) {
        if (vo.getOrderNo() != null && vo.getOrderNo().toLowerCase().contains(lowerKw)) {
            return true;
        }
        return vo.getItems() != null && vo.getItems().stream()
                .anyMatch(item -> item.getGoodsName() != null
                        && item.getGoodsName().toLowerCase().contains(lowerKw));
    }

    /**
     * 后台订单分页（管理员）：全部用户订单，按创建时间倒序，可按订单状态筛选，
     * 支持订单号 / 下单人账号模糊搜索（自定义 SQL LEFT JOIN sys_user 回填下单人账号）
     */
    @Override
    public PageResult<OrderItemVO> pageAdminOrders(PageDto pageDto, Integer orderStatus, String keyword) {
        Page<GoodsOrder> page = new Page<>(pageDto.getCurrent(), pageDto.getSize());
        String kw = keyword == null ? "" : keyword.trim();
        // 自定义 SQL 手动过滤 is_deleted=0；orderStatus 为空查全部；keyword 非空时模糊匹配订单编号或下单人账号
        baseMapper.selectAdminOrderPage(page, orderStatus, kw);
        return PageResult.of(page, toAdminOrderItemVOs(page.getRecords()));
    }

    /** 批量组装管理端订单VO：在通用订单VO基础上回填下单人账号（自定义 SQL LEFT JOIN sys_user 查询结果） */
    private List<OrderItemVO> toAdminOrderItemVOs(List<GoodsOrder> orders) {
        List<OrderItemVO> vos = toOrderItemVOs(orders);
        for (int i = 0; i < orders.size(); i++) {
            vos.get(i).setUserAccount(orders.get(i).getUserAccount());
        }
        return vos;
    }

    /**
     * 订单详情（登录用户）：仅可查看本人订单
     */
    @Override
    public OrderItemVO getOrderDetail(String orderNo, Long userId) {
        GoodsOrder order = getOwnOrder(orderNo, userId);
        return toOrderItemVO(order, listOrderItems(order.getId()));
    }


    /** 读取支付令牌对应的结算批次号（手机端免登录接口凭据，一次支付覆盖批次内全部待付款订单）；令牌缺失 / 已失效时抛出业务异常 */
    private String readPayBatchNo(String token) {
        String batchNo = token == null ? null
                : stringRedisTemplate.opsForValue().get(REDIS_PAY_TOKEN_KEY_PREFIX + token);
        if (batchNo == null) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "支付链接已失效，请返回电脑端重新发起支付");
        }
        return batchNo;
    }

    /** 确保订单拥有结算批次号：改造前创建的历史订单批次号为空时以订单号补写并落库，返回有效批次号 */
    private String ensureBatchNo(GoodsOrder order) {
        if (order.getBatchNo() != null && !order.getBatchNo().isEmpty()) {
            return order.getBatchNo();
        }
        String batchNo = order.getOrderNo();
        GoodsOrder update = new GoodsOrder();
        update.setId(order.getId());
        update.setBatchNo(batchNo);
        updateById(update);
        order.setBatchNo(batchNo);
        return batchNo;
    }

    /** 查询结算批次内全部待付款订单（本次支付对象）：批次号为空（历史订单）时仅返回本单 */
    private List<GoodsOrder> listPendingBatchOrders(GoodsOrder order) {
        String batchNo = order.getBatchNo();
        if (batchNo == null || batchNo.isEmpty()) {
            return List.of(order);
        }
        return lambdaQuery()
                .eq(GoodsOrder::getBatchNo, batchNo)
                .eq(GoodsOrder::getOrderStatus, STATUS_PENDING_PAY)
                .orderByAsc(GoodsOrder::getId)
                .list();
    }

    /** 查询结算批次内全部订单（含已支付 / 已取消，手机端确认页展示与模拟支付使用）；批次号无匹配时按订单号兜底（兼容改造前未写批次号的历史订单） */
    private List<GoodsOrder> listBatchOrders(String batchNo) {
        List<GoodsOrder> orders = lambdaQuery()
                .eq(GoodsOrder::getBatchNo, batchNo)
                .orderByAsc(GoodsOrder::getId)
                .list();
        return orders.isEmpty() ? List.of(getOrderByNo(batchNo)) : orders;
    }

    /** 计算批次展示状态（手机端确认页）：批次内含待付款订单 → 0待付款；全部已取消 → 5已取消；其余（已支付等）→ 1待发货 */
    private int computeBatchDisplayStatus(List<GoodsOrder> orders) {
        if (orders.stream().anyMatch(o -> o.getOrderStatus() == STATUS_PENDING_PAY)) {
            return STATUS_PENDING_PAY;
        }
        return orders.stream().allMatch(o -> o.getOrderStatus() == STATUS_CANCELLED)
                ? STATUS_CANCELLED : STATUS_WAIT_SHIP;
    }

    /** 清除订单支付会话（Redis）：按超时标记中记录的令牌删除令牌键后删除标记键（支付成功 / 取消 / 超时 / 删除时调用） */
    private void cleanupPayKeys(String orderNo) {
        try {
            String orderKey = REDIS_PAY_ORDER_KEY_PREFIX + orderNo;
            String payToken = stringRedisTemplate.opsForValue().get(orderKey);
            if (payToken != null && !payToken.isEmpty()) {
                stringRedisTemplate.delete(REDIS_PAY_TOKEN_KEY_PREFIX + payToken);
            }
            stringRedisTemplate.delete(orderKey);
        } catch (Exception e) {
            log.warn("支付会话清理失败（不影响主流程）：orderNo={}, err={}", orderNo, e.getMessage());
        }
    }

    /**
     * 解析服务端局域网 IPv4 地址（二维码需手机可访问，不能使用 localhost）：
     * 优先「默认路由出口法」（返回本机访问外网所选用的网卡地址，天然避开 VirtualBox / VMware / WSL 等虚拟网卡），
     * 探测失败时回退网卡枚举并跳过常见虚拟网卡名称，最后兜底本机地址
     */
    private String resolveLanIp() {
        // 1. 默认路由出口法：connect 仅查路由表不发送数据（离线也可用），自动选中通往外部网络的物理网卡（通常为 WLAN）
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("114.114.114.114"), 100);
            InetAddress local = socket.getLocalAddress();
            if (local instanceof Inet4Address && !local.isLoopbackAddress()) {
                return local.getHostAddress();
            }
        } catch (Exception e) {
            log.warn("默认路由探测局域网IP失败（改用网卡枚举）：err={}", e.getMessage());
        }
        // 2. 网卡枚举兜底：跳过环回 / 未启用 / 虚拟接口及常见虚拟网卡（VirtualBox Host-Only 的 192.168.56.x 等仅供宿主机访问，手机无法到达）
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback() || networkInterface.isVirtual()
                        || isVirtualNic(networkInterface)) {
                    continue;
                }
                for (InterfaceAddress address : networkInterface.getInterfaceAddresses()) {
                    InetAddress ip = address.getAddress();
                    if (ip instanceof Inet4Address && ip.isSiteLocalAddress()) {
                        return ip.getHostAddress();
                    }
                }
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            log.warn("局域网IP解析失败（二维码需手动替换为电脑局域网IP）：err={}", e.getMessage());
            return "127.0.0.1";
        }
    }

    /** 常见虚拟网卡名称判断（按接口名 + 显示名匹配：VirtualBox / VMware / WSL / Hyper-V / Docker 等，手机均无法访问） */
    private boolean isVirtualNic(NetworkInterface networkInterface) {
        String name = (networkInterface.getName() + " " + networkInterface.getDisplayName()).toLowerCase();
        return name.contains("vmware") || name.contains("virtualbox") || name.contains("vbox")
                || name.contains("vethernet") || name.contains("hyper-v") || name.contains("docker")
                || name.contains("wsl") || name.contains("tap") || name.contains("tun")
                || name.contains("loopback") || name.contains("bluetooth");
    }

    /**
     * 申请退款（登录用户）：未收货前的订单（1待发货 / 2已发货）可申请，须填写退款原因，
     * 订单状态置 8退款申请中并记录申请前状态（驳回时恢复），等待非遗人审核；非遗人同意退款时才恢复库存
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refundOrder(String orderNo, String refundReason, Long userId) {
        GoodsOrder order = getOwnOrder(orderNo, userId);
        int status = order.getOrderStatus();
        // 仅待发货 / 已发货可申请退款；3已收货及之后不可申请，8退款申请中不可重复提交
        if (status != STATUS_WAIT_SHIP && status != STATUS_SHIPPED) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "仅待发货或已发货的订单可申请退款");
        }
        GoodsOrder update = new GoodsOrder();
        update.setId(order.getId());
        update.setOrderStatus(STATUS_REFUND_APPLY);
        update.setRefundReason(refundReason);
        update.setRefundApplyTime(LocalDateTime.now());
        update.setRefundPrevStatus(status);
        updateById(update);
        // 同步卖家订单快照：状态 8 + 退款申请原因（非遗人端「我卖出的」随即出现同意 / 拒绝入口）
        List<GoodsOrderItem> items = listOrderItems(order.getId());
        syncSellerOrderStatus(order.getOrderNo(), items, STATUS_REFUND_APPLY, refundReason);
        // 通知卖家有新的退款申请（MQ 发送失败不影响退款主流程）
        sendRefundApplyNotice(order, items, refundReason);
        log.info("买家申请退款成功（待非遗人审核）：orderNo={}, userId={}, 申请前状态={}, 申请原因={}",
                orderNo, userId, status, refundReason);
    }

    /**
     * 同意退款（非遗人）：本订单卖家之一同意后 8退款申请中 → 4已退款（终态），逐条恢复商品库存；
     * 订单含多卖家商品时任一卖家处理即整单流转（与发货规则一致）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void agreeRefund(String orderNo, Long sellerId) {
        GoodsOrder order = getOrderByNo(orderNo);
        List<GoodsOrderItem> items = listOrderItems(order.getId());
        checkSeller(items, sellerId, "同意退款");
        if (order.getOrderStatus() != STATUS_REFUND_APPLY) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "仅退款申请中的订单可同意退款");
        }
        updateStatus(order.getId(), STATUS_REFUNDED);
        // 退款成功：逐条恢复商品库存（点击支付创建 / 付款时已扣减）
        for (GoodsOrderItem item : items) {
            intangibleGoodsService.restoreStock(item.getGoodsId(), item.getGoodsNum());
        }
        syncSellerOrderStatus(orderNo, items, STATUS_REFUNDED);
        // 通知买家退款申请已通过（MQ 发送失败不影响退款主流程）
        sendRefundResultNotice(order, sellerId, true, null);
        log.info("非遗人同意退款（已退款）：orderNo={}, sellerId={}, 商品件数={}", orderNo, sellerId, items.size());
    }

    /**
     * 驳回退款申请（非遗人）：须填写驳回原因，订单恢复到申请前状态（1待发货 / 2已发货），买家可重新申请；
     * 订单含多卖家商品时任一卖家处理即整单流转（与发货规则一致）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectRefund(String orderNo, String rejectReason, Long sellerId) {
        GoodsOrder order = getOrderByNo(orderNo);
        List<GoodsOrderItem> items = listOrderItems(order.getId());
        checkSeller(items, sellerId, "拒绝退款");
        if (order.getOrderStatus() != STATUS_REFUND_APPLY) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "仅退款申请中的订单可驳回退款");
        }
        // 驳回：恢复申请前的订单状态（历史数据兜底为 1待发货），买家端展示驳回原因并可重新申请
        int prevStatus = order.getRefundPrevStatus() == null ? STATUS_WAIT_SHIP : order.getRefundPrevStatus();
        GoodsOrder update = new GoodsOrder();
        update.setId(order.getId());
        update.setOrderStatus(prevStatus);
        update.setRefundRejectReason(rejectReason);
        updateById(update);
        syncSellerOrderStatus(orderNo, items, prevStatus);
        // 通知买家退款申请被驳回（附驳回原因；MQ 发送失败不影响退款主流程）
        sendRefundResultNotice(order, sellerId, false, rejectReason);
        log.info("非遗人驳回退款申请：orderNo={}, sellerId={}, 恢复状态={}, 驳回原因={}",
                orderNo, sellerId, prevStatus, rejectReason);
    }

    /**
     * 更新订单状态（登录用户）：按目标状态分派校验与流转
     * 1付款（买家）：0待付款 → 1待发货（库存已在下单时锁定，付款仅流转状态并清除支付会话）
     * 2发货（卖家）：1待发货 → 2已发货（校验当前用户为本订单卖家之一）
     * 3收货（买家）：2已发货 → 3已收货
     * 5取消（买家）：0待付款 → 5已取消（逐条归还下单时锁定的库存并清除支付会话）
     * 说明：4已退款走同意退款接口、8退款申请中走申请退款接口；6已评价 / 7已完成由评价接口自动流转，均不可经本接口手动更新
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateOrderStatus(String orderNo, Integer targetStatus, Long userId) {
        // 兜底校验目标状态（防止内部调用绕开 DTO 注解约束），仅支持：1付款 2发货 3收货 5取消
        if (targetStatus == null || (targetStatus != STATUS_WAIT_SHIP && targetStatus != STATUS_SHIPPED
                && targetStatus != STATUS_RECEIVED && targetStatus != STATUS_CANCELLED)) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR,
                    "订单状态仅支持更新为：1付款、2发货、3收货、5取消");
        }
        GoodsOrder order = getOrderByNo(orderNo);
        List<GoodsOrderItem> items = listOrderItems(order.getId());

        if (targetStatus == STATUS_WAIT_SHIP) {
            // 付款（买家）：仅待付款订单可付款（点击取消创建的订单在此完成付款，付款后即进入待发货）
            checkBuyer(order, userId);
            if (order.getOrderStatus() != STATUS_PENDING_PAY) {
                throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "仅待付款订单可付款");
            }
            // 库存已在下单时锁定，付款仅流转状态（0待付款 → 1待发货），并清除 Redis 支付会话
            updateStatus(order.getId(), STATUS_WAIT_SHIP);
            syncSellerOrderStatus(orderNo, items, STATUS_WAIT_SHIP);
            cleanupPayKeys(orderNo);
            // 通知卖家有新订单（买家已付款，等待发货；MQ 发送失败不影响付款主流程）
            sendOrderPaidNotice(order, items);
            log.info("订单付款成功（已进入待发货）：orderNo={}, userId={}, 商品件数={}", orderNo, userId, items.size());
        } else if (targetStatus == STATUS_SHIPPED) {
            // 发货（卖家）：校验当前用户为本订单卖家之一（订单含多卖家商品时，任一卖家发货即整单流转）
            checkSeller(items, userId, "发货");
            if (order.getOrderStatus() != STATUS_WAIT_SHIP) {
                throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "仅待发货订单可发货");
            }
            updateStatus(order.getId(), STATUS_SHIPPED);
            syncSellerOrderStatus(orderNo, items, STATUS_SHIPPED);
            // 通知买家已发货（MQ 发送失败不影响发货主流程）
            sendOrderShippedNotice(order, items, userId);
            log.info("订单发货成功：orderNo={}, sellerId={}, 商品件数={}", orderNo, userId, items.size());
        } else if (targetStatus == STATUS_RECEIVED) {
            // 确认收货（买家）：仅已发货订单可确认收货
            checkBuyer(order, userId);
            if (order.getOrderStatus() != STATUS_SHIPPED) {
                throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "仅已发货订单可确认收货");
            }
            updateStatus(order.getId(), STATUS_RECEIVED);
            syncSellerOrderStatus(orderNo, items, STATUS_RECEIVED);
            log.info("订单确认收货成功：orderNo={}, userId={}", orderNo, userId);
        } else {
            // 取消订单（买家）：仅待付款订单可取消；逐条归还下单时锁定的库存并清除支付会话
            checkBuyer(order, userId);
            if (order.getOrderStatus() != STATUS_PENDING_PAY) {
                throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "仅待付款订单可取消");
            }
            updateStatus(order.getId(), STATUS_CANCELLED);
            for (GoodsOrderItem item : items) {
                intangibleGoodsService.restoreStock(item.getGoodsId(), item.getGoodsNum());
            }
            syncSellerOrderStatus(orderNo, items, STATUS_CANCELLED);
            cleanupPayKeys(orderNo);
            log.info("订单取消成功（库存已归还）：orderNo={}, userId={}", orderNo, userId);
        }
    }

    /**
     * 删除订单（登录用户）：逻辑删除；进行中订单（1待发货 / 2已发货 / 3已收货 / 8退款申请中）需先完成交易 / 处理退款申请 / 取消；
     * 删除 0待付款 订单时逐条归还下单时锁定的库存并清除支付会话
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteOrder(String orderNo, Long userId) {
        GoodsOrder order = getOwnOrder(orderNo, userId);
        int status = order.getOrderStatus();
        // 进行中订单不可直接删除（终态 4已退款 / 5已取消 / 7已完成 以及 0待付款 可直接删除）
        if (status == STATUS_WAIT_SHIP || status == STATUS_SHIPPED || status == STATUS_RECEIVED
                || status == STATUS_REFUND_APPLY) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "订单进行中，请先完成交易 / 处理退款申请 / 取消订单后再删除");
        }
        // 待付款订单下单时已锁定库存：删除时逐条归还并清除支付会话（订单随后逻辑删除）
        if (status == STATUS_PENDING_PAY) {
            for (GoodsOrderItem item : listOrderItems(order.getId())) {
                intangibleGoodsService.restoreStock(item.getGoodsId(), item.getGoodsNum());
            }
            cleanupPayKeys(orderNo);
        }
        // 逻辑删除（@TableLogic）：唯一键仅为订单号，不存在重复删除冲突
        removeById(order.getId());
        log.info("删除订单成功（逻辑删除）：orderNo={}, userId={}", orderNo, userId);
    }

    /**
     * 待付款订单超时自动取消（定时任务调用）：0待付款且创建超过15分钟未支付的订单 → 5已取消，
     * 逐条归还下单时锁定的库存、清除 Redis 支付会话（超时标记 / 令牌），
     * 并通过 WebSocket 向电脑端模拟支付页推送超时通知（TIMEOUT）
     * @return 本次取消的订单数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int cancelTimeoutOrders() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(PAY_TIMEOUT_MINUTES);
        List<GoodsOrder> orders = lambdaQuery()
                .eq(GoodsOrder::getOrderStatus, STATUS_PENDING_PAY)
                .lt(GoodsOrder::getCreateTime, deadline)
                .list();
        for (GoodsOrder order : orders) {
            List<GoodsOrderItem> items = listOrderItems(order.getId());
            updateStatus(order.getId(), STATUS_CANCELLED);
            // 归还下单时锁定的库存（沙箱模拟支付超时规则：取消订单并归还库存）
            for (GoodsOrderItem item : items) {
                intangibleGoodsService.restoreStock(item.getGoodsId(), item.getGoodsNum());
            }
            syncSellerOrderStatus(order.getOrderNo(), items, STATUS_CANCELLED);
            // 清除支付会话并推送超时通知（电脑端模拟支付页弹窗）
            cleanupPayKeys(order.getOrderNo());
            payWebSocketHandler.pushTimeout(order.getOrderNo());
        }
        if (!orders.isEmpty()) {
            log.info("待付款超时自动取消：共 {} 笔订单（超时阈值 {} 分钟，库存已归还）", orders.size(), PAY_TIMEOUT_MINUTES);
        }
        return orders.size();
    }

    /**
     * 评价完成后自动流转订单状态（评价服务调用）：3已收货 → 6已评价 →（自动）→ 7已完成，并同步卖家订单快照
     * @param orderId 订单ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void autoFinishAfterEvaluate(Long orderId) {
        GoodsOrder order = getById(orderId);
        if (order == null) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "订单不存在");
        }
        List<GoodsOrderItem> items = listOrderItems(orderId);
        // 3已收货 → 6已评价（记录评价动作的过渡状态）→（自动）→ 7已完成
        updateStatus(orderId, STATUS_EVALUATED);
        updateStatus(orderId, STATUS_FINISHED);
        syncSellerOrderStatus(order.getOrderNo(), items, STATUS_FINISHED);
        log.info("评价后订单自动完成：orderNo={}, 状态流转 6已评价→7已完成", order.getOrderNo());
    }

    /**
     * 校验订单明细是否包含指定商品（评价校验：多商品订单中仅包含的商品可被评价）
     */
    @Override
    public boolean orderContainsGoods(Long orderId, Long goodsId) {
        return goodsOrderItemMapper.selectCount(new LambdaQueryWrapper<GoodsOrderItem>()
                .eq(GoodsOrderItem::getOrderId, orderId)
                .eq(GoodsOrderItem::getGoodsId, goodsId)) > 0;
    }

    // ———————————————— Redis 卖家订单读写（异常仅告警，不影响数据库主流程） ————————————————

    /** 同步卖家订单快照到 Redis：按卖家分组，各存自己商品的订单子集（Hash：field=订单号） */
    private void syncSellerOrders(GoodsOrder order, List<GoodsOrderItem> items, Long buyerId) {
        try {
            Map<Long, List<GoodsOrderItem>> sellerItemsMap = items.stream()
                    .collect(Collectors.groupingBy(GoodsOrderItem::getPublisherId));
            sellerItemsMap.forEach((sellerId, sellerItems) -> {
                SellerOrderVO vo = new SellerOrderVO();
                vo.setOrderNo(order.getOrderNo());
                vo.setBuyerId(buyerId);
                vo.setOrderStatus(order.getOrderStatus());
                vo.setTotalPrice(sellerItems.stream()
                        .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getGoodsNum())))
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
                vo.setTotalNum(sellerItems.stream().mapToInt(GoodsOrderItem::getGoodsNum).sum());
                vo.setCreateTime(DateUtil.format(order.getCreateTime()));
                vo.setReceiverName(order.getReceiverName());
                vo.setReceiverPhone(order.getReceiverPhone());
                vo.setReceiverAddress(order.getReceiverAddress());
                vo.setOrderRemark(order.getOrderRemark());
                vo.setItems(sellerItems.stream().map(this::toOrderGoodsItemVO).toList());

                stringRedisTemplate.opsForHash().put(REDIS_SELLER_ORDER_KEY_PREFIX + sellerId,
                        order.getOrderNo(), JSONUtil.toJsonStr(vo));
            });
        } catch (Exception e) {
            // Redis 仅作订单展示同步，写入失败不影响下单主流程（订单数据以 MySQL 为准）
            log.warn("卖家订单同步写入 Redis 失败（不影响下单主流程）：orderNo={}, err={}",
                    order.getOrderNo(), e.getMessage());
        }
    }

    /** 同步订单状态到卖家订单 Redis 快照（状态变更时调用，失败仅告警不影响主流程） */
    private void syncSellerOrderStatus(String orderNo, List<GoodsOrderItem> items, int status) {
        syncSellerOrderStatus(orderNo, items, status, null);
    }

    /**
     * 同步订单状态到卖家订单 Redis 快照（可附带退款申请信息）：
     * 买家申请退款时写入状态 8 与退款申请原因 / 时间，供非遗人端展示申请详情并处理（同意 / 拒绝）
     */
    private void syncSellerOrderStatus(String orderNo, List<GoodsOrderItem> items, int status, String refundReason) {
        try {
            Set<Long> sellerIds = items.stream().map(GoodsOrderItem::getPublisherId).collect(Collectors.toSet());
            for (Long sellerId : sellerIds) {
                String key = REDIS_SELLER_ORDER_KEY_PREFIX + sellerId;
                Object raw = stringRedisTemplate.opsForHash().get(key, orderNo);
                if (raw == null) {
                    continue;
                }
                JSONObject json = JSONUtil.parseObj(raw.toString());
                json.set("orderStatus", status);
                if (refundReason != null) {
                    json.set("refundReason", refundReason);
                    json.set("refundApplyTime", DateUtil.now());
                }
                stringRedisTemplate.opsForHash().put(key, orderNo, json.toString());
            }
        } catch (Exception e) {
            log.warn("卖家订单状态同步失败（不影响主流程）：orderNo={}, err={}", orderNo, e.getMessage());
        }
    }

    /** 读取卖家订单快照（Redis Hash 全量）：解析失败 / 无数据时返回空列表 */
    private List<SellerOrderVO> readSellerOrders(Long sellerId) {
        try {
            Map<Object, Object> entries = stringRedisTemplate.opsForHash()
                    .entries(REDIS_SELLER_ORDER_KEY_PREFIX + sellerId);
            return entries.values().stream()
                    .map(value -> parseSellerOrder(value.toString()))
                    .filter(vo -> vo != null)
                    .toList();
        } catch (Exception e) {
            log.warn("卖家订单读取失败（Redis）：sellerId={}, err={}", sellerId, e.getMessage());
            return List.of();
        }
    }

    /** 解析卖家订单 JSON 快照（手动逐字段映射，规避嵌套泛型反序列化问题） */
    private SellerOrderVO parseSellerOrder(String json) {
        try {
            JSONObject obj = JSONUtil.parseObj(json);
            SellerOrderVO vo = new SellerOrderVO();
            vo.setOrderNo(obj.getStr("orderNo"));
            vo.setBuyerId(obj.getLong("buyerId"));
            vo.setOrderStatus(obj.getInt("orderStatus"));
            vo.setRefundReason(obj.getStr("refundReason"));
            vo.setRefundApplyTime(obj.getStr("refundApplyTime"));
            vo.setTotalPrice(obj.getBigDecimal("totalPrice"));
            vo.setTotalNum(obj.getInt("totalNum"));
            vo.setCreateTime(obj.getStr("createTime"));
            vo.setReceiverName(obj.getStr("receiverName"));
            vo.setReceiverPhone(obj.getStr("receiverPhone"));
            vo.setReceiverAddress(obj.getStr("receiverAddress"));
            vo.setOrderRemark(obj.getStr("orderRemark"));
            if (obj.getJSONArray("items") != null) {
                vo.setItems(JSONUtil.toList(obj.getJSONArray("items"), OrderGoodsItemVO.class));
            }
            return vo;
        } catch (Exception e) {
            log.warn("卖家订单快照解析失败（跳过该条）：err={}", e.getMessage());
            return null;
        }
    }

    // ———————————————— 订单组装与校验 ————————————————

    /** 查询本人订单：不存在或非本人所有时抛出业务异常 */
    private GoodsOrder getOwnOrder(String orderNo, Long userId) {
        GoodsOrder order = getOrderByNo(orderNo);
        checkBuyer(order, userId);
        return order;
    }

    /** 查询订单（按编号）：不存在时抛出业务异常（卖家发货等操作不校验买家归属） */
    private GoodsOrder getOrderByNo(String orderNo) {
        GoodsOrder order = lambdaQuery()
                .eq(GoodsOrder::getOrderNo, orderNo)
                .one();
        if (order == null) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "订单不存在");
        }
        return order;
    }

    /** 校验订单买家归属：非本人订单时抛出业务异常 */
    private void checkBuyer(GoodsOrder order, Long userId) {
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN, "仅可操作本人订单");
        }
    }

    /** 校验当前用户为本订单卖家之一（发货 / 退款处理共用；多卖家订单任一卖家操作即整单流转） */
    private void checkSeller(List<GoodsOrderItem> items, Long userId, String action) {
        boolean isSeller = items.stream().anyMatch(item -> userId.equals(item.getPublisherId()));
        if (!isSeller) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN, "仅订单卖家可" + action);
        }
    }

    /** 仅更新订单状态字段（状态流转中间步骤共用） */
    private void updateStatus(Long orderId, int orderStatus) {
        GoodsOrder update = new GoodsOrder();
        update.setId(orderId);
        update.setOrderStatus(orderStatus);
        updateById(update);
    }

    /** 查询订单明细（下单时快照，按明细ID升序保持下单时商品顺序） */
    private List<GoodsOrderItem> listOrderItems(Long orderId) {
        return goodsOrderItemMapper.selectList(new LambdaQueryWrapper<GoodsOrderItem>()
                .eq(GoodsOrderItem::getOrderId, orderId)
                .orderByAsc(GoodsOrderItem::getId));
    }

    /** 查询已上架商品：商品不存在 / 已被删除 / 未上架时抛出业务异常 */
    private IntangibleGoods getOnSaleGoods(Long goodsId) {
        IntangibleGoods goods = intangibleGoodsService.getById(goodsId);
        if (goods == null || goods.getStatus() != GOODS_STATUS_APPROVED) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "商品不存在或已下架");
        }
        return goods;
    }

    /** 校验购买数量：不超过当前库存与限购数量（下单创建时快照校验，扣减时原子操作再次兜底） */
    private void checkStockAndLimit(IntangibleGoods goods, int goodsNum) {
        if (goodsNum > goods.getStock()) {
            throw new BusinessException(ResultCodeEnum.STOCK_NOT_ENOUGH, "商品库存不足，当前仅剩" + goods.getStock() + "件");
        }
        if (goods.getLimitBuyNum() != null && goodsNum > goods.getLimitBuyNum()) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "该商品限购" + goods.getLimitBuyNum() + "件");
        }
    }

    /** 批量组装订单VO：一次查询全部订单明细（快照数据，不依赖商品表） */
    private List<OrderItemVO> toOrderItemVOs(List<GoodsOrder> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }
        List<Long> orderIds = orders.stream().map(GoodsOrder::getId).toList();
        Map<Long, List<GoodsOrderItem>> itemMap = goodsOrderItemMapper.selectList(new LambdaQueryWrapper<GoodsOrderItem>()
                        .in(GoodsOrderItem::getOrderId, orderIds))
                .stream().collect(Collectors.groupingBy(GoodsOrderItem::getOrderId));
        return orders.stream()
                .map(order -> toOrderItemVO(order, itemMap.getOrDefault(order.getId(), List.of())))
                .toList();
    }

    /** 组装单个订单VO（含收货信息快照与商品明细） */
    private OrderItemVO toOrderItemVO(GoodsOrder order, List<GoodsOrderItem> items) {
        OrderItemVO vo = new OrderItemVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setTotalPrice(order.getTotalPrice());
        vo.setTotalNum(items.stream().mapToInt(GoodsOrderItem::getGoodsNum).sum());
        vo.setOrderStatus(order.getOrderStatus());
        vo.setRefundReason(order.getRefundReason());
        vo.setRefundApplyTime(order.getRefundApplyTime());
        vo.setRefundRejectReason(order.getRefundRejectReason());
        vo.setCreateTime(order.getCreateTime());
        vo.setReceiverName(order.getReceiverName());
        vo.setReceiverPhone(order.getReceiverPhone());
        vo.setReceiverAddress(order.getReceiverAddress());
        vo.setOrderRemark(order.getOrderRemark());
        vo.setItems(items.stream().map(this::toOrderGoodsItemVO).toList());
        return vo;
    }

    /** 组装订单商品明细VO（下单时快照） */
    private OrderGoodsItemVO toOrderGoodsItemVO(GoodsOrderItem item) {
        OrderGoodsItemVO vo = new OrderGoodsItemVO();
        vo.setGoodsId(item.getGoodsId());
        vo.setGoodsName(item.getGoodsName());
        vo.setGoodsImg(item.getGoodsImg());
        vo.setPrice(item.getPrice());
        vo.setGoodsNum(item.getGoodsNum());
        return vo;
    }

    // ———————————————— 消息通知（RabbitMQ 异步投递 mic-notice 消费落库，发送失败仅告警不影响订单主流程） ————————————————

    /**
     * 发送新订单通知（卖家收）：买家支付成功（mockPay / 状态流转付款）或兼容模式直接创建待发货订单时调用
     */
    private void sendOrderPaidNotice(GoodsOrder order, List<GoodsOrderItem> items) {
        if (items.isEmpty()) {
            return;
        }
        GoodsOrderItem first = items.get(0);
        String buyerNickname = userNickname(order.getUserId());
        String goodsDesc = items.size() == 1
                ? "《" + first.getGoodsName() + "》" + first.getGoodsNum() + " 件"
                : "《" + first.getGoodsName() + "》等 " + items.size() + " 件商品";
        sendNotice(first.getPublisherId(), order.getUserId(), NOTIFY_TYPE_ORDER, "你有一笔新订单",
                "买家 " + buyerNickname + " 拍下了" + goodsDesc + "，请及时处理。", order.getOrderNo());
    }

    /** 发送已发货通知（买家收）：卖家标记发货时调用 */
    private void sendOrderShippedNotice(GoodsOrder order, List<GoodsOrderItem> items, Long sellerId) {
        String goodsName = items.isEmpty() ? "商品" : items.get(0).getGoodsName();
        sendNotice(order.getUserId(), sellerId, NOTIFY_TYPE_ORDER,
                "你购买的《" + goodsName + "》已发货",
                "订单 " + order.getOrderNo() + " 卖家已发货，请注意查收。", order.getOrderNo());
    }

    /** 发送退款申请通知（卖家收）：买家提交退款申请时调用 */
    private void sendRefundApplyNotice(GoodsOrder order, List<GoodsOrderItem> items, String refundReason) {
        if (items.isEmpty()) {
            return;
        }
        String buyerNickname = userNickname(order.getUserId());
        sendNotice(items.get(0).getPublisherId(), order.getUserId(), NOTIFY_TYPE_REFUND, "买家申请退款",
                "买家 " + buyerNickname + " 对订单 " + order.getOrderNo() + " 提交了退款申请，理由：" + refundReason + "。",
                order.getOrderNo());
    }

    /** 发送退款结果通知（买家收）：非遗人同意 / 驳回退款时调用 */
    private void sendRefundResultNotice(GoodsOrder order, Long sellerId, boolean agreed, String rejectReason) {
        if (agreed) {
            sendNotice(order.getUserId(), sellerId, NOTIFY_TYPE_REFUND, "退款申请已通过",
                    "订单 " + order.getOrderNo() + " 卖家已同意退款，退款将按原支付渠道退回。", order.getOrderNo());
        } else {
            sendNotice(order.getUserId(), sellerId, NOTIFY_TYPE_REFUND, "退款申请被拒绝",
                    "订单 " + order.getOrderNo() + " 卖家拒绝了退款申请，原因：" + rejectReason + "。", order.getOrderNo());
        }
    }

    /**
     * 通用通知发送：组装通知消息经 RabbitMQ 异步投递通知服务（接收者为空 / 自己触发自己的事件时跳过；发送失败仅告警）
     * 消息链路：交换机 mic.notice.exchange（路由键 notice.order）→ 队列 mic.order.notice.queue → mic-notice 消费落库
     * 与订单事务解耦：不等待通知落库结果，投递失败由 confirm / returns 回调记录日志（通知属旁路流程，不做补偿）
     */
    private void sendNotice(Long receiveUserId, Long sendUserId, int notifyType,
                            String title, String content, String bizId) {
        if (receiveUserId == null || Objects.equals(receiveUserId, sendUserId)) {
            return;
        }
        NoticeCreateDTO notice = new NoticeCreateDTO();
        notice.setReceiveUserId(receiveUserId);
        notice.setSendUserId(sendUserId);
        notice.setNotifyType(notifyType);
        notice.setTitle(truncate(title, NOTIFY_TITLE_MAX));
        notice.setContent(truncate(content, NOTIFY_CONTENT_MAX));
        notice.setBizType(NOTIFY_BIZ_TYPE_ORDER);
        notice.setBizId(bizId);
        try {
            rabbitTemplate.convertAndSend(MqConstant.NOTICE_EXCHANGE, MqConstant.ORDER_NOTICE_ROUTING_KEY, notice);
        } catch (Exception e) {
            log.warn("订单通知消息发送异常（不影响订单业务）：receiveUserId={}, title={}, error={}",
                    receiveUserId, title, e.getMessage());
        }
    }

    /** 查询用户昵称（sys_user 同库）：通知文案展示用；用户不存在 / 昵称为空时兜底「用户」 */
    private String userNickname(Long userId) {
        String nickname = userId == null ? null : baseMapper.selectNicknameById(userId);
        return nickname == null || nickname.isEmpty() ? "用户" : nickname;
    }

    /** 文本截断：超出上限时截断加省略号（通知标题 / 内容上限与 notification 表字段长度对齐） */
    private String truncate(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength - 3) + "...";
    }
}
