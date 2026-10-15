package com.mic.user.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mic.common.model.dto.PageDto;
import com.mic.common.result.PageResult;
import com.mic.common.util.IpUtil;
import com.mic.user.mapper.SysLogMapper;
import com.mic.user.model.entity.SysLog;
import com.mic.user.model.vo.SysLogVO;
import com.mic.user.service.SysLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 系统日志服务实现：管理端日志分页查询 + 业务关键操作写入日志
 * 说明：record 由 UserService / HeritageApplyService 等业务在关键操作成功后调用，
 * IP 从当前请求上下文自动获取（网关代理场景由 IpUtil 兼容 X-Forwarded-For）
 */
@Slf4j
@Service
public class SysLogServiceImpl extends ServiceImpl<SysLogMapper, SysLog> implements SysLogService {

    @Override
    public PageResult<SysLogVO> pageLogs(PageDto pageDto, String keyword) {
        Page<SysLogVO> page = new Page<>(pageDto.getCurrent(), pageDto.getSize());
        String kw = keyword == null ? "" : keyword.trim();
        // 自定义 SQL 手动过滤 is_deleted=0；keyword 非空时模糊匹配操作模块或操作人账号
        baseMapper.selectLogPage(page, kw);
        return PageResult.of(page);
    }

    @Override
    public void record(Long operateUserId, String operateModule, String operateContent) {
        try {
            SysLog logEntry = new SysLog();
            logEntry.setOperateUserId(operateUserId);
            logEntry.setOperateModule(operateModule);
            logEntry.setOperateContent(operateContent);
            logEntry.setIp(resolveClientIp());
            save(logEntry);
        } catch (Exception e) {
            // 日志写入失败不影响主业务流程，仅记录告警
            log.warn("系统日志写入失败：operateUserId={}, operateModule={}, operateContent={}",
                    operateUserId, operateModule, operateContent, e);
        }
    }

    /** 从当前请求上下文获取客户端IP：无请求上下文（如定时任务线程）时返回 unknown */
    private String resolveClientIp() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes == null ? null : attributes.getRequest();
        return IpUtil.getClientIp(request);
    }
}
