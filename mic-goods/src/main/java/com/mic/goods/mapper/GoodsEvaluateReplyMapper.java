package com.mic.goods.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mic.goods.model.entity.GoodsEvaluateReply;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 商品评价追问/回复表Mapper
 */
public interface GoodsEvaluateReplyMapper extends BaseMapper<GoodsEvaluateReply> {

    /**
     * 按评价ID集合批量查询追问/回复（含回复人与被回复人昵称）：
     * LEFT JOIN sys_user（同库）填充瞬态字段 userNickname / replyToNickname，按创建时间正序（对话顺序）；
     * 返回一级追问与二级回复全量（层级由服务端按 parentReplyId 组装）
     * @param evaluateIds 评价ID集合（非空，调用方保证）
     */
    List<GoodsEvaluateReply> selectByEvaluateIds(@Param("evaluateIds") List<Long> evaluateIds);
}
