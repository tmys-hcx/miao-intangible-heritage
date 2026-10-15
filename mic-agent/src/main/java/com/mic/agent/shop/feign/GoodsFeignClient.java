package com.mic.agent.shop.feign;

import com.mic.agent.shop.model.dto.ShopAddressSaveDTO;
import com.mic.agent.shop.model.dto.ShopCartAddDTO;
import com.mic.agent.shop.model.dto.ShopOrderCreateDTO;
import com.mic.agent.shop.model.vo.ShopAddressVO;
import com.mic.agent.shop.model.vo.ShopCartItemVO;
import com.mic.agent.shop.model.vo.ShopCategoryVO;
import com.mic.agent.shop.model.vo.ShopGoodsVO;
import com.mic.common.model.dto.PageDto;
import com.mic.common.model.feign.FeignConstant;
import com.mic.common.result.PageResult;
import com.mic.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * mic-goods 非遗商品服务 Feign 客户端（Agent 直连，按 Nacos 服务名负载均衡）：
 * 覆盖商品查询（分类 / 列表 / 详情）、购物车（加购 / 列表）、收货地址（列表 / 新增）、订单（创建）接口契约，
 * 入参 / 出参使用 Agent 侧镜像实体（字段与 mic-goods 接口 JSON 对齐）；
 * 用户身份不通过方法参数传递——由 FeignAuthRelayConfig 拦截器统一透传 X-User-Id / X-User-Role 可信头，
 * 下游接口据此将购物车 / 地址 / 订单操作限定在当前登录用户本人。
 */
@FeignClient(name = FeignConstant.SERVICE_GOODS, path = "/api/goods")
public interface GoodsFeignClient {

    /**
     * 前台商品分页：仅返回已审核上架商品，支持分类筛选 / 关键词搜索 / 排序
     *
     * @param pageDto    分页参数（current / size）
     * @param categoryId 分类ID（可空）
     * @param keyword    关键词：匹配商品名称 / 描述（可空）
     * @param sort       排序：hot最热（按销量），缺省最新
     */
    @GetMapping("/page")
    Result<PageResult<ShopGoodsVO>> pageGoods(@SpringQueryMap PageDto pageDto,
                                              @RequestParam(value = "categoryId", required = false) Long categoryId,
                                              @RequestParam(value = "keyword", required = false) String keyword,
                                              @RequestParam(value = "sort", required = false) String sort);

    /**
     * 商品分类列表：parentId为空查全部，传0查一级分类，传ID查其子分类
     */
    @GetMapping("/category/list")
    Result<List<ShopCategoryVO>> listCategories(@RequestParam(value = "parentId", required = false) Long parentId);

    /**
     * 商品详情：仅返回已审核商品
     */
    @GetMapping("/{id}")
    Result<ShopGoodsVO> goodsDetail(@PathVariable("id") Long id);

    /**
     * 加入购物车（登录用户）：同一商品重复加入时数量累加，超出库存 / 限购时返回业务失败提示
     */
    @PostMapping("/cart")
    Result<Void> addCart(@RequestBody ShopCartAddDTO dto);

    /**
     * 购物车列表（登录用户）：本人购物车，携带商品名称 / 价格 / 库存等展示信息
     */
    @GetMapping("/cart/list")
    Result<List<ShopCartItemVO>> listCart();

    /**
     * 收货地址列表（登录用户）：本人全部地址，默认地址在前
     */
    @GetMapping("/address/list")
    Result<List<ShopAddressVO>> listAddresses();

    /**
     * 新增收货地址（登录用户）：返回新增地址ID；设为默认时服务端自动替换原默认地址
     */
    @PostMapping("/address")
    Result<Long> addAddress(@RequestBody ShopAddressSaveDTO dto);

    /**
     * 创建订单（登录用户）：购物车结算，按商品拆分为多张 0待付款订单（共享结算批次号），
     * 返回批次首张订单编号，供前端跳转沙箱模拟支付页扫码支付
     */
    @PostMapping("/order")
    Result<String> createOrder(@RequestBody ShopOrderCreateDTO dto);
}
