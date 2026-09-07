package com.voyage.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件分析工作区（物理隔离子目录）实体（biz_workspace_sub_dir）。
 * <p>
 * 每行对应物理磁盘 workspace.root-path 下一个 UUID 子目录，
 * 按操作员工号(emp_no)隔离。数据库仅保存元数据，文件二进制落盘在 abs_path。
 */
@Data
@TableName("biz_workspace_sub_dir")
public class WorkspaceSubDir {

    /** 主键 UUID 字符串（同时作为物理隔离子目录名） */
    @TableId(value = "sub_dir_id", type = IdType.INPUT)
    private String subDirId;

    /** 归属操作员工号（7位） */
    private String empNo;

    /** 工作区展示名称，如 "09-07 季度报告质检" */
    private String dirName;

    /** 物理绝对路径，如 D:/data/voyage/workspace/<uuid>/ */
    private String absPath;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
