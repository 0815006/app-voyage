package com.voyage.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voyage.common.EmpContext;
import com.voyage.common.Result;
import com.voyage.entity.SamplingHistory;
import com.voyage.entity.SamplingTask;
import com.voyage.service.SamplingTaskService;
import com.voyage.service.SamplingTaskService.ColumnDescriptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 死元组监测采样 Controller。
 * <p>
 * 后端保持完全通用：任务 CRUD、启停控制与采样明细查询，
 * 明细以「采样时间 + 原始行 JSON」原样返回，不做任何业务语义解析
 * （死元组表名筛选、指标计算、波动图绘制均由前端垂直闭环完成）。
 * 所有接口按操作员工号(EmpContext)隔离。
 *
 * @author voyage
 */
@Slf4j
@RestController
@RequestMapping("/api/sampling")
public class SamplingController {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final SamplingTaskService samplingTaskService;

    public SamplingController(SamplingTaskService samplingTaskService) {
        this.samplingTaskService = samplingTaskService;
    }

    /** 单行采样明细（采样时间戳 + 原始行 JSON 平铺）。 */
    public record SamplingHistoryRow(LocalDateTime samplingTime, Map<String, Object> row) {
    }

    // ==================== 任务管理 ====================

    /** 任务列表（按创建时间倒序）。 */
    @GetMapping("/tasks")
    public Result<List<SamplingTask>> listTasks() {
        return Result.ok(samplingTaskService.listTasks(EmpContext.getEmpNo()));
    }

    /** 任务详情。 */
    @GetMapping("/task/{id}")
    public Result<SamplingTask> getTask(@PathVariable String id) {
        return Result.ok(samplingTaskService.getTask(EmpContext.getEmpNo(), id));
    }

    /** 新增或更新任务（运行中禁止编辑；SQL 卡口仅允许 SELECT/WITH）。 */
    @PostMapping("/task")
    public Result<SamplingTask> saveTask(@RequestBody SamplingTask task) {
        return Result.ok(samplingTaskService.saveTask(EmpContext.getEmpNo(), task));
    }

    /** 删除任务（运行中先停止调度，级联删除历史明细）。 */
    @DeleteMapping("/task/{id}")
    public Result<Void> deleteTask(@PathVariable String id) {
        samplingTaskService.deleteTask(EmpContext.getEmpNo(), id);
        return Result.ok();
    }

    // ==================== 生命周期控制 ====================

    /**
     * 解析采样 SQL 的结果集列结构（JDBC 元数据，不执行查询）：
     * 供前端确定表范围维度列与展示列（纵轴数值列）候选，兼做保存前语法校验。
     */
    @PostMapping("/preview-columns")
    public Result<List<ColumnDescriptor>> previewColumns(@RequestBody Map<String, String> body) {
        return Result.ok(samplingTaskService.previewColumns(
                EmpContext.getEmpNo(), body.get("connectionId"), body.get("customSql")));
    }

    /** 启动任务：按频率开始周期采样。 */
    @PostMapping("/task/{id}/start")
    public Result<SamplingTask> startTask(@PathVariable String id) {
        return Result.ok(samplingTaskService.startTask(EmpContext.getEmpNo(), id));
    }

    /** 停止任务：销毁调度线程与临时连接池。 */
    @PostMapping("/task/{id}/stop")
    public Result<SamplingTask> stopTask(@PathVariable String id) {
        return Result.ok(samplingTaskService.stopTask(EmpContext.getEmpNo(), id));
    }

    // ==================== 采样明细（通用 JSON 行） ====================

    /**
     * 查询任务在时间窗口内的采样明细，row_data 原样解析为 JSON 对象返回。
     *
     * @param start 起始时间（可选，ISO 格式 yyyy-MM-ddTHH:mm:ss）
     * @param end   结束时间（可选）
     */
    @GetMapping("/task/{id}/history")
    public Result<List<SamplingHistoryRow>> listHistory(
            @PathVariable String id,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        List<SamplingHistory> histories =
                samplingTaskService.listHistory(EmpContext.getEmpNo(), id, start, end);
        return Result.ok(histories.stream().map(h -> {
            Map<String, Object> row;
            try {
                row = OBJECT_MAPPER.readValue(h.getRowData(), new TypeReference<Map<String, Object>>() {
                });
            } catch (Exception e) {
                log.warn("[采样明细] row_data 解析失败, historyId={}: {}", h.getId(), e.getMessage());
                row = Map.of();
            }
            return new SamplingHistoryRow(h.getSamplingTime(), row);
        }).toList());
    }
}
