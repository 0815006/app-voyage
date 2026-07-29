package com.voyage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.voyage.entity.WikiDocument;
import org.apache.ibatis.annotations.Mapper;

/**
 * Wiki 文档 Mapper 接口。
 */
@Mapper
public interface WikiDocumentMapper extends BaseMapper<WikiDocument> {
}
