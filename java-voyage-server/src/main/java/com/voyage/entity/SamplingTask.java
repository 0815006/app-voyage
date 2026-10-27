package com.voyage.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通用采样任务实体（sampling_task）。
 * <p>
 * 死元组监测等采样场景的任务配置：仅存 connection_id 引用 db_connection_config，
 * 采样执行时由后端按归属操作者解密密码（零新增密码落盘）。
 * sampling_type 为通用架构的类型标识，当前固定 DEAD_TUPLE。
 * </p>
 */
@Data
@TableName("sampling_task")
public class SamplingTask {

    /** 主键，雪花 ID (VARCHAR 32) */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 归属操作员工号（7位，由后端从 EmpContext 注入，不接受前端指定） */
    @TableField("emp_no")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String empNo;

    /** 任务名称 */
    private String taskName;

    /** 复用的数据库连接 ID（db_connection_config.id） */
    private String connectionId;

    /** 采样类型：DEAD_TUPLE=死元组监测采样（预留扩展） */
    private String samplingType;

    /** 自定义采样 SQL（后端卡口仅允许 SELECT/WITH 开头） */
    private String customSql;

    /** 采样窗口开始时间（前端日期控件上送/回显均为空格分隔格式，Jackson 默认 ISO 解析不了，显式声明格式） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    /** 采样窗口结束时间，到达后任务自动置 COMPLETED */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    /** 采样频率（秒/次） */
    private Integer frequency;

    /** 任务状态：RUNNING / STOPPED / COMPLETED */
    private String status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
