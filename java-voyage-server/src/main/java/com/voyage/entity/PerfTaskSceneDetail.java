package com.voyage.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 场景内交易配置明细表
 */
@Data
@TableName("perf_task_scene_detail")
public class PerfTaskSceneDetail {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sceneId;
    private Long tranId;
    private String tranName;
    private BigDecimal targetTps;
    private BigDecimal targetRt;
    private BigDecimal targetSuccessRate;
    private Integer vuCount;
    private Integer rampUp;
    private BigDecimal pacing;
    private BigDecimal throughputTimer;
    private Integer iterations;
}
