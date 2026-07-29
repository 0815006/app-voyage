package com.voyage.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 性能测试场景定义主表
 */
@Data
@TableName("perf_task_scene")
public class PerfTaskScene {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Integer sceneType;
    private String sceneName;
    private BigDecimal targetTpsRatio;
    private BigDecimal targetTotalTps;
    private String testObjective;
    private String implementationMethod;
    private String endCondition;
    private Integer isSelected;
    private Integer globalDuration;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;
}
