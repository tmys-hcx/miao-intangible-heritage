package com.mic.goods.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mic.goods.mapper.GoodsEvaluateReplyMapper;
import com.mic.goods.model.entity.GoodsEvaluateReply;
import com.mic.goods.service.GoodsEvaluateReplyService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 商品评价追问/回复服务实现
 */
@Service
public class GoodsEvaluateReplyServiceImpl extends ServiceImpl<GoodsEvaluateReplyMapper, GoodsEvaluateReply> implements GoodsEvaluateReplyService {

    /**
     * 按评价ID集合批量查询追问/回复（含回复人昵称），按创建时间正序
     */
    @Override
    public List<GoodsEvaluateReply> listByEvaluateIds(List<Long> evaluateIds) {
        return baseMapper.selectByEvaluateIds(evaluateIds);
    }
}
