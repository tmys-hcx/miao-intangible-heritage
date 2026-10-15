package com.mic.agent.study.service;

import com.mic.common.model.vo.InheritSearchVO;

import java.util.List;

/**
 * 传承与学习 Agent 业务服务：承载「学习内容推荐」与「论坛辅助答疑」两条链路
 * （资料检索问答由 StudyAssistant 对话 + MaterialSearchTool 工具完成，不经本服务）
 */
public interface StudyAssistService {

    /**
     * 学习内容推荐：根据用户当前浏览的视频 / 资料检索资料库，
     * 返回配套学习视频 / 访谈实录推荐列表（已剔除当前条目本身；无相关内容时返回空列表）
     */
    List<InheritSearchVO> recommend(Integer infoType, Long id);

    /**
     * 论坛辅助答疑：读取帖子标题 / 正文并检索知识库资料，生成可直接发布的回答草稿文本
     */
    String generateForumDraft(Long postId);

    /**
     * 论坛智能总结：按关键词检索论坛帖子并读取帖内评论，生成论坛搜索框下方展示的智能总结
     * （无匹配帖子返回空串，由前端隐藏总结区；生成失败抛业务异常，前端静默忽略）
     */
    String summarizeForum(String keyword);
}
