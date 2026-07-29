package com.voyage.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("meta_enum_library")
public class MetaEnumLibrary {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String enumKey;

    private String enumName;

    private String items;   // JSON: [{"val":"0","desc":"男"},{"val":"1","desc":"女"}]
}
