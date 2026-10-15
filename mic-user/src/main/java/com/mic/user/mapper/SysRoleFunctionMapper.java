package com.mic.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mic.user.model.entity.SysRoleFunction;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

/**
 * 角色功能关系表Mapper
 */
public interface SysRoleFunctionMapper extends BaseMapper<SysRoleFunction> {

    /**
     * 物理删除角色的全部功能关系（权限分配覆盖保存前清理）：
     * 关系表为纯绑定数据，物理删除可避免逻辑删除记录累积触发唯一键冲突
     * @param roleId 角色ID
     * @return 删除行数
     */
    @Delete("DELETE FROM sys_role_function WHERE role_id = #{roleId}")
    int physicalDeleteByRoleId(@Param("roleId") Long roleId);
}
