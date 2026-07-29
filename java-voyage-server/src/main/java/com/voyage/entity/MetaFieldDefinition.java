package com.voyage.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("meta_field_definition")
public class MetaFieldDefinition {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long modelId;

    private String section;     // FILENAME / HEADER / BODY / FOOTER

    private Long parentId;

    private String fieldKey;

    private String fieldName;

    private Integer sortIndex;  // backend real sort

    private Integer level;      // 1 or 2

    private Integer length;

    private Integer isRequired;     // 1-必填，0-非必填

    private String paddingDirection; // LEFT / RIGHT / NONE

    private String paddingChar;

    private String ruleType;    // FIXED, DATE, ENUM, REF_FILE, REF_FIELD, SEQ, SEQUENCE, SUM, COUNT, RANDOM, RANDOM_NUM, RANDOM_CN, RANDOM_UUID, AMOUNT, BATCH_NO, EXPR, EXPRESSION

    private String refFieldKey;

    private String refEnumKey;

    private String refSequenceKey;

    private Long refFileId;

    private String ruleConfigJson;
}
