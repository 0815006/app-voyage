package com.voyage.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("meta_sequence_tracker")
public class MetaSequenceTracker {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String targetType;      // SEQ / REF_FILE

    private String targetId;        // modelId:fieldKey or refFileId

    private Long currentValue;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
