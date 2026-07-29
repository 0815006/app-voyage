package com.voyage.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 性能测试数据准备明细表(表级规模)
 */
@Data
@TableName("perf_data_detail")
public class PerfDataDetail {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Integer dataType; // 数据分类(1:核心业务表, 2:基础数据/码表)

    // 对应Word表格与Excel导入列
    private String tableNameEn;
    private String tableNameCn;

    // 生产存量指标
    private Long tableRowsCount;
    private BigDecimal tableGrowthRate;

    // 方案目标指标
    private Long targetRowsCount;
    private String dataDistDesc;

    // 准备方式
    private String prepMethod;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date lastTime;
}
