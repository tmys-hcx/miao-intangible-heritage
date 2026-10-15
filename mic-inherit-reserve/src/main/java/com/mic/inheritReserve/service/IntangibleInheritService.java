package com.mic.inheritReserve.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mic.common.model.dto.PageDto;
import com.mic.common.model.vo.InheritSearchVO;
import com.mic.common.result.PageResult;
import com.mic.inheritReserve.model.dto.InheritSaveDTO;
import com.mic.inheritReserve.model.dto.VideoPublishDTO;
import com.mic.inheritReserve.model.entity.IntangibleInherit;
import com.mic.inheritReserve.model.entity.IntangibleVideo;
import com.mic.inheritReserve.model.vo.AdminStatsVO;
import com.mic.inheritReserve.model.vo.MyWatchVO;

import java.util.List;

/**
 * 非遗传承信息服务
 */
public interface IntangibleInheritService extends IService<IntangibleInherit> {

    /**
     * 公开传承信息分页：视频学习仅返回已审核（audit_status=1）数据；访谈资料 / 知识窗口为管理员直发内容
     *
     * @param pageDto  分页参数
     * @param infoType 信息类型（1视频学习 3访谈资料 4知识窗口），为空则按视频学习处理
     * @return 分页结果（records 为对应板块实体：视频学习 IntangibleVideo / 访谈资料 IntangibleInterview / 知识窗口 IntangibleKnowledge）
     */
    PageResult<?> pagePublic(PageDto pageDto, Integer infoType);

    /**
     * 公开详情：视频学习仅返回已审核（audit_status=1）数据
     *
     * @param id   内容ID
     * @param type 信息类型（1视频学习 3访谈资料 4知识窗口），为空时依次在三个板块中查找（兼容历史链接）
     * @return 传承信息（不存在或未发布抛出 400）
     */
    Object getPublicDetail(Long id, Integer type);

    /**
     * 管理端分页：按信息类型分发三张拆分局表（3访谈资料 / 4知识窗口 / 其余按1视频学习处理）；
     * 支持标题关键词筛选，审核状态仅视频学习板块生效；发布人账号由 SQL LEFT JOIN 填充
     *
     * @param pageDto     分页参数
     * @param infoType    信息类型（1视频学习 3访谈资料 4知识窗口），为空则按视频学习处理
     * @param keyword     标题关键词（模糊匹配），为空则不过滤
     * @param auditStatus 审核状态（0待审核 1已通过 2已驳回），仅视频学习板块生效
     * @return 分页结果（records 为对应板块实体，瞬态 infoType 已回填）
     */
    PageResult<?> pageAdmin(PageDto pageDto, Integer infoType, String keyword, Integer auditStatus);

    /**
     * 新增传承信息（管理员维护）：按 infoType 写入访谈资料 / 知识窗口表，直发内容前台即刻可见
     *
     * @param dto    保存入参（infoType：3访谈资料 4知识窗口）
     * @param userId 管理员ID（记录发布人）
     * @return 新记录ID
     */
    Long saveInherit(InheritSaveDTO dto, Long userId);

    /**
     * 编辑传承信息（管理员维护）：按 dto.infoType 定位所属拆分局表，仅更新标题、正文与配图
     *
     * @param id  传承信息ID
     * @param dto 保存入参（infoType 须与记录原类型一致，不支持修改信息类型）
     */
    void updateInherit(Long id, InheritSaveDTO dto);

    /**
     * 删除传承信息（管理员维护，逻辑删除）
     *
     * @param id   传承信息ID
     * @param type 信息类型（1视频学习 3访谈资料 4知识窗口），区分三张拆分局表的同ID记录
     */
    void removeInherit(Long id, Integer type);

    /**
     * 发布学习视频（仅非遗人）：提交后进入待审核（audit_status=0），管理员审核通过后前台展示
     *
     * @param dto    发布入参
     * @param userId 发布人ID（非遗人）
     * @param role   当前用户角色（网关透传），非1（非遗人）拒绝发布
     * @return 新记录ID
     */
    Long publishVideo(VideoPublishDTO dto, Long userId, Integer role);

    /**
     * 学习视频审核（管理员）：基于独立视频表 intangible_video，通过（1，前台可见）或驳回（2，需填写驳回原因）
     *
     * @param id          学习视频ID
     * @param auditStatus 审核结论（1通过 2驳回）
     * @param remark      审核备注（驳回时必填）
     * @param auditorId   审核管理员ID
     */
    void auditVideo(Long id, Integer auditStatus, String remark, Long auditorId);

    /**
     * 编辑我发布的学习视频（个人中心，仅作者本人）：编辑后重新进入待审核（audit_status=0）
     * 并清空上一轮审核结果，管理员重审通过后前台展示
     *
     * @param id     学习视频ID
     * @param dto    编辑入参（标题 / 视频链接 / 封面 / 时长 / 简介）
     * @param userId 当前用户ID（须为该视频发布人，否则拒绝）
     */
    void updateMyVideo(Long id, VideoPublishDTO dto, Long userId);

    /**
     * 删除我发布的学习视频（个人中心，仅作者本人，逻辑删除）：删除后前台与个人中心均不再展示，
     * 关联学习记录随视频失效自动隐藏
     *
     * @param id     学习视频ID
     * @param userId 当前用户ID（须为该视频发布人，否则拒绝）
     */
    void removeMyVideo(Long id, Long userId);

    /**
     * 我发布的视频分页（个人中心，仅非遗人）：本人发布的学习视频，含全部审核状态
     * （0待审核 1已通过 2已驳回），按发布时间倒序
     *
     * @param pageDto 分页参数
     * @param userId  当前用户ID（发布人）
     * @return 分页结果（records 为 IntangibleVideo）
     */
    PageResult<IntangibleVideo> pageMyPublish(PageDto pageDto, Long userId);

    /**
     * 上报完整观看（个人中心，非遗人 / 普通用户均可）：视频完整播放结束后调用；
     * 重复完整观看仅刷新最近观看时间，同一用户同一视频仅保留一条记录
     *
     * @param videoId 学习视频ID（须存在且已审核通过，否则抛出 400）
     * @param userId  当前用户ID
     */
    void reportWatch(Long videoId, Long userId);

    /**
     * 我学习的视频分页（个人中心，非遗人 / 普通用户均可）：仅已完整观看且视频仍有效
     * 并审核通过的记录，按最近观看时间倒序
     *
     * @param pageDto 分页参数
     * @param userId  当前用户ID
     * @return 分页结果（records 为 MyWatchVO）
     */
    PageResult<MyWatchVO> pageMyWatch(PageDto pageDto, Long userId);

    /**
     * 资料检索（智能助手 Agent 内部调用）：按关键词在【访谈资料 / 知识窗口 / 学习视频简介】中
     * 聚合检索并返回纯文本片段，视频仅检索已审核（audit_status=1）数据
     *
     * @param keyword 检索关键词（支持空格分隔多个词，命中任一即匹配；空白返回空列表）
     * @param limit   每类资料返回条数上限（为空默认3，上限10）
     * @return 三类资料的匹配结果（含来源类型、标题与文本片段）
     */
    List<InheritSearchVO> searchMaterials(String keyword, Integer limit);

    /**
     * 管理端首页数据统计：论坛帖子总量 / 待审核帖子数 / 回复总量 / 学习视频总量 / 待审核视频数
     */
    AdminStatsVO adminStats();
}
