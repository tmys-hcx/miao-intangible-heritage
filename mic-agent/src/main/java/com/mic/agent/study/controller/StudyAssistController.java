package com.mic.agent.study.controller;

import com.mic.agent.study.model.dto.ForumDraftDTO;
import com.mic.agent.study.model.dto.ForumSummaryDTO;
import com.mic.agent.study.model.dto.StudyRecommendDTO;
import com.mic.agent.study.service.StudyAssistService;
import com.mic.common.model.vo.InheritSearchVO;
import com.mic.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 传承与学习助手业务接口（学习内容推荐 / 论坛辅助答疑）：
 * - 学习内容推荐：详情页根据当前浏览的视频 / 资料，自动检索资料库返回配套学习视频、访谈实录（游客可用，网关白名单）；
 * - 论坛辅助答疑：论坛详情页为提问帖生成回答草稿（Agent 读取知识库资料），用户修改后自行发布到论坛（需登录，由网关鉴权）。
 * 资料检索问答（对话形态）见 ChatController 的 study 主题（/api/agent/chat）
 */
@Tag(name = "传承与学习助手接口")
@RestController
@RequestMapping("/api/agent/study")
@RequiredArgsConstructor
public class StudyAssistController {

    private final StudyAssistService studyAssistService;

    @Operation(summary = "检索资料库，返回配套学习视频 / 访谈实录（最学习内容推荐：按当前浏览的信息类型与ID多 4 条）")
    @PostMapping("/recommend")
    public Result<List<InheritSearchVO>> recommend(@Valid @RequestBody StudyRecommendDTO dto) {
        return Result.ok(studyAssistService.recommend(dto.getInfoType(), dto.getId()));
    }

    @Operation(summary = "论坛回答草稿：读取帖子与知识库资料生成回答草稿，供用户修改后直接发布到论坛")
    @PostMapping("/forum/draft")
    public Result<String> forumDraft(@Valid @RequestBody ForumDraftDTO dto) {
        return Result.ok(studyAssistService.generateForumDraft(dto.getPostId()));
    }

    @Operation(summary = "论坛智能总结：按关键词检索帖子与帖内评论生成智能总结，展示在论坛搜索框下方")
    @PostMapping("/forum/summary")
    public Result<String> forumSummary(@Valid @RequestBody ForumSummaryDTO dto) {
        return Result.ok(studyAssistService.summarizeForum(dto.getKeyword()));
    }
}
