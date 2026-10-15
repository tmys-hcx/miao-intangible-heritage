package com.mic.user.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mic.common.exception.BusinessException;
import com.mic.common.model.dto.PageDto;
import com.mic.common.result.PageResult;
import com.mic.common.result.ResultCodeEnum;
import com.mic.user.mapper.HeritageApplyMapper;
import com.mic.user.mapper.UserMapper;
import com.mic.user.model.dto.HeritageApplyDTO;
import com.mic.user.model.dto.HeritageAuditDTO;
import com.mic.user.model.entity.HeritageApply;
import com.mic.user.model.entity.SysUser;
import com.mic.user.model.vo.HeritageApplyVO;
import com.mic.user.service.HeritageApplyService;
import com.mic.user.service.SysLogService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 非遗人申请服务实现
 * 流程：普通用户（role=2）在个人中心提交申请（要求手机号、身份信息完善）→ 管理员审核
 * - 审核通过：sys_user.role 置为1（非遗人），JWT 内角色随旧 token 过期，重新登录后生效
 * - 审核驳回：记录驳回原因，用户可在个人中心查看并重新申请
 */
@Slf4j
@Service
public class HeritageApplyServiceImpl extends ServiceImpl<HeritageApplyMapper, HeritageApply> implements HeritageApplyService {

    /** 角色：1非遗人、2普通用户 */
    private static final int ROLE_HERITAGE = 1;
    private static final int ROLE_NORMAL = 2;

    /** 账号状态：1正常 */
    private static final int STATUS_NORMAL = 1;

    /** 审核状态：0待审核，1已通过，2已驳回 */
    private static final int AUDIT_PENDING = 0;
    private static final int AUDIT_PASS = 1;
    private static final int AUDIT_REJECT = 2;

    @Resource
    private UserMapper userMapper;

    @Resource
    private SysLogService sysLogService;

