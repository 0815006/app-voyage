package com.voyage.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("meta_ref_file")
public class MetaRefFile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String refName;

    private String filePath;

    private String parseType;       // DELIMITER / FIXED

    private String delimiter;

    private String columnMapping;   // JSON: {"userName":1,"age":2}
}
