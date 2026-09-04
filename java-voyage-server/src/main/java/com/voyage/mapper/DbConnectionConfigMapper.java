package com.voyage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.voyage.entity.DbConnectionConfigEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数据库连接配置 Mapper 接口。
 */
@Mapper
public interface DbConnectionConfigMapper extends BaseMapper<DbConnectionConfigEntity> {
}
