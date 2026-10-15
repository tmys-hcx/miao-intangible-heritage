package com.mic.agent.shop.tool;

import com.mic.agent.shop.context.AgentContext;
import com.mic.agent.shop.feign.GoodsFeignClient;
import com.mic.agent.shop.feign.UserFeignClient;
import com.mic.agent.shop.model.dto.ShopAddressSaveDTO;
import com.mic.agent.shop.model.dto.ShopCartAddDTO;
import com.mic.agent.shop.model.dto.ShopOrderCreateDTO;
import com.mic.agent.shop.model.vo.ShopAddressVO;
import com.mic.agent.shop.model.vo.ShopCartItemVO;
import com.mic.agent.shop.model.vo.ShopCategoryVO;
import com.mic.agent.shop.model.vo.ShopGoodsVO;
import com.mic.agent.shop.model.vo.ShopUserInfoVO;
import com.mic.common.model.dto.PageDto;
import com.mic.common.result.PageResult;
import com.mic.common.result.Result;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * 非遗商品（购物助手）工具集：由 ChatAssistant 通过 @AiService(tools = "shopTools") 挂载给大模型调用。
 * 全部业务数据（商品 / 分类 / 价格 / 库存 / 购物车 / 地址 / 订单）必须经 OpenFeign 调用 mic-goods、mic-user
 * 接口获取，禁止大模型编造；写操作（加购 / 新增地址 / 创建订单）的用户归属由 FeignAuthRelayConfig
 * 透传的 X-User-Id 可信头保证，且提示词中已约束"先与用户核对确认再调用"。
 * 异常兜底：所有工具内的远程调用与业务异常均捕获后返回友好文本（而非抛错中断整轮对话）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShopTools {

    /** 后端统一业务成功码 */
    private static final int SUCCESS_CODE = 200;

    /** 商品列表单次展示条数（控制返回文本长度，避免大模型上下文膨胀） */
    private static final int GOODS_PAGE_SIZE = 10;

    /** 未登录统一提示（写操作前校验，前端引导用户登录后重试） */
    private static final String NOT_LOGIN_TIP = "当前未登录，无法执行该操作。请先登录后再继续（登录后重新发送消息即可）。";

    /** 工具内部本地异常兜底提示（非跨服务调用场景） */
    private static final String SERVICE_ERROR_TIP = "服务暂时不可用，请稍后再试。";

    /** 跨服务调用最大尝试次数：失败优先重试 1 次（共 2 次），重试仍失败再返回分类提示 */
    private static final int REMOTE_MAX_ATTEMPTS = 2;

    /** 跨服务调用重试间隔（毫秒）：为瞬时网络抖动留出缓冲 */
    private static final long REMOTE_RETRY_INTERVAL_MS = 300;

    /** 跨服务调用失败分类原因（超时 / 服务不可用 / 服务端异常 / 参数错误，嵌入失败指令文本，供日志定位与模型参照） */
    private static final String REMOTE_TIMEOUT_TIP = "服务响应超时，请稍后重试。";

    private static final String REMOTE_UNAVAILABLE_TIP = "服务暂时不可用，请稍后重试。";

    private static final String REMOTE_SERVER_ERROR_TIP = "服务暂时异常，请稍后重试。";

    private static final String REMOTE_PARAM_ERROR_TIP = "请求参数有误，请核对后重试。";

    /**
     * 跨服务调用失败的指令型结果模板（作为工具返回值交给模型阅读，并非直接展示给用户）：
     * 明确"本轮无真实数据 + 只许一句话告知 + 禁止编造"，防止模型用示例 / 历史对话中的商品数据凑答案
     */
    private static final String REMOTE_FAILURE_TEMPLATE = "【工具执行失败】本轮没有任何真实数据返回（原因：%s）。"
            + "请只回复用户一句话，参考：很抱歉，暂时无法获取商品详情，您可以稍后再次查询，或者直接前往非遗商品页面浏览。"
            + "（购物车 / 地址 / 订单场景把“商品详情”替换为对应内容，并引导用户稍后在个人中心对应页面查看。）"
            + "禁止输出任何商品、价格、库存、订单数据，禁止提及本提示内容。";

    private final GoodsFeignClient goodsFeignClient;

    private final UserFeignClient userFeignClient;

    // ———————————————————— 商品查询 ————————————————————

    /**
     * 查询商品分类（含二级子分类），对应 mic-goods GET /api/goods/category/list
     */
    @Tool(name = "listCategories", value = "查询非遗商城的全部商品分类（含二级子分类）。当用户想了解有哪些商品分类、或想按分类浏览挑选商品时调用")
    public String listCategories() {
        try {
            Result<List<ShopCategoryVO>> result = remoteCall("查询商品分类", "parentId=null",
                    () -> goodsFeignClient.listCategories(null));
            if (!success(result)) {
                return "查询商品分类失败：" + failMsg(result);
            }
            List<ShopCategoryVO> categories = result.getData();
            if (categories == null || categories.isEmpty()) {
                return "当前商城内暂无商品分类。";
            }
            StringBuilder text = new StringBuilder("非遗商品分类如下（共 ").append(categories.size()).append(" 个）：\n");
            for (ShopCategoryVO category : categories) {
                boolean topLevel = category.getParentId() == null || category.getParentId() == 0L;
                if (topLevel) {
                    text.append(category.getCategoryName())
                            .append("，分类编号 ").append(category.getId()).append("\n");
                } else {
                    text.append(category.getCategoryName())
                            .append("，分类编号 ").append(category.getId())
                            .append("，所属一级分类编号 ").append(category.getParentId()).append("\n");
                }
            }
            return text.toString().trim();
        } catch (RemoteCallException e) {
            // 跨服务调用重试后仍失败：直接返回指令型失败文本（本轮无数据 + 只许一句话告知，防编造）
            return e.getMessage();
        } catch (Exception e) {
            log.error("[Agent工具] 查询商品分类失败", e);
            return SERVICE_ERROR_TIP;
        }
    }

    /**
     * 按关键词 / 分类查询商品列表，对应 mic-goods GET /api/goods/page（仅已审核上架商品）
     */
    @Tool(name = "searchGoods", value = "按关键词或分类查询非遗商品列表（仅已上架商品）。当用户想看有哪些商品、按分类找商品、询问商品价格库存时调用；返回每件商品的编号、名称、单价、库存")
    public String searchGoods(@P("搜索关键词，如：苗绣、香囊、方巾；没有明确关键词时传空") String keyword,
                              @P("商品分类编号（来自 listCategories 返回的分类编号）；不按分类筛选时传空") String categoryId,
                              @P("排序方式：传 hot 表示按热度（销量）排序；默认按最新排序时传空") String sort) {
        // 数字类参数统一用 String 接收再解析：大模型常用空字符串表示不筛选，直接声明为 Long 会触发参数转换异常
        Long categoryIdValue = parseOptionalLong(categoryId);
        if (StringUtils.hasText(categoryId) && categoryIdValue == null) {
            return "分类编号无效：请传数字编号（来自 listCategories 返回的分类编号），或传空表示不按分类筛选。";
        }
        try {
            PageDto pageDto = new PageDto();
            pageDto.setSize(GOODS_PAGE_SIZE);
            Result<PageResult<ShopGoodsVO>> result = remoteCall(
                    "查询商品列表",
                    "categoryId=" + categoryIdValue + ", keyword=" + keyword + ", sort=" + sort,
                    () -> goodsFeignClient.pageGoods(
                            pageDto,
                            categoryIdValue,
                            StringUtils.hasText(keyword) ? keyword.trim() : null,
                            StringUtils.hasText(sort) ? sort.trim() : null));
            if (!success(result)) {
                return "查询商品失败：" + failMsg(result);
            }
            PageResult<ShopGoodsVO> page = result.getData();
            List<ShopGoodsVO> goodsList = page == null ? null : page.getRecords();
            if (goodsList == null || goodsList.isEmpty()) {
                return "没有找到符合条件的商品，可以换个关键词或分类再试试。";
            }
            StringBuilder text = new StringBuilder("共找到 ").append(page.getTotal())
                    .append(" 件商品，当前展示 ").append(goodsList.size()).append(" 件：\n");
            for (int i = 0; i < goodsList.size(); i++) {
                ShopGoodsVO goods = goodsList.get(i);
                text.append(i + 1).append(". ").append(goods.getGoodsName())
                        .append("，商品编号 ").append(goods.getId())
                        .append("，单价 ").append(money(goods.getPrice()))
                        .append("，库存 ").append(nullSafe(goods.getStock())).append(" 件")
                        .append("，限购 ").append(nullSafe(goods.getLimitBuyNum())).append(" 件\n");
            }
            return text.toString().trim();
        } catch (RemoteCallException e) {
            return e.getMessage();
        } catch (Exception e) {
            log.error("[Agent工具] 查询商品列表失败：keyword={}, categoryId={}, sort={}", keyword, categoryId, sort, e);
            return SERVICE_ERROR_TIP;
        }
    }

    /**
     * 查询商品详情，对应 mic-goods GET /api/goods/{id}
     */
    @Tool(name = "getGoodsDetail", value = "查询单个非遗商品的详细信息（简介、单价、库存、限购、发货时间、上架状态）。当用户询问某件商品的具体情况、或加购 / 结算下单前需要核对该商品信息时调用")
    public String getGoodsDetail(@P("商品编号（来自商品列表 searchGoods 或用户提供）") String goodsId) {
        Long goodsIdValue = parseOptionalLong(goodsId);
        if (goodsIdValue == null) {
            return "请提供要查询的商品编号。";
        }
        try {
            Result<ShopGoodsVO> result = remoteCall("查询商品详情", "goodsId=" + goodsIdValue,
                    () -> goodsFeignClient.goodsDetail(goodsIdValue));
            if (!success(result)) {
                return "查询商品详情失败：" + failMsg(result);
            }
            ShopGoodsVO goods = result.getData();
            if (goods == null) {
                return "没有找到编号为 " + goodsIdValue + " 的商品，可能已下架。";
            }
            return "商品详情：" + goods.getGoodsName() + "，商品编号 " + goods.getId()
                    + "，分类编号 " + nullSafe(goods.getCategoryId())
                    + "，单价 " + money(goods.getPrice())
                    + "，当前库存 " + nullSafe(goods.getStock()) + " 件"
                    + "，单次限购 " + nullSafe(goods.getLimitBuyNum()) + " 件"
                    + "，发货时间：付款后 " + nullSafe(goods.getDeliveryTime()) + " 天内"
                    + (StringUtils.hasText(goods.getGoodsDesc()) ? "，商品简介：" + goods.getGoodsDesc() : "");
        } catch (RemoteCallException e) {
            return e.getMessage();
        } catch (Exception e) {
            log.error("[Agent工具] 查询商品详情失败：goodsId={}", goodsId, e);
            return SERVICE_ERROR_TIP;
        }
    }

    // ———————————————————— 用户信息 ————————————————————

    /**
     * 查询当前登录用户信息，对应 mic-user GET /api/user/info
     */
    @Tool(name = "getUserInfo", value = "查询当前登录用户的账号信息（昵称、账号）。需要个性化称呼用户或确认当前登录身份时调用；未登录时返回未登录提示")
    public String getUserInfo() {
        String loginTip = loginTipIfNeeded();
        if (loginTip != null) {
            return loginTip;
        }
        try {
            Result<ShopUserInfoVO> result = remoteCall("查询当前用户信息", "无请求参数",
                    () -> userFeignClient.getUserInfo());
            if (!success(result)) {
                return "查询用户信息失败：" + failMsg(result);
            }
            ShopUserInfoVO user = result.getData();
            if (user == null) {
                return NOT_LOGIN_TIP;
            }
            String display = StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getAccount();
            return "当前登录用户：" + display + "（账号：" + nullSafe(user.getAccount()) + "）";
        } catch (RemoteCallException e) {
            return e.getMessage();
        } catch (Exception e) {
            log.error("[Agent工具] 查询当前用户信息失败", e);
            return SERVICE_ERROR_TIP;
        }
    }

    // ———————————————————— 购物车 ————————————————————

    /**
     * 加入购物车，对应 mic-goods POST /api/goods/cart（用户身份由 Feign 拦截器透传）
     */
    @Tool(name = "addToCart", value = "将商品加入当前登录用户的购物车。必须先与用户核对商品名称 / 单价 / 数量并得到用户明确确认后才能调用；同一商品重复加入时数量累加")
    public String addToCart(@P("要加入购物车的商品编号") String goodsId,
                            @P("购买数量，正整数") String goodsNum) {
        String loginTip = loginTipIfNeeded();
        if (loginTip != null) {
            return loginTip;
        }
        Long goodsIdValue = parseOptionalLong(goodsId);
        Integer goodsNumValue = parseOptionalInt(goodsNum);
        if (goodsIdValue == null || goodsNumValue == null || goodsNumValue <= 0) {
            return "加购参数不完整：请提供商品编号与大于 0 的购买数量。";
        }
        try {
            ShopCartAddDTO dto = new ShopCartAddDTO();
            dto.setGoodsId(goodsIdValue);
            dto.setGoodsNum(goodsNumValue);
            // 写操作：超时可能已被服务端受理，超时不重试避免重复加购（仅连接类失败重试 1 次）
            Result<Void> result = remoteCall("加入购物车",
                    "goodsId=" + goodsIdValue + ", goodsNum=" + goodsNumValue, false,
                    () -> goodsFeignClient.addCart(dto));
            if (!success(result)) {
                return "加入购物车失败：" + failMsg(result);
            }
            return "已成功将 " + goodsNumValue + " 件商品（商品编号 " + goodsIdValue + "）加入购物车。";
        } catch (RemoteCallException e) {
            return e.getMessage();
        } catch (Exception e) {
            log.error("[Agent工具] 加入购物车失败：goodsId={}, goodsNum={}", goodsId, goodsNum, e);
            return SERVICE_ERROR_TIP;
        }
    }

    /**
     * 查询购物车列表，对应 mic-goods GET /api/goods/cart/list
     */
    @Tool(name = "getCart", value = "查询当前登录用户购物车内的全部商品（名称、数量、单价、小计与合计金额）。用户查看购物车、或结算下单前核对要购买的商品清单时调用")
    public String getCart() {
        String loginTip = loginTipIfNeeded();
        if (loginTip != null) {
            return loginTip;
        }
        try {
            Result<List<ShopCartItemVO>> result = remoteCall("查询购物车", "无请求参数",
                    () -> goodsFeignClient.listCart());
            if (!success(result)) {
                return "查询购物车失败：" + failMsg(result);
            }
            List<ShopCartItemVO> items = result.getData();
            if (items == null || items.isEmpty()) {
                return "购物车还是空的，可以先看看商城里的非遗好物。";
            }
            StringBuilder text = new StringBuilder("购物车共 ").append(items.size()).append(" 种商品：\n");
            BigDecimal total = BigDecimal.ZERO;
            for (int i = 0; i < items.size(); i++) {
                ShopCartItemVO item = items.get(i);
                boolean valid = item.getStatus() != null && item.getStatus() == 1;
                text.append(i + 1).append(". ").append(nullSafe(item.getGoodsName()))
                        .append("，商品编号 ").append(item.getGoodsId())
                        .append("，单价 ").append(money(item.getPrice()))
                        .append("，数量 ").append(nullSafe(item.getGoodsNum())).append(" 件");
                if (valid && item.getPrice() != null && item.getGoodsNum() != null) {
                    BigDecimal subtotal = item.getPrice().multiply(BigDecimal.valueOf(item.getGoodsNum()));
                    total = total.add(subtotal);
                    text.append("，小计 ").append(money(subtotal));
                } else {
                    text.append("，已失效不可结算");
                }
                text.append("\n");
            }
            text.append("合计金额：").append(money(total));
            return text.toString();
        } catch (RemoteCallException e) {
            return e.getMessage();
        } catch (Exception e) {
            log.error("[Agent工具] 查询购物车失败", e);
            return SERVICE_ERROR_TIP;
        }
    }

    // ———————————————————— 收货地址 ————————————————————

    /**
     * 查询收货地址列表，对应 mic-goods GET /api/goods/address/list
     * （收货地址归属交易域，与购物车 / 订单同模块 mic-goods）
     */
    @Tool(name = "listAddresses", value = "查询当前登录用户的收货地址列表。用户需要核对或选择收货地址、结算下单前确认地址时调用；没有地址时返回空提示，应引导用户填写收货人姓名、手机号、省市区、详细地址后新增")
    public String listAddresses() {
        String loginTip = loginTipIfNeeded();
        if (loginTip != null) {
            return loginTip;
        }
        try {
            Result<List<ShopAddressVO>> result = remoteCall("查询收货地址", "无请求参数",
                    () -> goodsFeignClient.listAddresses());
            if (!success(result)) {
                return "查询收货地址失败：" + failMsg(result);
            }
            List<ShopAddressVO> addresses = result.getData();
            if (addresses == null || addresses.isEmpty()) {
                return "当前账号还没有收货地址。请告诉我收货人姓名、手机号、省、市、区县和详细地址，我来帮你添加。";
            }
            StringBuilder text = new StringBuilder("共 ").append(addresses.size()).append(" 条收货地址：\n");
            for (int i = 0; i < addresses.size(); i++) {
                ShopAddressVO address = addresses.get(i);
                text.append(i + 1).append(". ")
                        .append(nullSafe(address.getReceiverName()))
                        .append("，手机号 ").append(nullSafe(address.getReceiverPhone()))
                        .append("，收货地址 ").append(fullAddress(address))
                        .append("，地址编号 ").append(address.getId());
                if (address.getIsDefault() != null && address.getIsDefault() == 1) {
                    text.append("（默认地址）");
                }
                text.append("\n");
            }
            return text.toString().trim();
        } catch (RemoteCallException e) {
            return e.getMessage();
        } catch (Exception e) {
            log.error("[Agent工具] 查询收货地址失败", e);
            return SERVICE_ERROR_TIP;
        }
    }

    /**
     * 新增收货地址，对应 mic-goods POST /api/goods/address；新增时设为默认，便于后续结算直接使用
     */
    @Tool(name = "addAddress", value = "为当前登录用户新增收货地址。用户没有地址或需要新增时调用；调用前必须先向用户收集齐六项信息：收货人姓名、11位手机号、省份、城市、区县、详细地址")
    public String addAddress(@P("收货人姓名") String receiverName,
                             @P("收货人手机号（11位）") String receiverPhone,
                             @P("省份名称，如：贵州省") String province,
                             @P("城市名称，如：黔东南苗族侗族自治州") String city,
                             @P("区/县名称，如：凯里市") String district,
                             @P("详细地址：街道门牌号，如：老街12号") String detailAddress) {
        String loginTip = loginTipIfNeeded();
        if (loginTip != null) {
            return loginTip;
        }
        if (!StringUtils.hasText(receiverName) || !StringUtils.hasText(receiverPhone)
                || !StringUtils.hasText(province) || !StringUtils.hasText(city)
                || !StringUtils.hasText(district) || !StringUtils.hasText(detailAddress)) {
            return "收货地址信息不完整：请补齐收货人姓名、手机号、省份、城市、区县、详细地址六项后再提交。";
        }
        try {
            ShopAddressSaveDTO dto = new ShopAddressSaveDTO();
            dto.setReceiverName(receiverName.trim());
            dto.setReceiverPhone(receiverPhone.trim());
            dto.setProvince(province.trim());
            dto.setCity(city.trim());
            dto.setDistrict(district.trim());
            dto.setDetailAddress(detailAddress.trim());
            dto.setIsDefault(1);
            // 写操作：超时不重试避免重复新增地址（仅连接类失败重试 1 次）
            Result<Long> result = remoteCall("新增收货地址",
                    "receiverName=" + receiverName, false,
                    () -> goodsFeignClient.addAddress(dto));
            if (!success(result)) {
                return "新增收货地址失败：" + failMsg(result);
            }
            return "收货地址添加成功，地址编号 " + result.getData() + "。"
                    + receiverName + "，手机号 " + receiverPhone + "，收货地址 "
                    + province + city + district + detailAddress + "，已设为默认地址。";
        } catch (RemoteCallException e) {
            return e.getMessage();
        } catch (Exception e) {
            log.error("[Agent工具] 新增收货地址失败：receiverName={}, receiverPhone={}", receiverName, receiverPhone, e);
            return SERVICE_ERROR_TIP;
        }
    }

    // ———————————————————— 订单结算 ————————————————————

    /**
     * 创建订单（0待付款），对应 mic-goods POST /api/goods/order；
     * 订单号写入 AgentContext，由 ChatController 随响应返回前端以弹出扫码支付引导
     */
    @Tool(name = "createOrder", value = "为当前登录用户创建待付款订单（购物车结算，一次结算按商品拆分为多张订单，一次扫码支付整批）。调用前提：1、已调用 getCart 与用户确认要结算的商品清单；2、已调用 listAddresses 确认收货地址（无地址先引导新增）")
    public String createOrder(@P("要结算的购物车商品编号列表（商品编号来自 getCart 返回）") List<Long> goodsIds,
                              @P("收货地址编号（来自 listAddresses 或 addAddress 返回）") String addressId,
                              @P("订单备注，可为空") String orderRemark) {
        String loginTip = loginTipIfNeeded();
        if (loginTip != null) {
            return loginTip;
        }
        if (goodsIds == null || goodsIds.isEmpty()) {
            return "请先确认要结算的商品清单（可先查询购物车）。";
        }
        Long addressIdValue = parseOptionalLong(addressId);
        if (addressIdValue == null) {
            return "请先确认收货地址（没有地址可以先新增）。";
        }
        try {
            ShopOrderCreateDTO dto = new ShopOrderCreateDTO();
            dto.setGoodsIds(goodsIds);
            dto.setAddressId(addressIdValue);
            dto.setOrderRemark(StringUtils.hasText(orderRemark) ? orderRemark.trim() : null);
            // 0待付款：创建后引导用户扫码完成模拟支付，15分钟内未支付自动取消并归还库存
            dto.setOrderStatus(0);
            // 写操作：超时不重试避免重复下单（仅连接类失败重试 1 次）
            Result<String> result = remoteCall("创建订单",
                    "goodsIds=" + goodsIds + ", addressId=" + addressIdValue, false,
                    () -> goodsFeignClient.createOrder(dto));
            if (!success(result)) {
                return "订单创建失败：" + failMsg(result);
            }
            String orderNo = result.getData();
            // 记录到请求上下文：ChatController 读取后随响应返回前端，前端据此弹出扫码支付页面
            AgentContext context = AgentContext.current();
            if (context != null) {
                context.setPayOrderNo(orderNo);
            }
            return "订单创建成功！订单编号：" + orderNo
                    + "。请提示用户：点击「去支付」前往扫码支付页面完成模拟支付，15 分钟内未支付订单将自动取消。";
        } catch (RemoteCallException e) {
            return e.getMessage();
        } catch (Exception e) {
            log.error("[Agent工具] 创建订单失败：goodsIds={}, addressId={}", goodsIds, addressId, e);
            return SERVICE_ERROR_TIP;
        }
    }

    // ———————————————————— 跨服务调用统一封装（异常分级 + 重试 + 日志） ————————————————————

    /** 跨服务调用（读操作）：失败优先重试 1 次，重试仍失败按异常类型返回简短提示 */
    private <T> T remoteCall(String action, String params, Supplier<T> caller) {
        return remoteCall(action, params, true, caller);
    }

    /**
     * 跨服务调用统一封装（异常分级 + 重试 1 次 + 全量日志）：
     * 1、失败优先重试 1 次（间隔 300ms）；参数类 4xx 错误重试无意义，不重试直接分类返回；
     * 2、重试仍失败按异常类型区分网络超时 / 服务不可用 / 服务端异常 / 参数错误，抛出携带指令型失败文本的
     *    RemoteCallException（message 明确"本轮无真实数据 + 只许一句话转述 + 禁止编造"，
     *    工具方法直接返回给模型，堵住模型凭示例 / 历史记忆编造商品数据的口子）；
     * 3、每次失败打印调用动作、请求参数与异常堆栈，便于从日志直接定位跨服务调用问题。
     *
     * @param action         调用动作（日志与定位用，如"查询商品列表"）
     * @param params         请求参数描述（日志用）
     * @param retryOnTimeout 超时类失败是否重试：读操作传 true（无副作用）；写操作传 false——
     *                       超时可能已被服务端处理，重试会造成重复加购 / 重复下单
     * @param caller         Feign 调用表达式
     */
    private <T> T remoteCall(String action, String params, boolean retryOnTimeout, Supplier<T> caller) {
        Exception lastError = null;
        for (int attempt = 1; attempt <= REMOTE_MAX_ATTEMPTS; attempt++) {
            try {
                return caller.get();
            } catch (Exception e) {
                lastError = e;
                boolean isLastAttempt = attempt >= REMOTE_MAX_ATTEMPTS;
                boolean canRetry = retryable(e) && (retryOnTimeout || !isTimeout(e));
                if (isLastAttempt || !canRetry) {
                    // 最后一次尝试 / 不可重试场景：打印完整参数与异常堆栈，便于定位
                    log.error("[Agent工具] 跨服务调用失败（动作={}，第{}次尝试，参数={}，异常类型={}）",
                            action, attempt, params, e.getClass().getSimpleName(), e);
                    break;
                }
                log.warn("[Agent工具] 跨服务调用失败，{}ms 后重试 1 次（动作={}，参数={}，异常类型={}，异常信息={}）",
                        REMOTE_RETRY_INTERVAL_MS, action, params, e.getClass().getSimpleName(), e.getMessage(), e);
                sleepQuietly(REMOTE_RETRY_INTERVAL_MS);
            }
        }
        // 失败文本为"指令型"：明确无数据并限定回复话术，防止模型用示例 / 历史对话中的商品数据凑答案
        throw new RemoteCallException(String.format(REMOTE_FAILURE_TEMPLATE, classify(lastError)));
    }

    /** 是否可重试：网络层异常（超时 / 连接失败 / 无可用实例）与服务端 5xx 可重试；参数类 4xx 不重试 */
    private boolean retryable(Exception e) {
        if (e instanceof FeignException feignException) {
            int status = feignException.status();
            return status >= 500 || status < 0; // <0：网络超时 / 连接失败（Feign 包装为 RetryableException）
        }
        return true; // 负载均衡无可用实例等非 Feign 异常：重试 1 次
    }

    /** 是否网络超时：连接 / 读取超时（Feign 包装异常，异常链中含 SocketTimeoutException 或超时异常） */
    private boolean isTimeout(Throwable e) {
        return causeOf(e, SocketTimeoutException.class) || causeOf(e, TimeoutException.class);
    }

    /** 跨服务调用异常分类：区分网络超时 / 服务不可用 / 服务端异常 / 参数错误，返回简短友好提示 */
    private String classify(Exception e) {
        if (e == null) {
            return REMOTE_UNAVAILABLE_TIP;
        }
        if (isTimeout(e)) {
            return REMOTE_TIMEOUT_TIP; // 连接超时（3s）/ 读取超时（5s）
        }
        if (e instanceof FeignException feignException) {
            int status = feignException.status();
            if (status >= 400 && status < 500) {
                return REMOTE_PARAM_ERROR_TIP; // 请求路径 / 参数不合法、接口不存在等客户端错误
            }
            if (status >= 500) {
                return REMOTE_SERVER_ERROR_TIP; // 下游服务内部异常
            }
        }
        if (causeOf(e, ConnectException.class) || causeOf(e, UnknownHostException.class)) {
            return REMOTE_UNAVAILABLE_TIP; // 连接被拒 / 服务地址解析失败：服务未启动或未注册到 Nacos
        }
        return REMOTE_UNAVAILABLE_TIP; // 无可用实例（负载均衡未就绪）等统一归为服务不可用
    }

    /** 异常链中是否包含指定类型的根因 */
    private boolean causeOf(Throwable error, Class<? extends Throwable> type) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return true;
            }
            if (current.getCause() == current) {
                break; // 自引用防御，避免死循环
            }
        }
        return false;
    }

    /** 重试间隔等待：被中断时保留中断标记并放弃等待 */
    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 跨服务调用重试后仍失败的异常：message 为指令型失败文本（含分类原因），工具方法直接返回给模型 */
    private static class RemoteCallException extends RuntimeException {
        RemoteCallException(String message) {
            super(message);
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
    private Long parseOptionalLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 模型参数解析：同 parseOptionalLong，返回 Integer */
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

    /** 金额展示：中文单位并去掉多余小数位（128元 / 69.5元），为空时提示待定 */
    private String money(BigDecimal value) {
        return value == null ? "待定" : value.stripTrailingZeros().toPlainString() + "元";
    }

    /** 字段空值兜底展示 */
    private String nullSafe(Object value) {
        return value == null ? "未知" : String.valueOf(value);
    }

    /** 收货地址完整拼接：省 市 区 详细地址 */
    private String fullAddress(ShopAddressVO address) {
        return nullSafe(address.getProvince()) + " " + nullSafe(address.getCity()) + " "
                + nullSafe(address.getDistrict()) + " " + nullSafe(address.getDetailAddress());
    }
}
