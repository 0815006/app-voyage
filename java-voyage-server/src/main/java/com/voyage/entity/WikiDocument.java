package com.voyage.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Wiki文档实体类。
 * type: 1-文件夹, 2-文档
 * parent_id: 父级ID，"0"表示根目录
 * emp_no: 归属操作员工号，完全按人隔离（READ_ONLY，不接受前端指定/回传）
 */
@Data
@TableName("wiki_document")
public class WikiDocument {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String title;

    private String content;

    private Integer type;

    private String parentId;

    private Integer sortOrder;

    /** 归属操作员工号（7位，仅由后端从 EmpContext 注入，反序列化时忽略前端传入值） */
    @TableField("emp_no")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String empNo;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
