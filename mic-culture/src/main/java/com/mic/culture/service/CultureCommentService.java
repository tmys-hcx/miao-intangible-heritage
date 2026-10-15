package com.mic.culture.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mic.common.model.dto.PageDto;
import com.mic.common.result.PageResult;
import com.mic.culture.model.dto.CultureCommentDTO;
import com.mic.culture.model.entity.CultureComment;
import com.mic.culture.model.vo.CultureCommentVO;

/**
 * 非遗资料评论服务接口
 */
public interface CultureCommentService extends IService<CultureComment> {

    /** 评论分页：顶级评论（含二级回复），联表回填昵称 / 头像与作者标注 */
    PageResult<CultureCommentVO> pageByCulture(Long cultureId, PageDto pageDto);

    /** 发表评论 / 回复（仅已发布资料），返回新评论VO供前端插入展示 */
    CultureCommentVO publish(Long userId, CultureCommentDTO dto);

    /** 删除评论：评论作者本人或资料发布者；删除顶级评论时连带删除其二级回复 */
    void remove(Long userId, Long id);
}
