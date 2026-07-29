package com.voyage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.voyage.dto.DocGenerateContext;
import com.voyage.entity.*;
import com.voyage.mapper.*;
import com.voyage.service.PerformanceDocService;
import com.voyage.util.PoiEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PerformanceDocServiceImpl implements PerformanceDocService {

    @Value("${performance.template-path}")
    private String templatePath;

    @Value("${performance.doc-path}")
    private String docPath;

    private final PerfTaskMapper taskMapper;
    private final PerfTaskTranMapper tranMapper;
    private final PerfTaskBatchMapper batchMapper;
    private final PerfTaskSceneMapper sceneMapper;
    private final PerfTaskSceneDetailMapper sceneDetailMapper;
    private final PerfDataPlanMapper dataPlanMapper;
    private final PerfDataDetailMapper dataDetailMapper;
    private final PerformanceResourceInfoMapper resourceMapper;

    private static final String TEMPLATE_FILENAME = "性能测试方案模板poiEngine.docx";

    @Override
    public String generateDoc(Long taskId) {
        DocGenerateContext ctx = assembleData(taskId);
        if (ctx.getTask() == null) {
            throw new RuntimeException("任务不存在: " + taskId);
        }

        File docDir = new File(docPath);
        if (!docDir.exists()) {
            docDir.mkdirs();
        }

        File tmpl = new File(templatePath + TEMPLATE_FILENAME);
        if (!tmpl.exists()) {
            throw new RuntimeException("模板文件不存在: " + templatePath + TEMPLATE_FILENAME);
        }

        String safeName = sanitizeFilename(ctx.getTask().getTaskName());
        String batchNo = ctx.getTask().getBatchNo() != null ? ctx.getTask().getBatchNo() : "";
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String outputFilename = batchNo + "批次_" + safeName + "_性能测试方案_" + timestamp + ".docx";

        try {
            Map<String, Object> dataModel = buildDataModel(ctx);

            try (InputStream is = new FileInputStream(tmpl);
                 OutputStream os = new FileOutputStream(docPath + outputFilename)) {
                PoiEngine.fillModel(is, os, dataModel);
            }
            log.info("方案文档生成成功: {}", outputFilename);
            return outputFilename;
        } catch (Exception e) {
            log.error("方案文档生成失败, taskId={}", taskId, e);
            throw new RuntimeException("文档生成失败: " + e.getMessage(), e);
        }
    }

    private DocGenerateContext assembleData(Long taskId) {
        DocGenerateContext ctx = new DocGenerateContext();

        ctx.setTask(taskMapper.selectById(taskId));

        ctx.setTranList(tranMapper.selectList(
                new LambdaQueryWrapper<PerfTaskTran>().eq(PerfTaskTran::getTaskId, taskId)));

        ctx.setBatchList(batchMapper.selectList(
                new LambdaQueryWrapper<PerfTaskBatch>().eq(PerfTaskBatch::getTaskId, taskId)));

        ctx.setDataPlan(dataPlanMapper.selectOne(
                new LambdaQueryWrapper<PerfDataPlan>().eq(PerfDataPlan::getTaskId, taskId)));

        ctx.setDataDetailList(dataDetailMapper.selectList(
                new LambdaQueryWrapper<PerfDataDetail>().eq(PerfDataDetail::getTaskId, taskId)));

        List<PerfTaskScene> scenes = sceneMapper.selectList(
                new LambdaQueryWrapper<PerfTaskScene>().eq(PerfTaskScene::getTaskId, taskId));
        List<DocGenerateContext.SceneWithDetails> sceneWithDetails = new ArrayList<>();
        if (scenes != null) {
            for (PerfTaskScene scene : scenes) {
                DocGenerateContext.SceneWithDetails swd = new DocGenerateContext.SceneWithDetails();
                swd.setScene(scene);
                swd.setSceneDetailList(sceneDetailMapper.selectList(
                        new LambdaQueryWrapper<PerfTaskSceneDetail>().eq(PerfTaskSceneDetail::getSceneId, scene.getId())));
                sceneWithDetails.add(swd);
            }
        }
        ctx.setSceneList(sceneWithDetails);

        PerfTask task = ctx.getTask();
        if (task != null) {
            ctx.setResourceList(resourceMapper.selectList(
                    new LambdaQueryWrapper<PerformanceResourceInfo>()
                            .eq(PerformanceResourceInfo::getProductId, task.getProductId())
                            .eq(StringUtils.hasText(task.getBatchNo()), PerformanceResourceInfo::getBatchNo, task.getBatchNo())
                            .eq(PerformanceResourceInfo::getFileSource, "资源申请表")));
        }
        return ctx;
    }

    private Map<String, Object> buildDataModel(DocGenerateContext ctx) {
        Map<String, Object> map = new HashMap<>();

        PerfTask t = ctx.getTask();
        if (t != null) {
            map.put("task_name", nvl(t.getTaskName()));
            map.put("test_task_no", nvl(t.getTestTaskNo()));
            map.put("prod_task_no", nvl(t.getProdTaskNo()));
            map.put("product_id", nvl(t.getProductId()));
            map.put("batch_no", nvl(t.getBatchNo()));
            map.put("req_no", nvl(t.getReqNo()));
            map.put("proj_name", nvl(t.getProjName()));
            map.put("proj_no", nvl(t.getProjNo()));
            map.put("test_dept", nvl(t.getTestDept()));
            map.put("dev_dept", nvl(t.getDevDept()));
            map.put("perf_manager", nvl(t.getPerfManager()));
            map.put("test_arch", nvl(t.getTestArch()));
            map.put("project_manager", nvl(t.getProjectManager()));
            map.put("total_user_count", nvl(t.getTotalUserCount()));
            map.put("daily_online_user_count", nvl(t.getDailyOnlineUserCount()));
            map.put("daily_peak_tps", nvl(t.getDailyPeakTps()));
            map.put("annual_peak_tps", nvl(t.getAnnualPeakTps()));
            map.put("selected_tran_tps_sum", nvl(t.getSelectedTranTpsSum()));
            map.put("start_time", formatDate(t.getStartTime()));
            map.put("end_time", formatDate(t.getEndTime()));
            map.put("batch_total_duration", nvl(t.getBatchTotalDuration()));
            map.put("batch_total_data_volume", nvl(t.getBatchTotalDataVolume()));
            map.put("batch_parallel_degree", nvl(t.getBatchParallelDegree()));
            map.put("batch_max_parallel_count", nvl(t.getBatchMaxParallelCount()));
        }

        // tran_list
        List<PerfTaskTran> rawTranList = ctx.getTranList();
        if (rawTranList != null && !rawTranList.isEmpty()) {
            List<Map<String, Object>> tranRows = new ArrayList<>();
            int idx = 1;
            for (PerfTaskTran t2 : rawTranList) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("serialNumber",      idx++);
                row.put("moduleName",        nvl(t2.getModuleName()));
                row.put("interfaceType",     nvl(t2.getInterfaceType()));
                row.put("tranName",          nvl(t2.getTranName()));
                row.put("tranCode",          nvl(t2.getTranCode()));
                row.put("targetTps",         nvl(t2.getTargetTps()));
                row.put("targetRt",          nvl(t2.getTargetRt()));
                row.put("targetSuccessRate", nvl(t2.getTargetSuccessRate()));
                tranRows.add(row);
            }
            map.put("tran_list", tranRows);
        } else {
            map.put("tran_list", Collections.emptyList());
        }

        // batch_list
        List<PerfTaskBatch> rawBatchList = ctx.getBatchList();
        if (rawBatchList != null && !rawBatchList.isEmpty()) {
            List<Map<String, Object>> batchRows = new ArrayList<>();
            int idx = 1;
            for (PerfTaskBatch b : rawBatchList) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("serialNumber",    idx++);
                row.put("jobName",         nvl(b.getJobName()));
                row.put("jobDuration",     nvl(b.getJobDuration()));
                row.put("jobDataVolume",   nvl(b.getJobDataVolume()));
                row.put("jobNo",           nvl(b.getJobNo()));
                row.put("jobCount",        nvl(b.getJobCount()));
                row.put("jobParallelMode", nvl(b.getJobParallelMode()));
                row.put("jobDesc",         nvl(b.getJobDesc()));
                row.put("selectReason",    nvl(b.getSelectReason()));
                batchRows.add(row);
            }
            map.put("batch_list", batchRows);
        } else {
            map.put("batch_list", Collections.emptyList());
        }

        // data_detail_list
        List<PerfDataDetail> rawDetailList = ctx.getDataDetailList();
        if (rawDetailList != null && !rawDetailList.isEmpty()) {
            List<Map<String, Object>> detailRows = new ArrayList<>();
            int idx = 1;
            for (PerfDataDetail d : rawDetailList) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("serialNumber",   idx++);
                row.put("dataType",       nvl(d.getDataType()));
                row.put("tableNameEn",    nvl(d.getTableNameEn()));
                row.put("tableNameCn",    nvl(d.getTableNameCn()));
                row.put("tableRowsCount", nvl(d.getTableRowsCount()));
                row.put("targetRowsCount",nvl(d.getTargetRowsCount()));
                row.put("prepMethod",     nvl(d.getPrepMethod()));
                row.put("dataDistDesc",   nvl(d.getDataDistDesc()));
                detailRows.add(row);
            }
            map.put("data_detail_list", detailRows);
        } else {
            map.put("data_detail_list", Collections.emptyList());
        }

        // resource_list
        List<PerformanceResourceInfo> rawResList = ctx.getResourceList();
        if (rawResList != null && !rawResList.isEmpty()) {
            List<Map<String, Object>> resRows = new ArrayList<>();
            int idx = 1;
            for (PerformanceResourceInfo r : rawResList) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("serialNumber",                  r.getSerialNumber() != null ? r.getSerialNumber() : idx);
                idx++;
                row.put("serviceName",                   nvl(r.getServiceName()));
                row.put("systemPlatform",                nvl(r.getSystemPlatform()));
                row.put("paasPlatformType",              nvl(r.getPaasPlatformType()));
                row.put("cpuCores",                      nvl(r.getCpuCores()));
                row.put("memoryGb",                      nvl(r.getMemoryGb()));
                row.put("dedicatedStorageGb",            nvl(r.getDedicatedStorageGb()));
                row.put("sanStorageGb",                  nvl(r.getSanStorageGb()));
                row.put("nasStorageGb",                  nvl(r.getNasStorageGb()));
                row.put("operatingSystem",               nvl(r.getOperatingSystem()));
                row.put("middleware",                    nvl(r.getMiddleware()));
                row.put("middlewareReasonBelowBaseline", nvl(r.getMiddlewareReasonBelowBaseline()));
                row.put("deploymentLocation",            nvl(r.getDeploymentLocation()));
                row.put("networkDeployment",             nvl(r.getNetworkDeployment()));
                row.put("hostname",                      nvl(r.getHostname()));
                row.put("ipAddress",                     nvl(r.getIpAddress()));
                row.put("remarks",                       nvl(r.getRemarks()));
                resRows.add(row);
            }
            map.put("resource_list", resRows);
        } else {
            map.put("resource_list", Collections.emptyList());
        }

        PerfDataPlan plan = ctx.getDataPlan();
        if (plan != null) {
            map.put("model_analysis", nvl(plan.getModelAnalysis()));
            map.put("data_constraint", nvl(plan.getDataConstraint()));
            map.put("data_source_desc", nvl(plan.getDataSourceDesc()));
            map.put("prep_method_desc", nvl(plan.getPrepMethodDesc()));
            map.put("cleaning_rule", nvl(plan.getCleaningRule()));
        }

        map.put("scene_list", ctx.getSceneList() != null ? ctx.getSceneList() : Collections.emptyList());

        log.debug("DataModel keys: {}", map.keySet());
        return map;
    }

    private String nvl(Object obj) {
        return obj == null ? "" : obj.toString();
    }

    private String formatDate(Date date) {
        if (date == null) return "";
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date);
    }

    private String sanitizeFilename(String name) {
        if (name == null) return "未命名";
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    @Override
    public List<DocFileInfo> listDocs() {
        List<DocFileInfo> fileList = new ArrayList<>();
        File directory = new File(docPath);
        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && file.getName().endsWith(".docx")) {
                        DocFileInfo info = new DocFileInfo();
                        info.setFileName(file.getName());
                        info.setFileSize(file.length());
                        info.setLastModified(new Date(file.lastModified()));
                        fileList.add(info);
                    }
                }
            }
        }
        fileList.sort((a, b) -> b.getLastModified().compareTo(a.getLastModified()));
        return fileList;
    }

    @Override
    public boolean deleteDoc(String fileName) {
        File file = new File(docPath + fileName);
        if (file.exists() && file.isFile()) {
            return file.delete();
        }
        return false;
    }
}
