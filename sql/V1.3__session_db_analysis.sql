-- ============================================================
-- Voyage Platform - 数据库性能分析会话表 V1.3
-- 数据库: PostgreSQL 16+
-- 说明: 由 Flyway 在应用启动时自动执行，禁止手工执行
-- 设计: 零密码落盘，仅存储会话元数据快照与对话上下文/执行轨迹
-- ============================================================

-- 启用 pgcrypto 以使用 gen_random_uuid()（uuid 主键）
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- -----------------------------------------------------------
-- 数据库性能分析会话表
-- 记录选中 DB 元数据快照(不含密码)、对话历史、ReAct 执行轨迹
-- -----------------------------------------------------------
DROP TABLE IF EXISTS session_db_analysis;
CREATE TABLE session_db_analysis (
  id               VARCHAR(32)  NOT NULL,                      -- 主键(雪花ID)
  session_title    VARCHAR(255) NOT NULL,                      -- 会话标题
  selected_db_meta JSONB        NOT NULL DEFAULT '[]',         -- 选中 DB 元数据快照(别名/IP/端口/库名/方言/用户，无密码)
  context_messages JSONB        NOT NULL DEFAULT '[]',         -- 对话历史上下文(Message 列表)
  execution_trace  JSONB        DEFAULT '[]',                  -- ReAct 思考与工具调用轨迹
  created_at       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,     -- 创建时间
  update_time      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,     -- 更新时间
  PRIMARY KEY (id)
);

COMMENT ON TABLE  session_db_analysis IS '数据库性能分析会话表';
COMMENT ON COLUMN session_db_analysis.id IS '主键，雪花ID(VARCHAR 32)';
COMMENT ON COLUMN session_db_analysis.session_title IS '会话标题，如：主从库慢SQL与索引对比分析';
COMMENT ON COLUMN session_db_analysis.selected_db_meta IS '选中数据库元数据快照JSONB(仅别名/IP/端口/库名/方言/用户，零密码落盘)';
COMMENT ON COLUMN session_db_analysis.context_messages IS '对话历史上下文JSONB(Message列表)';
COMMENT ON COLUMN session_db_analysis.execution_trace IS 'ReAct思考与Tool调用轨迹JSONB(Thought/Tool Call History)';
COMMENT ON COLUMN session_db_analysis.created_at IS '创建时间';
COMMENT ON COLUMN session_db_analysis.update_time IS '更新时间';