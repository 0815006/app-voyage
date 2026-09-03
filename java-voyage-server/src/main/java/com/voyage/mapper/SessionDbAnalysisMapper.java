package com.voyage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.voyage.entity.SessionDbAnalysis;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数据库性能分析会话 Mapper 接口。
 */
@Mapper
public interface SessionDbAnalysisMapper extends BaseMapper<SessionDbAnalysis> {
}