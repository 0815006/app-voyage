package com.voyage.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 场景执行结果交易明细表
 */
@Data
@TableName("perf_task_scene_result_detail")
public class PerfTaskSceneResultDetail {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long resultId;
    private Long tranId;
    private String tranName;
    private BigDecimal actualTps;
    private BigDecimal actualRt;
    private BigDecimal actualRt90;
    private BigDecimal actualRtMax;
    private BigDecimal actualSuccessRate;
    private Long totalRequestCount;
    private Long actualDbCount;
    private Integer isCompliant;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
}
