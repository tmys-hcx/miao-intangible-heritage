package com.mic.goods.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mic.goods.model.entity.GoodsEvaluateReply;

import java.util.List;

/**
 * 商品评价追问/回复服务接口
 */
public interface GoodsEvaluateReplyService extends IService<GoodsEvaluateReply> {

    /**
     * 按评价ID集合批量查询追问/回复（含回复人昵称），按创建时间正序
     * @param evaluateIds 评价ID集合（非空，调用方保证）
     */
    List<GoodsEvaluateReply> listByEvaluateIds(List<Long> evaluateIds);
}
