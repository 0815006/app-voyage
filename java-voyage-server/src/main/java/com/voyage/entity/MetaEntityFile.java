package com.voyage.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("meta_entity_file")
public class MetaEntityFile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long modelId;

    private String fileName;

    private String fileType;        // PREVIEW / FORMAL

    private String storagePath;

    private Integer rowCount;

    private String status;          // SUCCESS / FAILED / RUNNING

    private String createUser;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    private Integer durationMs;

    private String errorMsg;

    private String tempPath;
}
