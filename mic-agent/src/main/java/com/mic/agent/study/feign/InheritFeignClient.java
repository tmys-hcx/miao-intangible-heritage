package com.mic.agent.study.feign;

import com.mic.agent.study.model.dto.ForumPostDTO;
import com.mic.agent.study.model.dto.ForumPostItemDTO;
import com.mic.agent.study.model.dto.ForumReplyItemDTO;
import com.mic.agent.study.model.dto.InheritItemDTO;
import com.mic.common.model.feign.FeignConstant;
import com.mic.common.model.vo.InheritSearchVO;
import com.mic.common.result.PageResult;
import com.mic.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 传承预约服务Feign客户端（服务名 mic-inherit-reserve，经Nacos服务发现调用）：
 * 「传承与学习」Agent 三个功能（资料检索问答 / 学习内容推荐 / 论坛辅助答疑）统一经此客户端
 * 读取访谈资料、知识窗口、视频简介与论坛帖子数据；被调服务统一返回 Result 包装，调用方据 code 判定业务结果
 */
@FeignClient(value = FeignConstant.SERVICE_INHERIT)
public interface InheritFeignClient {

    /** 资料检索（Agent 专用接口）：按关键词聚合检索访谈资料 / 知识窗口 / 视频简介（仅已审核视频），返回纯文本片段 */
    @GetMapping("/api/inherit/search")
    Result<List<InheritSearchVO>> search(@RequestParam("keyword") String keyword,
                                         @RequestParam("limit") Integer limit);

    /** 传承信息详情（公开）：type=1视频学习 3访谈资料 4知识窗口，供推荐功能读取当前浏览条目的标题与正文/简介 */
    @GetMapping("/api/inherit/{id}")
    Result<InheritItemDTO> detail(@PathVariable("id") Long id,
                                  @RequestParam("type") Integer type);

    /** 论坛帖子详情（已通过帖子公开）：供辅助答疑读取帖子标题与正文 */
    @GetMapping("/api/inherit/forum/{id}")
    Result<ForumPostDTO> forumDetail(@PathVariable("id") Long id);

    /** 论坛公开帖子分页（关键词模糊匹配标题 / 正文，仅已通过帖子）：供论坛智能总结读取检索命中的帖子 */
    @GetMapping("/api/inherit/forum/page")
    Result<PageResult<ForumPostItemDTO>> forumPage(@RequestParam("pageNum") Integer pageNum,
                                                   @RequestParam("pageSize") Integer pageSize,
                                                   @RequestParam("keyword") String keyword);

    /** 论坛评论分页（公开）：供论坛智能总结读取帖子顶级评论（二级回复嵌在 replies） */
    @GetMapping("/api/inherit/forum/reply/page")
    Result<PageResult<ForumReplyItemDTO>> forumReplyPage(@RequestParam("postId") Long postId,
                                                         @RequestParam("pageNum") Integer pageNum,
                                                         @RequestParam("pageSize") Integer pageSize);
}
