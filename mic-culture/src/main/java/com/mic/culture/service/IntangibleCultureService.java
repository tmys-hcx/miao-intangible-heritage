package com.mic.culture.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mic.common.model.dto.PageDto;
import com.mic.common.result.PageResult;
import com.mic.culture.model.dto.CultureSaveDTO;
import com.mic.culture.model.entity.IntangibleCulture;
import com.mic.culture.model.vo.AdminStatsVO;

import java.util.List;

/**
 * 非遗文化资料服务接口
 */
public interface IntangibleCultureService extends IService<IntangibleCulture> {

    /** 前台公开分页：仅展示已发布资料，支持关键词检索与最新 / 最热 / 排版推荐（custom）排序 */
    PageResult<IntangibleCulture> pagePublished(PageDto pageDto, String keyword, String sortBy);

    /** 前台详情：已发布资料公开可见且浏览量自增 +1；未发布资料（待审核 / 已驳回）仅发布者本人可预览 */
    IntangibleCulture getDetail(Long id, Long userId);

    /** 我的发布分页：非遗人查看本人全部资料（含待审核 / 已驳回，驳回资料携带驳回原因） */
    PageResult<IntangibleCulture> pageMy(Long userId, Integer role, PageDto pageDto);

    /** 后台资料分页（仅管理员）：全部资料（含待审核 / 已驳回），可按审核状态筛选 */
    PageResult<IntangibleCulture> pageAdminCulture(PageDto pageDto, Integer auditStatus);

    /** 发布非遗资料（仅非遗人，提交后进入待审核），返回新资料ID */
    Long publish(Long userId, Integer role, CultureSaveDTO dto);

    /** 修改本人发布的非遗资料（仅非遗人，修改后重新进入待审核） */
    void updateCulture(Long id, Long userId, Integer role, CultureSaveDTO dto);

    /** 逻辑删除本人发布的非遗资料（仅非遗人） */
    void removeCulture(Long id, Long userId, Integer role);

    /** 审核非遗资料（仅管理员）：1审核通过（发布，前台可见） / 2审核拒绝（驳回，需填写不合格原因） */
    void auditCulture(Long id, Integer auditStatus, String remark, Long auditorId);

    /** 保存前台默认展示排版（仅管理员）：ids 为拖拽后的文章ID有序列表，数组顺序即展示顺序 */
    void saveSortOrder(List<Long> ids);

    /** 管理端首页数据统计：资料总数 / 状态分布（待审核·已发布·已驳回）/ 总浏览量 / 近7天发布趋势 */
    AdminStatsVO adminStats();
}
