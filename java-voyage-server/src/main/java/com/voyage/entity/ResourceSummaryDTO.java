package com.voyage.entity;

/**
 * 产品级资源汇总（按部署地点 + 系统平台维度）
 */
public record ResourceSummaryDTO(
        String deploymentLocation,
        String systemPlatform,
        Integer hostCount,
        Integer totalCpu,
        Integer totalMemoryGb,
        Long totalStorageGb
) {
}
