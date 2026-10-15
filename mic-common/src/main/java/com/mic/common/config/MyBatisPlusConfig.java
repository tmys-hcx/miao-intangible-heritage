package com.mic.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatisPlus配置：分页插件、逻辑删除
 * 逻辑删除说明：实体字段标注 @TableLogic，未删除=0、已删除=1（与建表脚本一致）
 */
@Configuration
public class MyBatisPlusConfig {

    /** 单页最大条数，防止大分页查询拖垮数据库 */
    private static final long MAX_PAGE_SIZE = 100L;

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor paginationInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);
        paginationInterceptor.setMaxLimit(MAX_PAGE_SIZE);
        interceptor.addInnerInterceptor(paginationInterceptor);
        return interceptor;
    }
}