    @Override
    public void submitApply(Long userId, HeritageApplyDTO dto) {
        // 1.申请资格校验：仅普通用户可申请成为非遗人
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCodeEnum.NOT_LOGIN, "登录用户不存在或已被删除");
        }
        if (user.getRole() != null && user.getRole() == ROLE_HERITAGE) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "您已是非遗人，无需重复申请");
        }
        if (user.getRole() == null || user.getRole() != ROLE_NORMAL) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "当前账号不支持申请非遗人");
        }
        // 2.个人信息完善校验：手机号、身份信息为申请前提（前端引导完善，服务端兜底）
        if (!StringUtils.hasText(user.getPhone()) || !StringUtils.hasText(user.getIdentityInfo())) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "请先在个人中心完善个人信息（手机号、身份信息）后再提交申请");
        }
        // 3.防重复提交：存在待审核申请时不允许再次提交
        if (lambdaQuery().eq(HeritageApply::getUserId, userId)
                .eq(HeritageApply::getAuditStatus, AUDIT_PENDING)
                .exists()) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "您有正在审核中的申请，请耐心等待审核结果");
        }
        // 4.落库申请内容（证明材料以 JSON 数组字符串存储）
        HeritageApply apply = new HeritageApply();
        apply.setUserId(userId);
        apply.setIdentityInfo(dto.getIdentityInfo().trim());
        apply.setApplyReason(dto.getApplyReason().trim());
        apply.setMaterialUrls(JSONUtil.toJsonStr(dto.getMaterialUrls()));
        apply.setAuditStatus(AUDIT_PENDING);
        save(apply);
        log.info("非遗人申请提交成功：userId={}, account={}, applyId={}", userId, user.getAccount(), apply.getId());
        // 系统日志：记录用户提交非遗人申请（操作人为申请人本人）
        sysLogService.record(userId, "申请审核",
                "提交非遗人申请（账号：" + user.getAccount() + "，申请ID：" + apply.getId() + "）");
    }

    @Override
    public HeritageApplyVO getMyLatestApply(Long userId) {
        HeritageApply apply = lambdaQuery()
                .eq(HeritageApply::getUserId, userId)
                .orderByDesc(HeritageApply::getId)
                .last("LIMIT 1")
                .one();
        if (apply == null) {
            return null;
        }
        return toVO(apply, userMapper.selectById(userId));
    }

    @Override
    public PageResult<HeritageApplyVO> pageApplies(PageDto pageDto, Integer auditStatus) {
        Page<HeritageApply> page = new Page<>(pageDto.getCurrent(), pageDto.getSize());
        lambdaQuery()
                .eq(auditStatus != null, HeritageApply::getAuditStatus, auditStatus)
                // 待审核优先展示，同状态按申请时间倒序
                .orderByAsc(HeritageApply::getAuditStatus)
                .orderByDesc(HeritageApply::getId)
                .page(page);

        List<HeritageApply> records = page.getRecords();
        if (records.isEmpty()) {
            return PageResult.of(page, Collections.emptyList());
        }
        // 批量查询申请人信息，回填 VO（账号 / 昵称 / 头像 / 手机号）
        List<Long> userIds = records.stream().map(HeritageApply::getUserId).distinct().toList();
        Map<Long, SysUser> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity(), (a, b) -> a));
        List<HeritageApplyVO> voList = records.stream()
                .map(item -> toVO(item, userMap.get(item.getUserId())))
                .toList();
        return PageResult.of(page, voList);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditApply(Long applyId, HeritageAuditDTO dto, Long auditorId) {
        // 1.审核结论兜底校验（防止内部调用绕开 DTO 注解约束），仅支持：1审核通过 2审核驳回
        Integer auditStatus = dto.getAuditStatus();
        if (auditStatus == null || (auditStatus != AUDIT_PASS && auditStatus != AUDIT_REJECT)) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "审核结论仅支持：1审核通过、2审核驳回");
        }
        // 驳回必须填写驳回原因，供申请人了解未通过原因
        String remark = dto.getRemark() == null ? null : dto.getRemark().trim();
        if (auditStatus == AUDIT_REJECT && !StringUtils.hasText(remark)) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "驳回申请必须填写驳回原因");
        }
        // 2.仅待审核申请可审（防重复审核）
        HeritageApply apply = getById(applyId);
        if (apply == null) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "申请记录不存在或已删除");
        }
        if (!Integer.valueOf(AUDIT_PENDING).equals(apply.getAuditStatus())) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "该申请已审核，无需重复审核");
        }
        // 3.更新审核结论
        HeritageApply update = new HeritageApply();
        update.setId(applyId);
        update.setAuditStatus(auditStatus);
        update.setAuditRemark(remark);
        update.setAuditTime(LocalDateTime.now());
        update.setAuditorId(auditorId);
        updateById(update);
        // 4.审核通过：申请人角色置为非遗人，正常状态（JWT角色随旧token过期，重新登录后生效）
        if (auditStatus == AUDIT_PASS) {
            SysUser userUpdate = new SysUser();
            userUpdate.setId(apply.getUserId());
            userUpdate.setRole(ROLE_HERITAGE);
            userUpdate.setStatus(STATUS_NORMAL);
            userMapper.updateById(userUpdate);
        }
        log.info("非遗人申请审核完成：applyId={}, userId={}, auditStatus={}（1通过 2驳回）, auditorId={}",
                applyId, apply.getUserId(), auditStatus, auditorId);
        // 系统日志：记录管理员审核结论（操作人为当前管理员，驳回附原因）
        sysLogService.record(auditorId, "申请审核",
                (auditStatus == AUDIT_PASS ? "审核通过" : "审核驳回") + "非遗人申请（申请ID：" + applyId
                        + "，申请人ID：" + apply.getUserId() + "）"
                        + (auditStatus == AUDIT_REJECT ? "，驳回原因：" + remark : ""));
    }

    /** 实体转VO：补充申请人基础信息（账号 / 昵称 / 头像 / 手机号） */
    private HeritageApplyVO toVO(HeritageApply apply, SysUser user) {
        HeritageApplyVO vo = new HeritageApplyVO();
        vo.setId(apply.getId());
        vo.setUserId(apply.getUserId());
        vo.setIdentityInfo(apply.getIdentityInfo());
        vo.setApplyReason(apply.getApplyReason());
        vo.setMaterialUrls(apply.getMaterialUrls());
        vo.setAuditStatus(apply.getAuditStatus());
        vo.setAuditRemark(apply.getAuditRemark());
        vo.setAuditTime(apply.getAuditTime());
        vo.setCreateTime(apply.getCreateTime());
        if (user != null) {
            vo.setAccount(user.getAccount());
            vo.setNickname(user.getNickname());
            vo.setAvatar(user.getAvatar());
            vo.setPhone(user.getPhone());
        }
        return vo;
    }
}
