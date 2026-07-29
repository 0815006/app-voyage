package com.voyage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.voyage.entity.*;
import com.voyage.mapper.PerformanceResourceInfoMapper;
import com.voyage.service.ResourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceServiceImpl implements ResourceService {

    private final PerformanceResourceInfoMapper resourceMapper;

    @Override
    public ResourceCheckResponse getResourceCheckByProduct(String productId, String batchNo, String fileSource) {
        List<PerformanceResourceInfo> list = resourceMapper.selectList(
                new LambdaQueryWrapper<PerformanceResourceInfo>()
                        .eq(PerformanceResourceInfo::getProductId, productId)
                        .eq(batchNo != null, PerformanceResourceInfo::getBatchNo, batchNo)
                        .eq(fileSource != null, PerformanceResourceInfo::getFileSource, fileSource)
                        .isNotNull(PerformanceResourceInfo::getDeploymentLocation)
        );

        if (list.isEmpty()) {
            return new ResourceCheckResponse(List.of(), List.of(), List.of());
        }

        // 1. 产品级汇总：按 deploymentLocation + systemPlatform
        List<ResourceSummaryDTO> summaryList = list.stream()
                .collect(Collectors.groupingBy(
                        PerformanceResourceInfo::getDeploymentLocation,
                        Collectors.groupingBy(PerformanceResourceInfo::getSystemPlatform)
                ))
                .entrySet().stream()
                .flatMap(outer -> outer.getValue().entrySet().stream()
                        .map(inner -> {
                            List<PerformanceResourceInfo> group = inner.getValue();
                            return new ResourceSummaryDTO(
                                    outer.getKey(),
                                    inner.getKey(),
                                    group.size(),
                                    group.stream().mapToInt(r -> r.getCpuCores() == null ? 0 : r.getCpuCores()).sum(),
                                    group.stream().mapToInt(r -> r.getMemoryGb() == null ? 0 : r.getMemoryGb()).sum(),
                                    group.stream().mapToLong(r -> {
                                        int d = r.getDedicatedStorageGb() == null ? 0 : r.getDedicatedStorageGb();
                                        int s = r.getSanStorageGb() == null ? 0 : r.getSanStorageGb();
                                        int n = r.getNasStorageGb() == null ? 0 : r.getNasStorageGb();
                                        return (long) d + s + n;
                                    }).sum()
                            );
                        }))
                .sorted(Comparator.comparing(ResourceSummaryDTO::deploymentLocation)
                        .thenComparing(ResourceSummaryDTO::systemPlatform))
                .collect(Collectors.toList());

        // 2. 明细聚合：按 DetailKey
        List<ResourceDetailDTO> detailList = list.stream()
                .filter(r -> r.getPartitionUsage() != null)
                .collect(Collectors.groupingBy(DetailKey::new))
                .entrySet().stream()
                .map(entry -> {
                    DetailKey key = entry.getKey();
                    List<PerformanceResourceInfo> group = entry.getValue();
                    return new ResourceDetailDTO(
                            key.deploymentLocation(),
                            key.partitionUsage(),
                            key.systemPlatform(),
                            key.cpuCores(),
                            key.memoryGb(),
                            key.dedicatedStorageGb(),
                            key.sanStorageGb(),
                            key.nasStorageGb(),
                            group.size()
                    );
                })
                .sorted(Comparator.comparing(ResourceDetailDTO::deploymentLocation)
                        .thenComparing(ResourceDetailDTO::partitionUsage)
                        .thenComparing(ResourceDetailDTO::systemPlatform))
                .collect(Collectors.toList());

        // 3. 文件级汇总：按 original_file_name 分组
        List<FileSummaryGroup> fileSummaryList = list.stream()
                .filter(r -> r.getOriginalFileName() != null && !r.getOriginalFileName().trim().isEmpty())
                .collect(Collectors.groupingBy(PerformanceResourceInfo::getOriginalFileName))
                .entrySet().stream()
                .map(entry -> {
                    String originalFileName = entry.getKey();
                    List<PerformanceResourceInfo> fileGroup = entry.getValue();

                    // 计算上传次数 = 不同 file_name 的数量
                    long uploadCount = fileGroup.stream()
                            .map(PerformanceResourceInfo::getFileName)
                            .distinct()
                            .count();

                    // 在该文件名的所有记录中，按 deploymentLocation + systemPlatform 汇总
                    List<ResourceSummaryDTO> summary = fileGroup.stream()
                            .collect(Collectors.groupingBy(
                                    PerformanceResourceInfo::getDeploymentLocation,
                                    Collectors.groupingBy(PerformanceResourceInfo::getSystemPlatform)
                            ))
                            .entrySet().stream()
                            .flatMap(outer -> outer.getValue().entrySet().stream()
                                    .map(inner -> {
                                        List<PerformanceResourceInfo> group = inner.getValue();
                                        return new ResourceSummaryDTO(
                                                outer.getKey(),
                                                inner.getKey(),
                                                group.size(),
                                                group.stream().mapToInt(r -> r.getCpuCores() == null ? 0 : r.getCpuCores()).sum(),
                                                group.stream().mapToInt(r -> r.getMemoryGb() == null ? 0 : r.getMemoryGb()).sum(),
                                                group.stream().mapToLong(r -> {
                                                    int d = r.getDedicatedStorageGb() == null ? 0 : r.getDedicatedStorageGb();
                                                    int s = r.getSanStorageGb() == null ? 0 : r.getSanStorageGb();
                                                    int n = r.getNasStorageGb() == null ? 0 : r.getNasStorageGb();
                                                    return (long) d + s + n;
                                                }).sum()
                                        );
                                    }))
                            .sorted(Comparator.comparing(ResourceSummaryDTO::deploymentLocation)
                                    .thenComparing(ResourceSummaryDTO::systemPlatform))
                            .collect(Collectors.toList());

                    return new FileSummaryGroup(originalFileName, (int) uploadCount, summary);
                })
                .sorted(Comparator.comparing(FileSummaryGroup::originalFileName))
                .collect(Collectors.toList());

        return new ResourceCheckResponse(summaryList, fileSummaryList, detailList);
    }

    @Override
    public int deleteByOriginalFileName(String productId, String batchNo, String originalFileName) {
        return resourceMapper.delete(
                new LambdaQueryWrapper<PerformanceResourceInfo>()
                        .eq(PerformanceResourceInfo::getProductId, productId)
                        .eq(batchNo != null, PerformanceResourceInfo::getBatchNo, batchNo)
                        .eq(PerformanceResourceInfo::getOriginalFileName, originalFileName)
        );
    }

    @Override
    public List<String> getAllProductIds() {
        List<PerformanceResourceInfo> list = resourceMapper.selectList(
                new LambdaQueryWrapper<PerformanceResourceInfo>()
                        .select(PerformanceResourceInfo::getProductId, PerformanceResourceInfo::getBatchNo)
                        .isNotNull(PerformanceResourceInfo::getProductId)
                        .groupBy(PerformanceResourceInfo::getProductId, PerformanceResourceInfo::getBatchNo)
        );
        return list.stream()
                .map(r -> r.getProductId() + "|" + (r.getBatchNo() != null ? r.getBatchNo() : ""))
                .filter(s -> !s.startsWith("|") && !s.endsWith("|"))
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 自定义分组键，用于按部署地点/分区用途/系统平台/资源配置 维度聚合
     */
    private record DetailKey(
            String deploymentLocation,
            String partitionUsage,
            String systemPlatform,
            Integer cpuCores,
            Integer memoryGb,
            Integer dedicatedStorageGb,
            Integer sanStorageGb,
            Integer nasStorageGb
    ) {
        DetailKey(PerformanceResourceInfo r) {
            this(
                    r.getDeploymentLocation(),
                    r.getPartitionUsage(),
                    r.getSystemPlatform(),
                    r.getCpuCores(),
                    r.getMemoryGb(),
                    r.getDedicatedStorageGb(),
                    r.getSanStorageGb(),
                    r.getNasStorageGb()
            );
        }
    }
}
