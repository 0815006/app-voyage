-- ============================================================
-- Voyage Platform - 文件分析（Document Studio）模块 V1.6
-- 数据库: PostgreSQL 16+ (启用 pgvector 向量扩展)
-- 说明: 由 Flyway 在应用启动时自动执行，禁止手工执行
-- 设计: 文件分析页面（物理隔离工作区 SubDir + 轻量化解析存储）
--       · 按操作员工号(emp_no)隔离，各自维护自己的工作区
--       · 仅存文件元数据与 ai-tool-doc 预解析 Markdown，绝不存二进制 Blob
--       · doc_vector 面向长文档切片向量召回（预留，pgvector）
-- ============================================================

-- 向量检索扩展：涉及 AI 向量检索，脚本开头必须启用 pgvector
CREATE EXTENSION IF NOT EXISTS vector;

-- -----------------------------------------------------------
-- 1. 工作区（子目录）元数据表
--    物理磁盘根目录(workspace.root-path)下每行对应一个 UUID 隔离子目录
-- -----------------------------------------------------------
DROP TABLE IF EXISTS biz_workspace_doc_vector;
DROP TABLE IF EXISTS biz_workspace_file;
DROP TABLE IF EXISTS biz_session_text_analysis;
DROP TABLE IF EXISTS biz_workspace_sub_dir;

CREATE TABLE biz_workspace_sub_dir (
    sub_dir_id  UUID         NOT NULL DEFAULT gen_random_uuid(), -- 主键(同时为物理子目录名)
    emp_no      VARCHAR(7)   NOT NULL,                           -- 归属操作员工号(7位)
    dir_name    VARCHAR(255) NOT NULL,                           -- 工作区展示名称，如"09-07 季度报告质检"
    abs_path    VARCHAR(512) NOT NULL,                           -- 物理绝对路径，如 D:/data/voyage/workspace/<uuid>/
    create_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 创建时间
    update_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 更新时间
    PRIMARY KEY (sub_dir_id)
);

COMMENT ON TABLE  biz_workspace_sub_dir IS '文件分析工作区（物理隔离子目录）元数据表';
COMMENT ON COLUMN biz_workspace_sub_dir.sub_dir_id IS '主键UUID，同时作为物理磁盘隔离子目录名';
COMMENT ON COLUMN biz_workspace_sub_dir.emp_no IS '归属操作员工号(7位)，按操作者隔离';
COMMENT ON COLUMN biz_workspace_sub_dir.dir_name IS '工作区展示名称，如：09-07 季度报告质检';
COMMENT ON COLUMN biz_workspace_sub_dir.abs_path IS '物理绝对路径，如 D:/data/voyage/workspace/<uuid>/';
COMMENT ON COLUMN biz_workspace_sub_dir.create_time IS '创建时间';
COMMENT ON COLUMN biz_workspace_sub_dir.update_time IS '更新时间';

-- 按操作者快速检索历史工作区
CREATE INDEX idx_biz_sub_dir_emp_no ON biz_workspace_sub_dir (emp_no);
CREATE INDEX idx_biz_sub_dir_update_time ON biz_workspace_sub_dir (update_time);

-- 自动维护 update_time
CREATE TRIGGER trg_biz_workspace_sub_dir_update_time
BEFORE UPDATE ON biz_workspace_sub_dir
FOR EACH ROW EXECUTE FUNCTION set_update_time();

-- -----------------------------------------------------------
-- 2. 文件元数据表
--    仅存元数据与 ai-tool-doc 预解析文本，绝不存二进制 Blob
-- -----------------------------------------------------------
CREATE TABLE biz_workspace_file (
    file_id        UUID         NOT NULL DEFAULT gen_random_uuid(), -- 主键
    sub_dir_id     UUID         NOT NULL REFERENCES biz_workspace_sub_dir(sub_dir_id) ON DELETE CASCADE,
    file_name      VARCHAR(255) NOT NULL,          -- 原始文件名（磁盘同步同名存储）
    file_type      VARCHAR(32)  NOT NULL,          -- 类型: DOCX/PDF/TXT/MD/JSON/PNG
    file_size      BIGINT       NOT NULL DEFAULT 0,-- 文件大小(字节)
    parsed_markdown TEXT,                          -- ai-tool-doc 预解析出的 Markdown 文本(加速问答/轻量预览)
    parsed_schema  JSONB,                          -- 模板文件变量 Schema(ai-tool-doc inspect_template_schema)
    create_time    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (file_id)
);

