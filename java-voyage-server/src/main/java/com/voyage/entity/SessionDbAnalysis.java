package com.voyage.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据库性能分析会话实体（session_db_analysis）。
 * <p>
 * 遵循 PRD「零密码落盘」设计：selected_db_meta 仅存储选中数据库的元数据快照
 * （别名/IP/端口/库名/方言/用户），密码只存在于单次请求的后端内存中。
 * context_messages / execution_trace 以 JSON 文本形式存储。
 */
@Data
@TableName("session_db_analysis")
public class SessionDbAnalysis {

    /** 主键，雪花 ID (VARCHAR 32) */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 会话标题，如："主从库慢SQL与索引对比分析" */
    private String sessionTitle;

    /** 选中数据库元数据快照 JSON 数组（不含密码） */
    @TableField("selected_db_meta")
    private String selectedDbMeta;

    /** 对话历史上下文 JSON（Message 列表） */
    @TableField("context_messages")
    private String contextMessages;

    /** ReAct 思考与工具调用轨迹 JSON */
    @TableField("execution_trace")
    private String executionTrace;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updateTime;
}