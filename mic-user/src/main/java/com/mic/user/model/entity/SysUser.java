package com.mic.user.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    @NotBlank(message = "账号不能为空")
    @Size(min = 4, max = 12, message = "账号长度必须4-12个字符")
    private String account;

    // 注意：实体类不强制密码非空，修改用户信息密码可传null；注册DTO再做@NotBlank
    @Size(min = 6, max = 20, message = "密码长度6-20位")
    private String password;

    @NotBlank(message = "昵称不能为空")
    @Size(max = 50, message = "昵称不能超过50个字符")
    private String nickname;

    private String avatar;

    private String identityInfo;

    @NotNull(message = "用户角色不能为空")
    private Integer role;

    /** 角色ID，关联sys_role.id（管理员绑定细分角色决定后台功能权限；非遗人与普通用户绑定各自角色） */
    private Long roleId;

    @NotNull(message = "账号状态不能为空")
    private Integer status;

    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
