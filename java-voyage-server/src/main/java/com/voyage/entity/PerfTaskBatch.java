package com.voyage.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 批量作业调研与指标方案表
 */
@Data
@TableName("perf_task_batch")
public class PerfTaskBatch {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;

    // 基础属性
    private String jobNo;
    private String jobName;
    private String jobCount;
    private String jobParallelMode;
    private String jobDesc;
    private String jobTriggerCond;
    private String jobPreName;
    private String jobConcurrentNames;
    private String jobFrequency;

    // 生产调研指标
    private String jobDataType;
    private String jobDataVolume;
    private String jobActualDuration;
    private String jobDuration;
    private String jobExecTimePoint;

    // 方案与机制
    private String isMixedLink;
    private String mixedTranNames;
    private String hasRetry;
    private String retryDesc;

    // 交易选择结果
    @TableField("select_reason")
    private String selectReason;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date lastTime;
}
