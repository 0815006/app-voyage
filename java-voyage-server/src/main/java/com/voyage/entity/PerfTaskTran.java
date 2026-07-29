package com.voyage.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 联机交易调研与指标方案表
 */
@Data
@TableName("perf_task_tran")
public class PerfTaskTran {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private String moduleName;
    private String tranName;
    private String tranCode;
    private String interfaceType;

    // 1. 生产现状调研情况
    private BigDecimal tranDailyVol;
    private BigDecimal tranPeakHourVol;
    private BigDecimal tranPeakTps;
    private BigDecimal tranAvgRt;
    private BigDecimal tranMaxRt;

    // 2. 业务交易指标明细要求
    private BigDecimal targetDailyVol;
    private BigDecimal targetPeakHourVol;
    private BigDecimal targetTps;
    private BigDecimal targetRt;
    private BigDecimal targetMaxRt;
    private BigDecimal targetSuccessRate;
    private BigDecimal targetThinkTime;

    // 3. 交易选择结果
    private Integer isSelected;
    private String selectReason;

    // 4. 指标推算审计
    @TableField("indicator_source")
    private Integer indicatorSource;
    @TableField("calculation_process")
    private String calculationProcess;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date lastTime;
}