COMMENT ON TABLE  biz_workspace_file IS '文件分析工作区文件元数据表（轻量化，不存二进制Blob）';
COMMENT ON COLUMN biz_workspace_file.file_id IS '主键UUID';
COMMENT ON COLUMN biz_workspace_file.sub_dir_id IS '所属工作区UUID(级联删除)';
COMMENT ON COLUMN biz_workspace_file.file_name IS '原始文件名，磁盘同步同名存储';
COMMENT ON COLUMN biz_workspace_file.file_type IS '文件类型: DOCX/PDF/TXT/MD/JSON/PNG';
COMMENT ON COLUMN biz_workspace_file.file_size IS '文件大小(字节)';
COMMENT ON COLUMN biz_workspace_file.parsed_markdown IS 'ai-tool-doc 预解析出的 Markdown 文本(加速问答与轻量预览)';
COMMENT ON COLUMN biz_workspace_file.parsed_schema IS '模板文件变量Schema JSONB(inspect_template_schema产物)';
COMMENT ON COLUMN biz_workspace_file.create_time IS '创建时间';

-- 文件所属工作区快速查询（文件树）
CREATE INDEX idx_biz_workspace_file_subdir ON biz_workspace_file (sub_dir_id);

-- -----------------------------------------------------------
-- 3. 向量索引表（用于长文档切片召回，预留）
-- -----------------------------------------------------------
CREATE TABLE biz_workspace_doc_vector (
    vector_id     UUID           NOT NULL DEFAULT gen_random_uuid(), -- 主键
    sub_dir_id    UUID           NOT NULL REFERENCES biz_workspace_sub_dir(sub_dir_id) ON DELETE CASCADE,
    file_id       UUID           NOT NULL REFERENCES biz_workspace_file(file_id) ON DELETE CASCADE,
    chunk_content TEXT           NOT NULL,          -- 切片内容
    embedding     vector(1536),                     -- pgvector 向量(预留嵌入维度 1536)
    create_time   TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (vector_id)
);

COMMENT ON TABLE  biz_workspace_doc_vector IS '文件分析文档切片向量索引表（长文档切片召回，预留）';
COMMENT ON COLUMN biz_workspace_doc_vector.vector_id IS '主键UUID';
COMMENT ON COLUMN biz_workspace_doc_vector.sub_dir_id IS '所属工作区UUID(级联删除)';
COMMENT ON COLUMN biz_workspace_doc_vector.file_id IS '所属文件UUID(级联删除)';
COMMENT ON COLUMN biz_workspace_doc_vector.chunk_content IS '切片内容';
COMMENT ON COLUMN biz_workspace_doc_vector.embedding IS 'pgvector 向量 embedding(1536)';
COMMENT ON COLUMN biz_workspace_doc_vector.create_time IS '创建时间';

-- 向量加速查询(HNSW 余弦相似)
CREATE INDEX idx_biz_doc_vector_embedding ON biz_workspace_doc_vector
    USING hnsw (embedding vector_cosine_ops);
-- 按文件/工作区检索切片
CREATE INDEX idx_biz_doc_vector_file ON biz_workspace_doc_vector (file_id);

-- -----------------------------------------------------------
-- 4. 文本分析会话表
--    每个工作区维护一段 Agent 对话上下文（JSONB），随 Chat SSE 追加
-- -----------------------------------------------------------
CREATE TABLE biz_session_text_analysis (
    session_id       UUID        NOT NULL DEFAULT gen_random_uuid(), -- 主键
    sub_dir_id       UUID        NOT NULL REFERENCES biz_workspace_sub_dir(sub_dir_id) ON DELETE CASCADE,
    session_title    VARCHAR(255) NOT NULL DEFAULT '文件分析',       -- 会话标题
    context_messages JSONB       NOT NULL DEFAULT '[]',              -- 对话上下文历史(Message 列表)
    execution_trace  JSONB       DEFAULT '[]',                       -- ReAct 思考与工具调用轨迹
    create_time      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (session_id)
);

COMMENT ON TABLE  biz_session_text_analysis IS '文件分析文本分析会话表';
COMMENT ON COLUMN biz_session_text_analysis.session_id IS '主键UUID';
COMMENT ON COLUMN biz_session_text_analysis.sub_dir_id IS '所属工作区UUID(级联删除)';
COMMENT ON COLUMN biz_session_text_analysis.session_title IS '会话标题';
COMMENT ON COLUMN biz_session_text_analysis.context_messages IS '对话上下文历史JSONB(Message列表)';
COMMENT ON COLUMN biz_session_text_analysis.execution_trace IS 'ReAct思考与Tool调用轨迹JSONB';
COMMENT ON COLUMN biz_session_text_analysis.create_time IS '创建时间';
COMMENT ON COLUMN biz_session_text_analysis.update_time IS '更新时间';

-- 工作区-会话检索
CREATE INDEX idx_biz_session_sub_dir ON biz_session_text_analysis (sub_dir_id);
CREATE INDEX idx_biz_session_update_time ON biz_session_text_analysis (update_time);

-- 自动维护 update_time
CREATE TRIGGER trg_biz_session_text_analysis_update_time
BEFORE UPDATE ON biz_session_text_analysis
FOR EACH ROW EXECUTE FUNCTION set_update_time();
