package com.voyage.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 性能测试数据准备方案主表(定性描述)
 */
@Data
@TableName("perf_data_plan")
public class PerfDataPlan {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;

    // 二、数据模型分析部分
    private String modelAnalysis;
    private String dataConstraint;

    // 三、数据构造设计部分
    private String dataSourceDesc;
    private String prepMethodDesc;
    private String cleaningRule;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date lastTime;
}
