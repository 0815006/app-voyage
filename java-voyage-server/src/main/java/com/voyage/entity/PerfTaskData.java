package com.voyage.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 性能测试数据准备方案表
 */
@Data
@TableName("perf_task_data")
public class PerfTaskData {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private String tableName;
    private Long tableRowsCount;
    private BigDecimal tableGrowthRate;
    private Long targetRowsCount;
    private Integer prepMethod;
    private String dataDistDesc;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date lastTime;
}
