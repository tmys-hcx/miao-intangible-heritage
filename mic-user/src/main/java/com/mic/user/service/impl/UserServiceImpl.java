package com.mic.user.service.impl;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.ShearCaptcha;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mic.common.exception.BusinessException;
import com.mic.common.model.dto.PageDto;
import com.mic.common.model.vo.LoginUserVO;
import com.mic.common.result.PageResult;
import com.mic.common.result.ResultCodeEnum;
import com.mic.common.util.JwtUtil;
import com.mic.common.util.MD5Util;
import com.mic.common.util.RedisUtil;
import com.mic.common.util.StatsUtil;
import com.mic.common.util.UUIDUtil;
import com.mic.user.mapper.UserMapper;
import com.mic.user.model.dto.UserInfoUpdateDTO;
import com.mic.user.model.dto.UserLoginDTO;
import com.mic.user.model.dto.UserPasswordUpdateDTO;
import com.mic.user.model.dto.UserRegisterDTO;
import com.mic.user.model.entity.HeritageApply;
import com.mic.user.model.entity.SysUser;
import com.mic.user.model.vo.AdminStatsVO;
import com.mic.user.model.vo.CaptchaVO;
import com.mic.user.model.vo.UserLoginVO;
import com.mic.user.service.HeritageApplyService;
import com.mic.user.service.SysLogService;
import com.mic.user.service.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 用户服务实现：图形验证码、注册、登录、用户信息、个人信息修改 + 管理端用户管理（分页 / 启用禁用 / 删除）
 * 说明：非遗人身份申请（heritage_apply）由 HeritageApplyService 承载，审核通过后修改 sys_user.role；
 * 注册 / 登录 / 用户管理为关键操作，成功后写入系统日志（sys_log，管理端系统日志可查）
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, SysUser> implements UserService {

    /** 角色：0管理员（不开放注册）、1非遗人、2普通用户 */
    private static final int ROLE_ADMIN = 0;
    private static final int ROLE_HERITAGE = 1;
    private static final int ROLE_NORMAL = 2;

    /** 登录后跳转地址：管理员进入后台管理页，非遗人/普通用户回到前台首页 */
    private static final String REDIRECT_URL_ADMIN = "/admin";
    private static final String REDIRECT_URL_HOME = "/";

    /** 状态：1正常、2禁用 */
    private static final int STATUS_NORMAL = 1;
    private static final int STATUS_DISABLED = 2;

    /** 非遗人申请审核状态：0待审核（与 heritage_apply.audit_status 对齐） */
    private static final int APPLY_AUDIT_PENDING = 0;

    /** 图形验证码 Redis key 前缀 */
    private static final String CAPTCHA_KEY_PREFIX = "captcha:";

    /** 图形验证码有效期（分钟） */
    private static final long CAPTCHA_EXPIRE_MINUTES = 2;

    @Resource
    private RedisUtil redisUtil;

    @Resource
    private JwtUtil jwtUtil;

    @Resource
    private SysLogService sysLogService;

    @Resource
    private HeritageApplyService heritageApplyService;

    @Override
    public CaptchaVO createCaptcha() {
        // 生成4位字符图形验证码（116×40 与前端展示尺寸一致）
        ShearCaptcha captcha = CaptchaUtil.createShearCaptcha(116, 40, 4, 4);

        String captchaKey = UUIDUtil.uuid();
        redisUtil.setEx(CAPTCHA_KEY_PREFIX + captchaKey, captcha.getCode(),
                CAPTCHA_EXPIRE_MINUTES, TimeUnit.MINUTES);

        CaptchaVO captchaVO = new CaptchaVO();
        captchaVO.setCaptchaKey(captchaKey);
        captchaVO.setImageBase64(captcha.getImageBase64Data());
        return captchaVO;
    }

    @Override
    public void register(UserRegisterDTO dto) {
        // 1.校验图形验证码
        checkCaptcha(dto.getCaptchaKey(), dto.getCaptchaCode());

        // 2.账号唯一性校验
        if (lambdaQuery().eq(SysUser::getAccount, dto.getAccount()).exists()) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "账号已存在，请更换账号");
        }

        // 3.组装用户信息（密码MD5加密，禁止明文存储）
        // 注册仅面向普通用户，非遗人身份需后续在个人中心申请，注册数据不进入待审核状态
        SysUser user = new SysUser();
        user.setAccount(dto.getAccount());
        user.setPassword(MD5Util.encrypt(dto.getPassword()));
        user.setNickname(StringUtils.hasText(dto.getNickname()) ? dto.getNickname() : dto.getAccount());
        user.setPhone(dto.getPhone());
        user.setRole(ROLE_NORMAL);
        user.setStatus(STATUS_NORMAL);

        save(user);
        log.info("用户注册成功：account={}, role={}, status={}", user.getAccount(), user.getRole(), user.getStatus());
        // 系统日志：注册为关键操作，记录新账号信息（操作人即新用户本人）
        sysLogService.record(user.getId(), "用户注册", "新用户注册成功（账号：" + user.getAccount() + "）");
    }

    @Override
    public UserLoginVO login(UserLoginDTO dto) {
        // 1.校验图形验证码
        checkCaptcha(dto.getCaptchaKey(), dto.getCaptchaCode());

        // 2.校验账号密码（账号不存在与密码错误统一提示，避免账号探测）
        SysUser user = lambdaQuery().eq(SysUser::getAccount, dto.getAccount()).one();
        if (user == null || !MD5Util.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCodeEnum.NOT_LOGIN, "账号或密码错误");
        }

        // 3.校验账号禁用状态（仅禁用账号拦截）
        if (Integer.valueOf(STATUS_DISABLED).equals(user.getStatus())) {
            throw new BusinessException(ResultCodeEnum.ACCOUNT_DISABLED);
        }

        // 4.校验角色字段合法性：角色决定登录后的权限与跳转地址，非法角色禁止登录（防御脏数据）
        Integer role = user.getRole();
        if (role == null || (role != ROLE_ADMIN && role != ROLE_HERITAGE && role != ROLE_NORMAL)) {
            log.warn("用户角色非法，拒绝登录：account={}, role={}", user.getAccount(), role);
            throw new BusinessException(ResultCodeEnum.NOT_LOGIN, "账号角色异常，无法登录，请联系管理员");
        }

        // 5.生成JWT令牌（含角色），并按角色返回跳转地址
        UserLoginVO loginVO = new UserLoginVO();
        loginVO.setToken(jwtUtil.createToken(user.getId(), user.getAccount(), user.getRole()));
        loginVO.setUserInfo(toLoginUserVO(user));
        loginVO.setRedirectUrl(resolveRedirectUrl(role));
        log.info("用户登录成功：account={}, role={}", user.getAccount(), role);
        // 系统日志：登录为关键操作，记录登录账号与角色
        sysLogService.record(user.getId(), "用户登录",
                "账号 " + user.getAccount() + " 登录成功（" + roleLabel(role) + "）");
        return loginVO;
    }

    /** 角色文案（系统日志展示）：0管理员 / 1非遗人 / 2普通用户 */
    private String roleLabel(Integer role) {
        return switch (role) {
            case ROLE_ADMIN -> "管理员";
            case ROLE_HERITAGE -> "非遗人";
            default -> "普通用户";
        };
    }

    /** 角色决定登录落地页：管理员进入后台管理页面，非遗人/普通用户回到前台首页 */
    private String resolveRedirectUrl(Integer role) {
        return ROLE_ADMIN == role ? REDIRECT_URL_ADMIN : REDIRECT_URL_HOME;
    }

    @Override
    public LoginUserVO getInfo(Long userId) {
        SysUser user = getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCodeEnum.NOT_LOGIN, "登录用户不存在或已被删除");
        }
        return toLoginUserVO(user);
    }

    // ———————————————— 管理端用户管理（管理员操作） ————————————————

    @Override
    public PageResult<SysUser> pageAdminUsers(PageDto pageDto, Integer role, Integer status, String keyword) {
        Page<SysUser> page = new Page<>(pageDto.getCurrent(), pageDto.getSize());
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;

        // 显式指定查询字段（不取密码列，避免敏感信息外泄），支持角色 / 状态 / 关键词（账号、昵称）筛选
        lambdaQuery()
                .select(SysUser::getId, SysUser::getAccount, SysUser::getNickname, SysUser::getAvatar,
                        SysUser::getIdentityInfo, SysUser::getRole, SysUser::getStatus, SysUser::getPhone,
                        SysUser::getCreateTime, SysUser::getUpdateTime)
                .eq(role != null, SysUser::getRole, role)
                .eq(status != null, SysUser::getStatus, status)
                .and(kw != null,
                        wrapper -> wrapper.like(SysUser::getAccount, kw)
                                .or()
                                .like(SysUser::getNickname, kw))
                .orderByDesc(SysUser::getCreateTime)
                .orderByDesc(SysUser::getId)
                .page(page);

        return PageResult.of(page);
    }

    @Override
    public LoginUserVO updateUserInfo(Long userId, UserInfoUpdateDTO dto) {
        SysUser user = getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCodeEnum.NOT_LOGIN, "登录用户不存在或已被删除");
        }
        // 部分更新：仅更新有值字段（MyBatis-Plus updateById 忽略 null 字段）
        SysUser update = new SysUser();
        update.setId(userId);
        if (StringUtils.hasText(dto.getNickname())) {
            update.setNickname(dto.getNickname().trim());
        }
        if (StringUtils.hasText(dto.getPhone())) {
            update.setPhone(dto.getPhone().trim());
        }
        if (StringUtils.hasText(dto.getIdentityInfo())) {
            update.setIdentityInfo(dto.getIdentityInfo().trim());
        }
        if (StringUtils.hasText(dto.getAvatar())) {
            update.setAvatar(dto.getAvatar().trim());
        }
        updateById(update);
        log.info("用户信息更新：userId={}, account={}", userId, user.getAccount());
        // 返回最新用户信息：前端据此就地刷新（身份信息完善状态影响非遗人申请入口）
        return toLoginUserVO(getById(userId));
    }

    @Override
    public void updatePassword(Long userId, UserPasswordUpdateDTO dto) {
        SysUser user = getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCodeEnum.NOT_LOGIN, "登录用户不存在或已被删除");
        }
        // 1.校验原密码（不匹配则拒绝，防越权改密）
        if (!MD5Util.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "原密码不正确");
        }
        // 2.新旧密码相同直接拒绝（避免无效提交）
        if (MD5Util.matches(dto.getNewPassword(), user.getPassword())) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "新密码不能与原密码相同");
        }
        // 3.密码MD5加密后更新（与注册/登录一致，禁止明文存储）
        SysUser update = new SysUser();
        update.setId(userId);
        update.setPassword(MD5Util.encrypt(dto.getNewPassword()));
        updateById(update);
        log.info("用户修改密码：userId={}, account={}", userId, user.getAccount());
    }

    @Override
    public void changeUserStatus(Long userId, Integer status, Long operatorId) {
        if (status == null || (status != STATUS_NORMAL && status != STATUS_DISABLED)) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "状态取值：1启用、2禁用");
        }
        SysUser user = getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "用户不存在或已删除");
        }
        // 管理员账号受保护：不可被禁用（避免误操作导致管理端失联）
        if (user.getRole() != null && user.getRole() == ROLE_ADMIN) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN, "管理员账号不可禁用");
        }
        SysUser update = new SysUser();
        update.setId(userId);
        update.setStatus(status);
        updateById(update);
        log.info("账号状态变更：userId={}, account={}, status={}（1启用 2禁用）", userId, user.getAccount(), status);
        // 系统日志：记录管理员对账号的启用 / 禁用操作（操作人为当前管理员）
        sysLogService.record(operatorId, "用户管理",
                (status == STATUS_DISABLED ? "禁用" : "启用") + "账号 " + user.getAccount());
    }

    @Override
    public void removeUser(Long userId, Long operatorId) {
        SysUser user = getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "用户不存在或已删除");
        }
        // 管理员账号受保护：不可被删除
        if (user.getRole() != null && user.getRole() == ROLE_ADMIN) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN, "管理员账号不可删除");
        }
        // @TableLogic 逻辑删除：is_deleted置1，数据保留
        removeById(userId);
        log.info("用户删除成功（逻辑删除）：userId={}, account={}", userId, user.getAccount());
        // 系统日志：记录管理员删除账号操作（操作人为当前管理员）
        sysLogService.record(operatorId, "用户管理", "删除账号 " + user.getAccount());
    }

    // ———————————————— 管理端首页数据统计 ————————————————

    @Override
    public AdminStatsVO adminStats() {
        AdminStatsVO vo = new AdminStatsVO();
        // 1.用户总量与今日新增（逻辑删除记录由 @TableLogic 自动排除）
        vo.setUserTotal(count());
        vo.setTodayNewUsers(lambdaQuery().ge(SysUser::getCreateTime, LocalDate.now().atStartOfDay()).count());
        // 2.角色分布：0管理员 / 1非遗人 / 2普通用户
        vo.setAdminCount(lambdaQuery().eq(SysUser::getRole, ROLE_ADMIN).count());
        vo.setHeritageCount(lambdaQuery().eq(SysUser::getRole, ROLE_HERITAGE).count());
        vo.setNormalCount(lambdaQuery().eq(SysUser::getRole, ROLE_NORMAL).count());
        // 3.待审核非遗人申请数（heritage_apply.audit_status=0）
        vo.setPendingApplies(heritageApplyService.lambdaQuery()
                .eq(HeritageApply::getAuditStatus, APPLY_AUDIT_PENDING)
                .count());
        // 4.近7天注册趋势：按天分组统计后补零组装（含今天，日期升序）
        List<Map<String, Object>> rows = listMaps(new QueryWrapper<SysUser>()
                .select("DATE_FORMAT(create_time, '%m-%d') AS trend_date", "COUNT(*) AS trend_count")
                .ge("create_time", LocalDate.now().minusDays(6).atStartOfDay())
                .groupBy("DATE_FORMAT(create_time, '%m-%d')"));
        vo.setRegisterTrend(StatsUtil.fillLast7Days(StatsUtil.toCountByDate(rows, "trend_date", "trend_count")));
        return vo;
    }

    /** 校验图形验证码：一次性使用，取出即删除，防重复提交 */
    private void checkCaptcha(String captchaKey, String captchaCode) {
        if (!StringUtils.hasText(captchaKey) || !StringUtils.hasText(captchaCode)) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "验证码不能为空");
        }
        String cachedCode = redisUtil.getAndDelete(CAPTCHA_KEY_PREFIX + captchaKey);
        if (cachedCode == null || !cachedCode.equalsIgnoreCase(captchaCode)) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "验证码错误或已过期");
        }
    }

    /** 实体转登录用户VO（不含密码等敏感字段） */
    private LoginUserVO toLoginUserVO(SysUser user) {
        LoginUserVO vo = new LoginUserVO();
        vo.setId(user.getId());
        vo.setAccount(user.getAccount());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setPhone(user.getPhone());
        vo.setIdentityInfo(user.getIdentityInfo());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        return vo;
    }
}
