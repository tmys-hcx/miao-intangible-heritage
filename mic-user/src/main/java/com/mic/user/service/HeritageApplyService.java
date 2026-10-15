package com.mic.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mic.common.model.dto.PageDto;
import com.mic.common.result.PageResult;
import com.mic.user.model.dto.HeritageApplyDTO;
import com.mic.user.model.dto.HeritageAuditDTO;
import com.mic.user.model.entity.HeritageApply;
import com.mic.user.model.vo.HeritageApplyVO;

/**
 * 非遗人申请服务接口：提交申请 / 我的最新申请 / 管理端分页 / 申请审核
 */
public interface HeritageApplyService extends IService<HeritageApply> {

    /** 提交非遗人申请（普通用户）：要求个人信息完善、无审核中的申请 */
    void submitApply(Long userId, HeritageApplyDTO dto);

    /** 我的最新申请（用户端）：无申请记录时返回 null */
    HeritageApplyVO getMyLatestApply(Long userId);

    /** 申请分页列表（管理员）：可按审核状态筛选，含申请人基础信息 */
    PageResult<HeritageApplyVO> pageApplies(PageDto pageDto, Integer auditStatus);

    /** 申请审核（管理员）：1审核通过（申请人转为非遗人） / 2审核驳回（remark必填） */
    void auditApply(Long applyId, HeritageAuditDTO dto, Long auditorId);
}
