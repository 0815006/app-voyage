package com.voyage.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通用采样结果明细实体（sampling_history）。
 * <p>
 * 行级 JSON 平铺存储：row_data 为纯通用 JSONB 大字段（借助 datasource
 * stringtype=unspecified 由 String 直插），SQL 查出的一行原样序列化，
 * 后端不感知任何业务语义，解析全部在前端完成。
 * </p>
 */
@Data
@TableName("sampling_history")
public class SamplingHistory {

    /** 主键自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联采样任务 ID */
    private String taskId;

    /** 采样时间戳（同一批次执行拿到的多行数据共享此时间戳） */
    private LocalDateTime samplingTime;

    /** 纯通用行数据 JSON 文本（存入 JSONB 列） */
    @TableField("row_data")
    private String rowData;

    /** 入库时间 */
    private LocalDateTime createTime;
}
