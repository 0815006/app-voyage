package com.voyage.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("meta_file_model")
public class MetaFileModel {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String modelName;

    private Integer hasHeader;

    private Integer hasFooter;

    private String splitType;   // DELIMITER / FIXED

    private String delimiter;

    private String lineEndingChar;

    private String encoding;    // UTF-8 / GBK

    private Integer maxRowsLimit;

    private String ownerId;

    private String status;      // DRAFT / PUBLISHED / DISABLED

    private Integer modelVersion;

    private String sharedWith;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
