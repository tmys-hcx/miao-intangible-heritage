package com.mic.user.service;

import com.mic.common.model.dto.PageDto;
import com.mic.common.result.PageResult;
import com.mic.user.model.vo.SysLogVO;

/**
 * 系统日志服务：管理端日志分页查询 + 业务关键操作写入日志
 */
public interface SysLogService {

    /**
     * 系统日志分页（管理端）：按操作时间倒序，支持操作模块 / 操作人账号模糊搜索
     * @param pageDto 分页参数（兼容 pageNum / pageSize）
     * @param keyword 搜索关键词（null/空=不筛选；匹配操作模块或操作人账号）
     */
    PageResult<SysLogVO> pageLogs(PageDto pageDto, String keyword);

    /**
     * 写入一条操作日志（业务关键操作成功后调用，IP 由实现自动从当前请求上下文获取）。
     * 容错约定：日志写入失败不影响主业务流程（内部捕获仅记录告警）
     * @param operateUserId 操作人ID（登录用户 / 管理员）
     * @param operateModule 操作模块（如：用户登录 / 用户注册 / 用户管理 / 申请审核）
     * @param operateContent 操作内容（具体动作与对象描述）
     */
    void record(Long operateUserId, String operateModule, String operateContent);
}
