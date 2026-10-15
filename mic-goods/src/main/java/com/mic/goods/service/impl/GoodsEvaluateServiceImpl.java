package com.mic.goods.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mic.common.exception.BusinessException;
import com.mic.common.model.dto.PageDto;
import com.mic.common.result.PageResult;
import com.mic.common.result.ResultCodeEnum;
import com.mic.goods.mapper.GoodsEvaluateMapper;
import com.mic.goods.model.dto.EvaluateReplyDTO;
import com.mic.goods.model.dto.EvaluateSaveDTO;
import com.mic.goods.model.entity.GoodsEvaluate;
import com.mic.goods.model.entity.GoodsEvaluateReply;
import com.mic.goods.model.entity.GoodsOrder;
import com.mic.goods.model.entity.IntangibleGoods;
import com.mic.goods.service.GoodsEvaluateReplyService;
import com.mic.goods.service.GoodsEvaluateService;
import com.mic.goods.service.GoodsOrderService;
import com.mic.goods.service.IntangibleGoodsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 商品评价服务实现
 * 评价流转：仅 3已收货 订单可评价，提交成功后订单自动流转 6已评价 →（自动）→ 7已完成
 * 互动规则（两级对话结构）：仅可删除本人发布的评价（级联删除其追问/回复）；任何登录用户可追问他人评价；
 *          任何登录用户可回复追问（追问者与被追问者可双向来回回复）；仅可删除本人发布的追问/回复
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoodsEvaluateServiceImpl extends ServiceImpl<GoodsEvaluateMapper, GoodsEvaluate> implements GoodsEvaluateService {

    /** 订单状态：3已收货（仅已收货订单可评价，评价后订单自动流转 6已评价 → 7已完成） */
    private static final int ORDER_STATUS_RECEIVED = 3;

    /** 订单服务：校验评价关联订单的存在性、归属与收货状态；评价成功后触发订单自动完成 */
    private final GoodsOrderService goodsOrderService;

    /** 追问/回复服务：评价分页批量回填回复列表、保存/删除追问回复 */
    private final GoodsEvaluateReplyService goodsEvaluateReplyService;

    /** 商品服务：评价分页批量回填商品发布人，用于标记卖家回复 */
    private final IntangibleGoodsService intangibleGoodsService;

    /**
     * 提交商品评价（登录用户）：仅已收货订单可评价，同一订单仅可评价一次；评价成功后订单自动流转为已完成
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveEvaluate(EvaluateSaveDTO dto, Long userId) {
        // 校验订单：存在、归属本人、已收货、订单商品与评价商品一致
        GoodsOrder order = goodsOrderService.getById(dto.getOrderId());
        if (order == null) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN, "仅可评价本人订单");
        }
        if (order.getOrderStatus() != ORDER_STATUS_RECEIVED) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "仅已收货订单可评价，请先确认收货");
        }
        if (!goodsOrderService.orderContainsGoods(dto.getOrderId(), dto.getGoodsId())) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "订单与商品不一致");
        }
        // 防重复：同一订单仅可评价一次
        if (lambdaQuery().eq(GoodsEvaluate::getOrderId, dto.getOrderId()).exists()) {
            throw new BusinessException(ResultCodeEnum.REPEAT_SUBMIT, "该订单已评价，请勿重复提交");
        }

        GoodsEvaluate evaluate = new GoodsEvaluate();
        evaluate.setOrderId(dto.getOrderId());
        evaluate.setGoodsId(dto.getGoodsId());
        evaluate.setUserId(userId);
        evaluate.setEvaluateContent(dto.getEvaluateContent());
        evaluate.setScore(dto.getScore());
        save(evaluate);
        // 评价成功后订单自动流转：3已收货 → 6已评价 →（自动）→ 7已完成，并同步卖家订单快照
        goodsOrderService.autoFinishAfterEvaluate(dto.getOrderId());
        log.info("提交商品评价成功（订单自动完成）：evaluateId={}, orderId={}, goodsId={}, userId={}, score={}",
                evaluate.getId(), dto.getOrderId(), dto.getGoodsId(), userId, dto.getScore());
    }

    /**
     * 商品评价分页列表（免登录）：自定义 SQL 分页 LEFT JOIN sys_user 填充评价人昵称，
     * 并按评价批量回填追问/回复列表（含回复人昵称与卖家标识）
     */
    @Override
    public PageResult<GoodsEvaluate> pageEvaluates(PageDto pageDto, Long goodsId) {
        Page<GoodsEvaluate> page = new Page<>(pageDto.getCurrent(), pageDto.getSize());
        // 自定义 SQL 分页（已手动过滤 is_deleted）；goodsId 为空查全部评价
        baseMapper.selectEvaluatePage(page, goodsId);
        fillReplies(page.getRecords());
        return PageResult.of(page);
    }

    /**
     * 批量回填评价的追问/回复列表：一次查询回复与商品发布人，避免逐条循环查库
     * 两级组装：parent_reply_id 为空 = 一级追问（挂评价下）；非空 = 二级回复（挂对应追问的 children 下）
     * 卖家标识：回复人 user_id = 评价对应商品的 publisher_id 时为卖家回复
     */
    private void fillReplies(List<GoodsEvaluate> evaluates) {
        if (evaluates == null || evaluates.isEmpty()) {
            return;
        }
        List<Long> evaluateIds = evaluates.stream().map(GoodsEvaluate::getId).toList();
        // 批量查询追问/回复（含回复人与被回复人昵称），按评价ID分组
        List<GoodsEvaluateReply> replies = goodsEvaluateReplyService.listByEvaluateIds(evaluateIds);
        Map<Long, List<GoodsEvaluateReply>> replyMap = replies.stream()
                .collect(Collectors.groupingBy(GoodsEvaluateReply::getEvaluateId));
        // 批量查询评价对应商品，构建 goodsId → 发布人ID 映射（标记卖家回复）
        Set<Long> goodsIds = evaluates.stream().map(GoodsEvaluate::getGoodsId).collect(Collectors.toSet());
        Map<Long, Long> publisherMap = intangibleGoodsService.listByIds(goodsIds).stream()
                .collect(Collectors.toMap(IntangibleGoods::getId, IntangibleGoods::getPublisherId, (a, b) -> a));

        for (GoodsEvaluate evaluate : evaluates) {
            Long publisherId = publisherMap.get(evaluate.getGoodsId());
            List<GoodsEvaluateReply> allReplies = replyMap.getOrDefault(evaluate.getId(), Collections.emptyList());
            // 层级拆分：一级追问列表 + 追问ID → 二级回复列表映射
            List<GoodsEvaluateReply> topReplies = new ArrayList<>();
            Map<Long, List<GoodsEvaluateReply>> childrenMap = new HashMap<>();
            for (GoodsEvaluateReply reply : allReplies) {
                reply.setSeller(publisherId != null && publisherId.equals(reply.getUserId()));
                if (reply.getParentReplyId() == null) {
                    topReplies.add(reply);
                } else {
                    childrenMap.computeIfAbsent(reply.getParentReplyId(), key -> new ArrayList<>()).add(reply);
                }
            }
            for (GoodsEvaluateReply top : topReplies) {
                top.setChildren(childrenMap.getOrDefault(top.getId(), Collections.emptyList()));
            }
            evaluate.setReplies(topReplies);
        }
    }

    /**
     * 删除商品评价（登录用户）：仅可删除本人发布的评价，级联（逻辑）删除该评价下的全部追问/回复
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteEvaluate(Long evaluateId, Long userId) {
        GoodsEvaluate evaluate = getById(evaluateId);
        if (evaluate == null) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "评价不存在或已删除");
        }
        // 归属校验：仅可删除本人发布的评价（不能删除别人的评价）
        if (!evaluate.getUserId().equals(userId)) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN, "仅可删除本人发布的评价");
        }
        removeById(evaluateId);
        // 级联逻辑删除该评价下的全部追问/回复（MP 自动置 is_deleted=1）
        goodsEvaluateReplyService.lambdaUpdate()
                .eq(GoodsEvaluateReply::getEvaluateId, evaluateId)
                .remove();
        log.info("删除商品评价（级联删除追问/回复）：evaluateId={}, userId={}", evaluateId, userId);
    }

    /**
     * 追问/回复商品评价（登录用户，双向对话）：
     * parentReplyId 为空：任何登录用户可追问他人评价；
     * parentReplyId 非空：任何登录用户可回复该追问（追问者与被追问者来回对话，回复的回复仍挂顶层追问）
     */
    @Override
    public void replyEvaluate(EvaluateReplyDTO dto, Long userId) {
        // 校验评价存在且未删除（逻辑删除自动过滤）
        if (getById(dto.getEvaluateId()) == null) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "评价不存在或已删除");
        }
        GoodsEvaluateReply reply = new GoodsEvaluateReply();
        reply.setEvaluateId(dto.getEvaluateId());
        reply.setUserId(userId);
        reply.setReplyContent(dto.getReplyContent());
        if (dto.getParentReplyId() != null) {
            // 回复追问：校验父级存在、归属同一评价、且必须为一级追问（仅两级结构）
            GoodsEvaluateReply parent = goodsEvaluateReplyService.getById(dto.getParentReplyId());
            if (parent == null || !parent.getEvaluateId().equals(dto.getEvaluateId())) {
                throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "追问不存在或已删除");
            }
            if (parent.getParentReplyId() != null) {
                throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "仅可回复一级追问");
            }
            reply.setParentReplyId(parent.getId());
            // 被回复人：优先取入参，为空时兜底为父级追问作者（保证对话对象可展示）
            reply.setReplyToUserId(dto.getReplyToUserId() != null ? dto.getReplyToUserId() : parent.getUserId());
        }
        goodsEvaluateReplyService.save(reply);
        log.info("发布评价追问/回复：replyId={}, evaluateId={}, parentReplyId={}, replyToUserId={}, userId={}",
                reply.getId(), dto.getEvaluateId(), reply.getParentReplyId(), reply.getReplyToUserId(), userId);
    }

    /**
     * 删除追问/回复（登录用户）：仅可删除本人发布的追问/回复；
     * 删除一级追问时级联（逻辑）删除其下的全部二级回复
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteReply(Long replyId, Long userId) {
        GoodsEvaluateReply reply = goodsEvaluateReplyService.getById(replyId);
        if (reply == null) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "追问不存在或已删除");
        }
        if (!reply.getUserId().equals(userId)) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN, "仅可删除本人发布的追问/回复");
        }
        goodsEvaluateReplyService.removeById(replyId);
        if (reply.getParentReplyId() == null) {
            // 一级追问：级联逻辑删除其下全部二级回复（MP 自动置 is_deleted=1）
            goodsEvaluateReplyService.lambdaUpdate()
                    .eq(GoodsEvaluateReply::getParentReplyId, replyId)
                    .remove();
        }
        log.info("删除评价追问/回复：replyId={}, userId={}", replyId, userId);
    }
}
