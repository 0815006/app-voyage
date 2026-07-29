package com.voyage.entity;

import java.util.List;

/**
 * 文件级汇总（按原始文件名分组）
 */
public record FileSummaryGroup(
        String originalFileName,
        Integer uploadCount,
        List<ResourceSummaryDTO> summary
) {
}
