package com.mic.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mic.common.model.dto.PageDto;
import com.mic.common.model.vo.LoginUserVO;
import com.mic.common.result.PageResult;
import com.mic.user.model.dto.UserInfoUpdateDTO;
import com.mic.user.model.dto.UserLoginDTO;
import com.mic.user.model.dto.UserPasswordUpdateDTO;
import com.mic.user.model.dto.UserRegisterDTO;
import com.mic.user.model.entity.SysUser;
import com.mic.user.model.vo.AdminStatsVO;
import com.mic.user.model.vo.CaptchaVO;
import com.mic.user.model.vo.UserLoginVO;

/**
 * 用户服务接口：图形验证码、注册、登录、用户信息、个人信息修改 + 管理端用户管理（分页 / 启用禁用 / 删除）
 */
public interface UserService extends IService<SysUser> {

    /** 生成图形验证码（存入Redis，有效期2分钟） */
    CaptchaVO createCaptcha();

    /** 用户注册（仅普通用户；非遗人身份需在个人中心提交申请） */
    void register(UserRegisterDTO dto);

    /** 用户登录（校验验证码、账号密码、禁用状态，返回token） */
    UserLoginVO login(UserLoginDTO dto);

    /** 获取当前登录用户信息 */
    LoginUserVO getInfo(Long userId);

    /** 用户分页列表（管理员）：可按角色 / 状态 / 关键词（账号、昵称）筛选，出参不含密码 */
    PageResult<SysUser> pageAdminUsers(PageDto pageDto, Integer role, Integer status, String keyword);

    /** 修改个人信息（部分更新）：昵称 / 手机号 / 身份信息 / 头像，null 字段不修改，返回最新用户信息 */
    LoginUserVO updateUserInfo(Long userId, UserInfoUpdateDTO dto);

    /** 修改登录密码：校验原密码，新密码长度6-20位且不能与原密码相同 */
    void updatePassword(Long userId, UserPasswordUpdateDTO dto);

    /** 启用 / 禁用账号（管理员）：status 1启用 2禁用，管理员账号受保护不可操作；operatorId 为操作管理员（写入系统日志） */
    void changeUserStatus(Long userId, Integer status, Long operatorId);

    /** 逻辑删除用户（管理员）：管理员账号受保护不可删除；operatorId 为操作管理员（写入系统日志） */
    void removeUser(Long userId, Long operatorId);

    /** 管理端首页数据统计：用户总数 / 今日新增 / 角色分布 / 待审核非遗人申请数 / 近7天注册趋势 */
    AdminStatsVO adminStats();
}
