package com.voyage.entity;

import java.util.List;

/**
 * 资源核查响应，包含产品级汇总、文件级汇总和明细聚合
 */
public record ResourceCheckResponse(
        List<ResourceSummaryDTO> summaryList,
        List<FileSummaryGroup> fileSummaryList,
        List<ResourceDetailDTO> detailList
) {
}
