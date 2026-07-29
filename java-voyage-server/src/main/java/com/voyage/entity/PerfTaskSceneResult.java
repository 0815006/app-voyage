package com.voyage.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 场景执行结果主表
 */
@Data
@TableName("perf_task_scene_result")
public class PerfTaskSceneResult {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sceneId;
    private Integer roundNumber;
    private String testEnv;
    private Integer runStatus;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date runStartTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date runEndTime;

    private Integer isStandard;
    private BigDecimal actualTotalTps;
    private Integer resultStatus;
    private String summaryRemark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
}
