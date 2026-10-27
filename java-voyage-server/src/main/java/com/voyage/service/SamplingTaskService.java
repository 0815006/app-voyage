package com.voyage.service;

import com.voyage.entity.SamplingHistory;
import com.voyage.entity.SamplingTask;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通用采样监测 Service 接口（当前服务于死元组监测，按操作员工号隔离）。
 * <p>
 * 任务生命周期：STOPPED → 启动 → RUNNING → 停止 → STOPPED / 到达结束时间 → COMPLETED。
 * 启动后按任务频率在独立调度线程上执行自定义 SQL，
 * 结果逐行原样序列化为 JSON 落入通用明细表 sampling_history。
 */
public interface SamplingTaskService {

    /**
     * 结果集列描述符（JDBC 元数据权威解析，不执行查询）。
     *
     * @param column   列标签（含别名）
     * @param label    类型名（如 BIGINT/VARCHAR/TIMESTAMP）
     * @param numeric  是否数值列（前端波动图纵轴/展示列候选）
     * @param temporal 是否时间列
     */
    record ColumnDescriptor(String column, String label, boolean numeric, boolean temporal) {
    }

    /** 查询当前操作者全部任务（按创建时间倒序）。 */
    List<SamplingTask> listTasks(String empNo);

    /** 查询单个任务（校验归属）。 */
    SamplingTask getTask(String empNo, String id);

    /** 新增或更新任务（运行中的任务禁止编辑；custom_sql 仅允许 SELECT/WITH）。 */
    SamplingTask saveTask(String empNo, SamplingTask task);

    /** 删除任务（运行中先停止调度，再级联删除历史明细）。 */
    void deleteTask(String empNo, String id);

    /** 启动任务：解密连接、建立临时连接池并按频率开始采样。 */
    SamplingTask startTask(String empNo, String id);

    /** 停止任务：销毁调度线程与临时连接池，状态置 STOPPED。 */
    SamplingTask stopTask(String empNo, String id);

    /** 查询指定任务时间窗口内的采样明细（校验任务归属）。 */
    List<SamplingHistory> listHistory(String empNo, String taskId, LocalDateTime start, LocalDateTime end);

    /**
     * 解析采样 SQL 的结果集列结构（供前端筛选器确定维度列/展示列候选）。
     * 通过 JDBC prepare 阶段元数据获取，不执行查询、不返回业务数据；失败时抛业务异常。
     */
    List<ColumnDescriptor> previewColumns(String empNo, String connectionId, String customSql);
}
