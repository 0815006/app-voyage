package com.voyage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.voyage.entity.SamplingHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通用采样结果明细 Mapper 接口。
 */
@Mapper
public interface SamplingHistoryMapper extends BaseMapper<SamplingHistory> {
}
