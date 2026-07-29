package com.voyage.entity;

/**
 * 资源明细聚合（按部署地点 + 分区用途 + 系统平台维度）
 */
public record ResourceDetailDTO(
        String deploymentLocation,
        String partitionUsage,
        String systemPlatform,
        Integer cpuCores,
        Integer memoryGb,
        Integer dedicatedStorageGb,
        Integer sanStorageGb,
        Integer nasStorageGb,
        Integer count
) {
}
