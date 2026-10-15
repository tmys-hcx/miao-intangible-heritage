package com.mic.goods.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mic.common.model.dto.PageDto;
import com.mic.common.result.PageResult;
import com.mic.goods.model.dto.GoodsSaveDTO;
import com.mic.goods.model.entity.IntangibleGoods;
import com.mic.goods.model.vo.AdminStatsVO;

import java.util.List;

/**
 * 非遗商品服务接口
 */
public interface IntangibleGoodsService extends IService<IntangibleGoods> {

    /**
     * 前台商品分页：仅查询已审核（上架）商品，支持分类筛选 / 名称、描述或分类名搜索 / 最新·最热排序
     * @param pageDto 分页参数（默认第1页、每页10条）
     * @param categoryId 分类ID（可选，空则查全部分类）
     * @param keyword 搜索关键词（可选，匹配商品名称 / 描述 / 分类名称；输入一级分类名可命中该分类及其全部二级子分类下的商品）
     * @param sort 排序方式：hot=最热（销量倒序）；其余=最新（创建时间倒序）
     * @return 分页结果
     */
    PageResult<IntangibleGoods> pageGoods(PageDto pageDto, Long categoryId, String keyword, String sort);

    /**
     * 前台最新上架商品推荐（不分页）：仅查询已审核（上架）商品，按发布时间倒序取前 N 条
     * @param categoryId 分类ID（可选，空则查全部分类）
     * @param keyword 搜索关键词（可选，匹配商品名称 / 描述 / 分类名称；输入一级分类名可命中该分类及其全部二级子分类下的商品）
     * @param limit 返回条数上限（前台商品推荐固定取9条）
     * @return 最新上架的商品列表（最多 limit 条）
     */
    List<IntangibleGoods> listLatestGoods(Long categoryId, String keyword, int limit);

    /**
     * 商品详情：仅返回已审核商品
     * @param id 商品ID
     * @return 商品详情，不存在或未上架时抛出业务异常
     */
    IntangibleGoods getGoodsDetail(Long id);

    /**
     * 发布商品（非遗人）：校验分类存在后新增，初始状态待审核
     * @param dto 商品信息
     * @param publisherId 发布人ID（网关透传的当前登录用户ID）
     * @return 新增商品ID
     */
    Long publishGoods(GoodsSaveDTO dto, Long publisherId);

    /**
     * 修改商品（非遗人）：仅可修改本人发布的商品，修改后重新进入待审核
     * @param id 商品ID
     * @param dto 商品信息
     * @param publisherId 当前登录用户ID
     */
    void updateGoods(Long id, GoodsSaveDTO dto, Long publisherId);

    /**
     * 删除商品（非遗人）：仅可删除本人发布的商品（逻辑删除）
     * @param id 商品ID
     * @param publisherId 当前登录用户ID
     */
    void deleteGoods(Long id, Long publisherId);

    /**
     * 下架商品（非遗人）：仅可下架本人发布的、处于已上架状态（1）的商品
     * 下架后商品（3已下架）前台不可见、不可搜索、不可加购 / 下单；
     * 库存售罄（0）的已上架商品由下单扣减库存流程自动下架（见 deductStock）
     * @param id 商品ID
     * @param publisherId 当前登录用户ID
     */
    void offShelfGoods(Long id, Long publisherId);

    /**
     * 审核商品（管理员）：审核通过上架（前台可见） / 审核拒绝打回（前台不可见）
     * 审核拒绝时必须填写不合格原因（remark）；审核结论与备注存入 Redis 审核记录
     * @param id 商品ID
     * @param auditStatus 审核结论：1审核通过 2审核拒绝
     * @param remark 审核备注：审核拒绝时为打回原因（必填）
     * @param auditorId 审核人（管理员）ID
     */
    void auditGoods(Long id, Integer auditStatus, String remark, Long auditorId);

    /**
     * 后台商品分页（管理员）：查询全部商品（含待审核 / 已拒绝），可按审核状态筛选
     * @param pageDto 分页参数（默认第1页、每页10条）
     * @param auditStatus 审核状态筛选（可选，空则查全部：0待审核 1审核通过 2审核拒绝）
     * @return 分页结果（按创建时间倒序）
     */
    PageResult<IntangibleGoods> pageAdminGoods(PageDto pageDto, Integer auditStatus);

    /**
     * 我发布的商品分页（非遗人）：查询本人发布的全部状态商品（含待审核 / 已上架 / 已打回）
     * 已打回商品的打回原因与审核时间从 Redis 审核记录回填，供修改后重新提交
     * @param pageDto 分页参数（默认第1页、每页10条）
     * @param publisherId 发布人（当前登录非遗人）ID
     * @param status 商品状态筛选（可选，空则查全部：0待审核 1上架 2打回）
     * @return 分页结果（按创建时间倒序）
     */
    PageResult<IntangibleGoods> pageMyGoods(PageDto pageDto, Long publisherId, Integer status);

    /**
     * 扣减商品库存（创建订单时调用）：UPDATE 原子扣减防止并发超卖，库存不足或商品不存在时抛出业务异常；
     * 扣减后库存为 0 的已上架商品自动置为已下架（3），前台不再展示与搜索
     * @param goodsId 商品ID
     * @param num 扣减数量
     */
    void deductStock(Long goodsId, int num);

    /**
     * 恢复商品库存（订单退款成功时调用）：UPDATE 增量恢复，商品不存在/已删除时静默忽略
     * @param goodsId 商品ID
     * @param num 恢复数量
     */
    void restoreStock(Long goodsId, int num);

    /**
     * 管理端首页数据统计：商品总量与状态分布（待审核·上架·打回·下架）
     * + 订单总量与状态分布、成交金额（已支付口径）、近7天订单趋势
     */
    AdminStatsVO adminStats();
}
