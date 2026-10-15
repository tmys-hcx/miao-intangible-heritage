package com.mic.inheritReserve.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mic.inheritReserve.model.entity.IntangibleInherit;

/**
 * 旧非遗传承信息表Mapper（过渡保留）
 * 说明：原 intangible_inherit 表已按业务拆分为 intangible_video / intangible_interview / intangible_knowledge 三张表，
 * 管理端分页维护已迁移至三张新表 Mapper（selectAdminPage）；本 Mapper 与实体仅为兼容 Service 继承结构保留，
 * 不再执行任何实际数据操作，旧表确认无引用后可执行 DROP TABLE intangible_inherit。
 * 注意：本 Mapper 与 IntangibleInherit 实体需成对保留，单独删除会破坏 Service 继承结构。
 */
public interface IntangibleInheritMapper extends BaseMapper<IntangibleInherit> {
}
