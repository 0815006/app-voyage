package com.voyage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.voyage.entity.SamplingTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通用采样任务 Mapper 接口。
 */
@Mapper
public interface SamplingTaskMapper extends BaseMapper<SamplingTask> {
}
