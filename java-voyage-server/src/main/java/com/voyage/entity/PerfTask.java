package com.voyage.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 性能测试任务主表
 */
@Data
@TableName("perf_task")
public class PerfTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String productId;
    private String batchNo;
    private String taskName;
    private String testTaskNo;
    private String prodTaskNo;
    private String reqNo;
    private String projName;
    private String projNo;
    private String testDept;
    private String devDept;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date startTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date endTime;
    private String perfManager;
    private String testArch;
    private String projectManager;
    private String recorderRange;
    private String creatorId;
    private Integer status;

    // 系统汇总统计字段
    private BigDecimal totalUserCount;
    private BigDecimal dailyOnlineUserCount;
    private BigDecimal dailyPeakTps;
    private BigDecimal annualPeakTps;
    private BigDecimal selectedTranTpsSum;

    // 批量汇总统计字段
    private String batchTotalDuration;
    private String batchTotalDataVolume;
    private String batchParallelDegree;
    private String batchMaxParallelCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date lastTime;
}
