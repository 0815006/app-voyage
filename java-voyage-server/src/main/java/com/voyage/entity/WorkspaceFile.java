package com.voyage.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件分析工作区文件元数据实体（biz_workspace_file）。
 * <p>
 * 轻量化存储：仅存元数据与 ai-tool-doc 预解析出的 Markdown 文本，绝不存二进制 Blob。
 * 二进制文件保存在所属工作区(sub_dir_id)的物理目录下，文件名与 file_name 一致。
 */
@Data
@TableName("biz_workspace_file")
public class WorkspaceFile {

    /** 主键 UUID 字符串 */
    @TableId(value = "file_id", type = IdType.INPUT)
    private String fileId;

    /** 所属工作区 UUID */
    private String subDirId;

    /** 原始文件名（磁盘同步同名存储） */
    private String fileName;

    /** 文件类型：DOCX/PDF/TXT/MD/JSON/PNG 等（大写扩展名） */
    private String fileType;

    /** 文件大小（字节） */
    private Long fileSize;

    /** ai-tool-doc 预解析出的 Markdown 文本（加速问答与轻量预览） */
    @TableField("parsed_markdown")
    private String parsedMarkdown;

    /** 模板文件变量 Schema JSON（inspect_template_schema 产物） */
    @TableField("parsed_schema")
    private String parsedSchema;

    /** 创建时间 */
    private LocalDateTime createTime;
}
