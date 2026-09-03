-- ============================================================
-- Voyage Platform - Wiki 文档模块建表 V1.2
-- 数据库: PostgreSQL 16+
-- 说明: 由 Flyway 在应用启动时自动执行，禁止手工执行
-- ============================================================

CREATE TABLE wiki_document (
    id          VARCHAR(32)  NOT NULL,
    title       VARCHAR(255) NOT NULL,
    content     TEXT,
    type        SMALLINT     DEFAULT 2,
    parent_id   VARCHAR(32)  DEFAULT '0',
    sort_order  INTEGER      DEFAULT 0,
    create_time TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_wiki_document_title UNIQUE (title)
);

COMMENT ON TABLE wiki_document IS 'Wiki文档主表';
COMMENT ON COLUMN wiki_document.id IS '唯一主键（雪花ID）';
COMMENT ON COLUMN wiki_document.title IS '文档或文件夹标题';
COMMENT ON COLUMN wiki_document.content IS 'Markdown原始内容（文件夹此项为空）';
COMMENT ON COLUMN wiki_document.type IS '节点类型: 1-文件夹, 2-文档';
COMMENT ON COLUMN wiki_document.parent_id IS '父级ID，0表示根目录';
COMMENT ON COLUMN wiki_document.sort_order IS '同层级排序权重，数字越小越靠前';
COMMENT ON COLUMN wiki_document.create_time IS '创建时间';
COMMENT ON COLUMN wiki_document.update_time IS '更新时间';

CREATE TRIGGER trg_wiki_document_update_time
BEFORE UPDATE ON wiki_document
FOR EACH ROW EXECUTE FUNCTION set_update_time();
