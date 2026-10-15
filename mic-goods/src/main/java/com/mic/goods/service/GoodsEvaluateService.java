package com.mic.goods.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mic.common.model.dto.PageDto;
import com.mic.common.result.PageResult;
import com.mic.goods.model.dto.EvaluateReplyDTO;
import com.mic.goods.model.dto.EvaluateSaveDTO;
import com.mic.goods.model.entity.GoodsEvaluate;

/**
 * 商品评价服务接口
 */
public interface GoodsEvaluateService extends IService<GoodsEvaluate> {

    /**
     * 提交商品评价（登录用户）：仅 3已收货 订单可评价（订单归属本人、商品一致），同一订单仅可评价一次；
     * 评价成功后订单自动流转 6已评价 →（自动）→ 7已完成
     * @param dto 评价信息（订单ID、商品ID、内容、评分）
     * @param userId 当前登录用户ID（网关透传）
     */
    void saveEvaluate(EvaluateSaveDTO dto, Long userId);

    /**
     * 商品评价分页列表（免登录）：按创建时间倒序，每条附评价人昵称与追问/回复列表（含回复人昵称、卖家标识）
     * @param pageDto 分页参数（默认第1页、每页10条）
     * @param goodsId 商品ID筛选（可选，空则查全部评价）
     * @return 分页结果
     */
    PageResult<GoodsEvaluate> pageEvaluates(PageDto pageDto, Long goodsId);

    /**
     * 删除商品评价（登录用户）：仅可删除本人发布的评价（不能删除别人的评价），
     * 级联（逻辑）删除该评价下的全部追问/回复
     * @param evaluateId 评价ID
     * @param userId 当前登录用户ID（网关透传）
     */
    void deleteEvaluate(Long evaluateId, Long userId);

    /**
     * 追问/回复商品评价（登录用户）：任何登录用户可追问他人评价，商品卖家可回复买家评价（同一接口）
     * @param dto 追问/回复信息（评价ID、内容）
     * @param userId 当前登录用户ID（网关透传）
     */
    void replyEvaluate(EvaluateReplyDTO dto, Long userId);

    /**
     * 删除追问/回复（登录用户）：仅可删除本人发布的追问/回复
     * @param replyId 追问/回复ID
     * @param userId 当前登录用户ID（网关透传）
     */
    void deleteReply(Long replyId, Long userId);
}
