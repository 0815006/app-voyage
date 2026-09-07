package com.voyage.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件分析文本分析会话实体（biz_session_text_analysis）。
 * <p>
 * 一个工作区对应一个会话，context_messages / execution_trace 以 JSON 文本形式
 * 存入 JSONB 列（借助 datasource stringtype=unspecified 由 PG 自动转换）。
 */
@Data
@TableName("biz_session_text_analysis")
public class SessionTextAnalysis {

    /** 主键 UUID 字符串 */
    @TableId(value = "session_id", type = IdType.INPUT)
    private String sessionId;

    /** 所属工作区 UUID */
    private String subDirId;

    /** 会话标题 */
    private String sessionTitle;

    /** 对话上下文历史 JSON（Message 列表） */
    @TableField("context_messages")
    private String contextMessages;

    /** ReAct 思考与工具调用轨迹 JSON */
    @TableField("execution_trace")
    private String executionTrace;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
