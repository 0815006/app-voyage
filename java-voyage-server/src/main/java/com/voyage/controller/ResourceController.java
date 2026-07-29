package com.voyage.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.voyage.common.EmpContext;
import com.voyage.common.Result;
import com.voyage.entity.PerformanceResourceInfo;
import com.voyage.entity.ResourceCheckResponse;
import com.voyage.service.PerformanceResourceInfoService;
import com.voyage.service.ResourceService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/resource")
@RequiredArgsConstructor
public class ResourceController {

    private final ResourceService resourceService;
    private final PerformanceResourceInfoService performanceResourceInfoService;

    /**
     * 查询环境资源清单明细
     */
    @GetMapping("/queryResourceDetail")
    public Result<Page<PerformanceResourceInfo>> query(
            @RequestParam(required = false, defaultValue = "1") int pageNum,
            @RequestParam(required = false, defaultValue = "10") int pageSize,
            @RequestParam(required = false) String productId,
            @RequestParam(required = false) String batchNo,
            @RequestParam(required = false) String fileName) {

        Page<PerformanceResourceInfo> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<PerformanceResourceInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(productId), PerformanceResourceInfo::getProductId, productId);
        wrapper.eq(StringUtils.hasText(batchNo), PerformanceResourceInfo::getBatchNo, batchNo);
        wrapper.eq(StringUtils.hasText(fileName), PerformanceResourceInfo::getFileName, fileName);
        wrapper.orderByDesc(PerformanceResourceInfo::getCreateTime);

        Page<PerformanceResourceInfo> result = performanceResourceInfoService.page(page, wrapper);
        return Result.ok(result);
    }

    /**
     * 上传环境资源清单
     */
    @PostMapping("/uploadResource")
    public Result<Void> uploadExcelFile(
            @RequestParam @NotBlank(message = "产品标识不能为空") String productId,
            @RequestParam @NotBlank(message = "批次不能为空") String batchNo,
            @RequestParam(required = false, defaultValue = "部署方案") String fileSource,
            @RequestParam("file") MultipartFile file) throws Exception {

        String empNo = EmpContext.getEmpNo();

        // 上传文件到本地
        Map<String, Object> map = performanceResourceInfoService.uploadExcel(file);

        // 导入到表
        performanceResourceInfoService.importExcel(file,
                Objects.toString(map.get("originalFileName"), "no originalFileName"),
                Objects.toString(map.get("fileName"), "no fileName"),
                productId, batchNo, empNo, fileSource);

        return Result.ok();
    }

    /**
     * 获取所有产品ID列表
     */
    @GetMapping("/productIds")
    public Result<List<String>> getProductIds() {
        try {
            return Result.ok(resourceService.getAllProductIds());
        } catch (Exception e) {
            return Result.fail("获取产品列表失败：" + e.getMessage());
        }
    }

    /**
     * 核查资源：按产品ID返回总量汇总 + 明细聚合 + 文件级汇总
     */
    @GetMapping("/check")
    public Result<ResourceCheckResponse> checkResources(
            @RequestParam("productId") @NotBlank(message = "productId 不能为空") String productId,
            @RequestParam("batchNo") @NotBlank(message = "batchNo 不能为空") String batchNo,
            @RequestParam(required = false) String fileSource) {
        try {
            ResourceCheckResponse response = resourceService.getResourceCheckByProduct(productId, batchNo, fileSource);
            return Result.ok(response);
        } catch (Exception e) {
            return Result.fail("查询资源信息失败：" + e.getMessage());
        }
    }

    /**
     * 删除指定产品下、指定原始文件名的资源数据
     */
    @DeleteMapping("/deleteByFile")
    public Result<String> deleteByOriginalFileName(
            @RequestParam("productId") @NotBlank String productId,
            @RequestParam("batchNo") @NotBlank String batchNo,
            @RequestParam("originalFileName") @NotBlank String originalFileName) {
        try {
            int count = resourceService.deleteByOriginalFileName(productId, batchNo, originalFileName);
            return Result.ok("删除成功，共删除 " + count + " 条记录");
        } catch (Exception e) {
            return Result.fail("删除失败：" + e.getMessage());
        }
    }
}
